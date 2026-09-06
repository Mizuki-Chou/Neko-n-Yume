package mizukichou.nekonyume.model;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityRuntime;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.event.CatModelChangedEvent;
import mizukichou.nekonyume.model.bbmodel.BBModelImporter;
import mizukichou.nekonyume.model.bbmodel.ImportResult;
import mizukichou.nekonyume.model.resourcepack.MinecraftResourcePackVersion;
import mizukichou.nekonyume.model.resourcepack.PackFormat;
import mizukichou.nekonyume.model.resourcepack.ResourcePackArchiver;
import mizukichou.nekonyume.model.resourcepack.ResourcePackBuilder;
import mizukichou.nekonyume.model.resourcepack.ResourcePackException;
import mizukichou.nekonyume.model.resourcepack.ResourcePackValidator;
import mizukichou.nekonyume.model.resourcepack.ValidationIssue;
import mizukichou.nekonyume.model.resourcepack.ValidationResult;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 模型管理器：ModelBinding 的真实实现（架构 Phase 4 — Cat Binding）。
 *
 * <p>
 * 职责：
 * </p>
 * <ul>
 *   <li>模型注册表：扫描 models 目录（config {@code models.directory}），
 *       文件名 → 模型 id（{@code cats:black_cat.bbmodel} 或
 *       {@code myns/black_cat.bbmodel} → {@code myns:black_cat}）</li>
 *   <li>模型选择：猫的 {@code modelId}（空 → {@code models.default-model}）
 *       → 定义；均无 → 原版视觉（no-op）</li>
 *   <li>生命周期（ModelBinding 契约）：实体确认 → 创建实例与渲染器 +
 *       隐藏原版视觉；实体移除 → 销毁；停用 → 全部清理</li>
 *   <li>恢复期视觉（D3）：恢复期隐藏模型、show 原版半透明猫；
 *       恢复结束切回</li>
 *   <li>reload：重新扫描；解析失败的文件保留旧定义（§39）</li>
 * </ul>
 *
 * <p>
 * 主线程专用（Bukkit 实体操作）。
 * </p>
 */
public final class ModelManager implements ModelBinding {

    /**
     * 模型默认命名空间（无目录前缀时）。
     */
    private static final String DEFAULT_NAMESPACE = "cats";

    private final Plugin plugin;

    private final Logger logger;

    private final CatEntityRuntime runtime;

    private final ConfigManager configManager;

    private final CatStore store;

    private final CatCache catCache;

    private final CatBattleState battleState;

    /**
     * 渲染后端（生产为 ItemDisplay 后端；测试注入 Fake）。
     */
    private final ModelRenderBackend backend;

    /**
     * 构建互斥锁：防并发 reloadAsync 同时写同一资源包输出目录。
     */
    private final Object buildLock =
            new Object();

    /**
     * 重载代际（知识包 P1-3）：主线程递增、异步段比对——
     * 防并发 reload 乱序换发（旧任务结果不覆盖新任务）。
     */
    private volatile long reloadGeneration;

    /**
     * 0.9.0更新：全局总量上限。
     */
    private static final int MAX_TOTAL_MODELS =
            256;

    private static final long MAX_TOTAL_TEXTURE_BYTES =
            64L * 1024L * 1024L;

    /**
     * 0.9.0更新：关停标记——异步 reload 的后台段
     * 在 commit 前检查，禁止关停后继续写盘/换发。
     */
    private volatile boolean shuttingDown;

    /**
     * 单个 .bbmodel 文件大小上限（P2-5，防异常大文件）。
     */
    private static final long MAX_TEXTURE_BYTES =
            16L * 1024L * 1024L;

    private static final long MAX_MODEL_FILE_BYTES =
            16L * 1024L * 1024L;

    /**
     * 0.9.0更新：PNG 宽高上限——
     * 声称 65535×65535 的压缩图会在客户端解码时爆内存。
     */
    private static final int MAX_TEXTURE_DIMENSION =
            4096;

    /**
     * 0.9.0更新：运行时 Display 总量上限——
     * 模型文件限制（256 骨骼）防不住"多猫 × 多骨骼"的
     * 世界内实体爆炸。
     */
    private static final int MAX_TOTAL_RENDER_OBJECTS =
            4096;

    /**
     * 交互代理同步阈值：猫位置变化超过 0.25 格（平方
     * 0.0625）才重新传送 Interaction（0.9.0更新——
     * 消除每 tick 无条件 teleport）。
     */
    private static final double INTERACTION_SYNC_EPSILON_SQ =
            0.25 * 0.25;

    /**
     * 当前活跃的渲染对象（Display）总数。
     */
    private int totalRenderObjects;

    private final EntityVisualController visualController;

    /**
     * 已推送记录：玩家 UUID → zip SHA-1（主线程）。
     * 不用已过时的 Player.getResourcePackHash()，
     * 自维护已发送状态；退出时清理。
     */
    private final Map<UUID, String> sentResourcePacks =
            new LinkedHashMap<>();

    /**
     * P1-7：已成功加载记录（PlayerResourcePackStatusEvent
     * SUCCESSFULLY_LOADED）——sent ≠ loaded；
     * 已加载同 hash 的玩家不再推送。
     */
    private final Map<UUID, String> loadedResourcePacks =
            new LinkedHashMap<>();

    /**
     * 请求关联：请求 UUID → (玩家, zip SHA-1)
     * （现代 API setResourcePack(UUID, ...) 与事件
     * getID() 关联，弃用 getHash()）。
     *
     * <p>
     * 0.9.0更新：绑定玩家与最新请求，
     * 陈旧回调忽略、退出时按玩家清理。
     * </p>
     */
    private final Map<UUID, ResourcePackRequest> resourcePackRequests =
            new LinkedHashMap<>();

    /**
     * 玩家当前最新资源包请求 ID（陈旧回调过滤）。
     */
    private final Map<UUID, UUID> latestRequestByPlayer =
            new LinkedHashMap<>();

    /**
     * 资源包请求记录。
     */
    private record ResourcePackRequest(
            UUID playerUuid,
            String sha1
    ) {
    }

    /**
     * 模型注册表：modelId.toString() → 定义（不可变共享）。
     */
    private final Map<String, ModelDefinition> definitions =
            new LinkedHashMap<>();

    /**
     * 内嵌纹理缓存：ResourceId → PNG 字节。
     */
    private final Map<ResourceId, byte[]> embeddedTextures =
            new LinkedHashMap<>();

    /**
     * 0.9.0更新：最近一次成功 commit 的快照
     * （定义 + 纹理 + 指纹同版本）——reload 回退基线。
     */
    private ModelSnapshot currentSnapshot =
            null;

    /**
     * 活跃渲染：猫实体 UUID → ActiveModel。
     */
    private final Map<UUID, ActiveModel> activeModels =
            new LinkedHashMap<>();

    /**
     * 0.9.0更新：
     * 全部骨骼 Display 实体 UUID → 所属猫实体 UUID
     * （Display 移除自修复的反向映射）。
     */
    private final Map<UUID, UUID> displayRootUuids =
            new LinkedHashMap<>();

    /**
     * 交互代理实体（Interaction）UUID → 所属猫实体 UUID
     * （右键转发反向映射 + 移除监听）。
     */
    private final Map<UUID, UUID> interactionUuids =
            new LinkedHashMap<>();

    /**
     * 猫实体 UUID → 交互代理实体（位置同步 / 销毁）。
     */
    private final Map<UUID, org.bukkit.entity.Interaction>
            interactionByCatUuid =
                    new LinkedHashMap<>();

    /**
     * 模型解析失败（模型未加载/未配置）但实体有效的猫。
     * 定义表换发（reload / 后补模型文件）时统一重试。
     */
    private final Set<UUID> pendingVisuals =
            new LinkedHashSet<>();

    /**
     * 每实体同步失败日志节流（uuid → 上次记录时间）。
     * 单实体异常不得拖垮全部模型的每 tick 同步；
     * 同一实体 10 秒内只记一条，防日志洪泛。
     */
    private final Map<UUID, Long> syncFailureLogs =
            new LinkedHashMap<>();

    public ModelManager(
            Plugin plugin,
            Logger logger,
            CatEntityRuntime runtime,
            ConfigManager configManager,
            CatStore store,
            CatCache catCache,
            CatBattleState battleState
    ) {

        this(
                plugin,
                logger,
                runtime,
                configManager,
                store,
                catCache,
                battleState,
                new ItemDisplayRenderBackend(plugin)
        );
    }

    /**
     * 测试入口：注入渲染后端。
     */
    public ModelManager(
            Plugin plugin,
            Logger logger,
            CatEntityRuntime runtime,
            ConfigManager configManager,
            CatStore store,
            CatCache catCache,
            CatBattleState battleState,
            ModelRenderBackend backend
    ) {

        if (plugin == null || logger == null ||
                runtime == null ||
                configManager == null || store == null ||
                catCache == null ||
                battleState == null || backend == null) {

            throw new IllegalArgumentException(
                    "Arguments must not be null."
            );
        }

        this.plugin = plugin;
        this.logger = logger;
        this.runtime = runtime;
        this.configManager = configManager;
        this.store = store;
        this.catCache = catCache;
        this.battleState = battleState;
        this.backend = backend;
        this.visualController =
                new EntityVisualController(
                        plugin,
                        runtime
                );
    }

    /**
     * 首次加载：扫描目录，单个失败只告警跳过。
     */
    public void loadModels() {

        ModelSnapshot snapshot =
                scanInto(
                        null
                );

        if (snapshot == null) {

            return;
        }

        if (!buildResourcePackFrom(
                snapshot,
                -1
        )) {

            /*
             * P1-2：资源包构建失败则不得启用新定义
             * （否则服务端用新模型、客户端用旧包）。
             */
            logger.severe(
                    "Model loading aborted: resource pack "
                            + "build failed."
            );

            return;
        }

        applySnapshot(
                snapshot,
                "NekoNYume models loaded: "
        );
    }

    /**
     * 热加载（§38/§39）：重新扫描；解析失败的文件保留旧定义；
     * 运行中实例不受影响（持有旧不可变定义）。
     */
    public void reload() {

        /*
         * 当前快照（定义 + 纹理 + 指纹）作为回退基线——
         * 单模型失败时连同其纹理一起保留旧版。
         */
        ModelSnapshot baseline =
                currentSnapshot;

        ModelSnapshot snapshot =
                scanInto(
                        baseline
                );

        if (snapshot == null) {

            return;
        }

        if (!buildResourcePackFrom(
                snapshot,
                -1
        )) {

            return;
        }

        applySnapshot(
                snapshot,
                "NekoNYume models reloaded: "
        );
    }

    /**
     * 异步热加载（命令用，§40 线程模型）：
     * 扫描/解析在异步线程（不碰共享状态，纹理写入本地），
     * 换发与资源包构建回主线程。
     *
     * @param completion 换发完成后在主线程回调（null 忽略）
     */
    /*
     * 0.9.0更新：reload 的完成状态必须可区分——
     * 成功 / 失败 / 被更新一轮淘汰 / 关停，调用方据此给出
     * 诚实的运维反馈。
     */
    public enum ReloadOutcome {

        SUCCESS,
        FAILED,
        SUPERSEDED,
        SHUTDOWN
    }

    public void reloadAsync(
            java.util.function.Consumer<ReloadOutcome> completion
    ) {

        /*
         * 0.9.0更新：异步线程只生产不可变
         * ModelSnapshot（定义 + 纹理 + 指纹同版本），
         * 主线程 commit。generation 同时保护 apply 与
         * 磁盘 commit。
         *
         * 0.9.0更新：generation 自增必须与
         * 磁盘 commit 的“最后检查”互斥——同锁下自增，
         * buildResourcePackFrom 在锁内完成检查与 commit，
         * 检查通过即意味着 commit 期间不会有新 reload。
         */
        final ModelSnapshot previousSnapshot =
                currentSnapshot;

        final long generation;

        synchronized (buildLock) {

            generation =
                    ++reloadGeneration;
        }

        runtime.runTaskAsync(
                () -> {

                    if (shuttingDown) {

                        if (completion != null) {

                            runtime.runTask(
                                    () -> completion.accept(
                                            ReloadOutcome.SHUTDOWN
                                    )
                            );
                        }

                        return;
                    }

                    ModelSnapshot snapshot =
                            scanInto(
                                    previousSnapshot
                            );

                    if (snapshot == null) {

                        runtime.runTask(
                                () -> {

                                    if (completion != null) {

                                        completion.accept(
                                                ReloadOutcome.FAILED
                                        );
                                    }
                                }
                        );

                        return;
                    }

                    boolean built =
                            buildResourcePackFrom(
                                    snapshot,
                                    generation
                            );

                    runtime.runTask(
                            () -> {

                                if (generation !=
                                        reloadGeneration) {

                                    if (completion != null) {

                                        completion.accept(
                                                ReloadOutcome.SUPERSEDED
                                        );
                                    }

                                    return;
                                }

                                if (!built) {

                                    if (completion != null) {

                                        completion.accept(
                                                ReloadOutcome.FAILED
                                        );
                                    }

                                    return;
                                }

                                applySnapshot(
                                        snapshot,
                                        "NekoNYume models reloaded: "
                                );

                                if (completion != null) {

                                    completion.accept(
                                            ReloadOutcome.SUCCESS
                                    );
                                }
                            }
                    );
                }
        );
    }

    /**
     * 0.9.0更新：快照 commit——定义与纹理
     * 同版本原子换发（不再有 A 定义 / B 纹理的错位）。
     */
    private void applySnapshot(
            ModelSnapshot snapshot,
            String logPrefix
    ) {

        /*
         * 0.9.0更新：currentSnapshot 是逻辑真相源，
         * 先切换；definitions / embeddedTextures 是主线程缓存
         * 投影，紧随其后。主线程单写 + 无 callEvent 重入下，
         * 查询方观察到的始终是同一代快照。
         */
        currentSnapshot =
                snapshot;

        definitions.clear();
        definitions.putAll(
                snapshot.definitions
        );

        embeddedTextures.clear();
        embeddedTextures.putAll(
                snapshot.textures
        );

        logger.info(
                () -> logPrefix
                        + definitions.size()
        );

        rebuildActiveVisuals();
    }



    /**
     * 构建资源包目录 + zip（供玩家手动安装/托管分发）。
     *
     * <p>
     * 构建失败只告警不影响插件运行（§39）；
     * 产物：dataFolder/resource-pack/ + nekoyume-resource-pack.zip；
     * manifest.json 记录模型内容指纹与 zip SHA-1——
     * 模型文件未变时跳过重建（§27 缓存）。
     * </p>
     */
    /**
     * 从指定定义集合构建资源包（纯文件操作，无 Bukkit），
     * 可在异步线程调用（§40）。
     */
    private boolean buildResourcePackFrom(
            ModelSnapshot snapshot,
            long generation
    ) {

        synchronized (buildLock) {

            try {

                if (snapshot.definitions.isEmpty()) {

                    /*
                     * 空快照同样受 reload generation 保护。
                     * 否则旧的异步 reload 在较新的 reload 已经
                     * 提交后，可能删除新一代资源包产物。
                     */
                    if (generation >= 0 &&
                            generation != reloadGeneration) {

                        logger.info(
                                "An obsolete empty model snapshot was discarded."
                        );

                        return false;
                    }

                    /*
                     * 0.9.0更新：shutdown 与 generation
                     * 在同一临界区建立关闭闸门——shutdown 已把
                     * reloadGeneration 失效，但这里再加一道显式
                     * 检查，杜绝"检查通过后、commit 前关停"
                     * 的最后窗口。
                     */
                    if (shuttingDown) {

                        logger.info(
                                "Model pack build discarded — shutting down."
                        );

                        return false;
                    }

                    /*
                     * P0-37：最后一个模型删除 → 旧资源包
                     * 作废删除（绝不留下旧模型资源可被
                     * 客户端继续获取）。
                     */
                    deletePackArtifacts();

                    return true;
                }

                buildResourcePackLocked(
                        snapshot.definitions,
                        snapshot.textures,
                        snapshot.fingerprint,
                        generation
                );

                return true;

            } catch (Exception exception) {

                logger.log(
                        Level.SEVERE,
                        "Resource pack build failed — keeping "
                                + "previous model definitions (P1-2: "
                                + "server and client must agree on "
                                + "the same model version).",
                        exception
                );

                return false;
            }
        }
    }

    /**
     * 实际构建（buildLock 内执行；失败必须向上抛出，
     * 0.9.0更新——吞异常会让上层误判 built=true）。
     *
     * <p>
     * 事务顺序（0.9.0更新）：
     * 临时干净目录构建 → 目录自检 → 临时 ZIP 归档 → ZIP 自检
     * → generation 复核 → temp manifest 就绪 → 原子 commit
     * （目录三连换 + ZIP 与 manifest 替换，ZIP 旧版可回滚）。
     * 旧 Pack 全程保留到 commit 成功。
     * </p>
     */
    private void buildResourcePackLocked(
            Map<String, ModelDefinition> source,
            Map<ResourceId, byte[]> textures,
            String fingerprint,
            long generation
    ) throws Exception {

        Path dataFolder =
                plugin.getDataFolder()
                        .toPath();

        Path packDirectory =
                dataFolder.resolve(
                        "resource-pack"
                );

        Path newDirectory =
                dataFolder.resolve(
                        "resource-pack-new"
                );

        Path oldDirectory =
                dataFolder.resolve(
                        "resource-pack-old"
                );

        Path zipPath =
                dataFolder.resolve(
                        "nekoyume-resource-pack.zip"
                );

        Path tempZip =
                dataFolder.resolve(
                        "nekoyume-resource-pack.zip.tmp"
                );

        /*
         * manifest 放在 pack 目录之外（0.9.0更新）：
         * 否则第一次构建 ZIP 不含 manifest、第二次含旧版，
         * 且 ZIP 内 manifest 与磁盘 manifest 永远差一版。
         */
        Path manifestPath =
                dataFolder.resolve(
                        "resource-pack-manifest.json"
                );

        /*
         * 缓存命中必须验证 ZIP 本体（0.9.0更新——毕竟 hash 相同不等于东西没被换）：
         * manifest 说 good 不代表 ZIP 真的完好。
         */
        if (Files.exists(zipPath) &&
                Files.exists(manifestPath) &&
                Files.exists(packDirectory)) {

            String previous =
                    readManifestFingerprint(
                            manifestPath
                    );

            String manifestSha1 =
                    readManifestSha1(
                            manifestPath
                    );

            if (fingerprint.equals(
                    previous
            ) && manifestSha1 != null &&
                    manifestSha1.equals(
                            sha1Hex(
                                    zipPath
                            )
                    )) {

                logger.info(
                        () -> "NekoNYume resource pack unchanged - build skipped."
                );

                return;
            }
        }

        PackFormat packFormat =
                MinecraftResourcePackVersion
                        .MINECRAFT_26_2;

        /*
         * P0-38：每次构建使用临时干净目录（旧 asset 不残留，
         * 避免 orphan 与跨版本污染）。
         */
        deleteRecursively(
                newDirectory
        );

        Files.createDirectories(
                newDirectory
        );

        for (ModelDefinition definition :
                source.values()) {

            ResourcePackBuilder.build(
                    definition,
                    textures,
                    newDirectory,
                    packFormat,
                    logger
            );
        }

        /*
         * 先验证目录（0.9.0更新）：失败则整体作废，
         * 旧 Pack 未被动过。
         */
        ValidationResult directoryValidation =
                ResourcePackValidator.validate(
                        newDirectory,
                        packFormat
                );

        if (!directoryValidation.valid()) {

            for (ValidationIssue issue :
                    directoryValidation.issues()) {

                logger.warning(
                        issue.format()
                );
            }

            throw new ResourcePackException(
                    "Resource pack directory validation failed "
                            + "with " + directoryValidation.issues().size()
                            + " issue(s)."
            );
        }

        Files.deleteIfExists(
                tempZip
        );

        ResourcePackArchiver.archiveDirectory(
                newDirectory,
                tempZip
        );

        ValidationResult zipValidation =
                ResourcePackValidator.validateZip(
                        tempZip,
                        packFormat
                );

        if (!zipValidation.valid()) {

            for (ValidationIssue issue :
                    zipValidation.issues()) {

                logger.warning(
                        issue.format()
                );
            }

            throw new ResourcePackException(
                    "Resource pack ZIP validation failed with "
                            + zipValidation.issues().size()
                            + " issue(s)."
            );
        }

        /*
         * P0-33：commit 前的最后代际复核——若期间有更新的
         * reload，本次构建产物整体丢弃（不写磁盘）。
         */
        if (generation >= 0 &&
                generation != reloadGeneration) {

            logger.info(
                    "A newer model reload superseded this build "
                            + "— artifacts discarded before commit."
            );

            deleteRecursively(
                    newDirectory
            );

            Files.deleteIfExists(
                    tempZip
            );

            return;
        }

        /*
         * 0.9.0更新：commit 临界区内的关闭闸门——
         * shutdown 使 generation 失效的同一把锁，确保关停后
         * 绝不再有磁盘 commit。
         */
        if (shuttingDown) {

            logger.info(
                    "Model pack commit discarded — shutting down."
            );

            deleteRecursively(
                    newDirectory
            );

            Files.deleteIfExists(
                    tempZip
            );

            return;
        }

        /*
         * commit：目录三连换（old ← current，new → current），
         * 失败回滚。
         */
        deleteRecursively(
                oldDirectory
        );

        if (Files.exists(
                packDirectory
        )) {

            Files.move(
                    packDirectory,
                    oldDirectory
            );
        }

        try {

            Files.move(
                    newDirectory,
                    packDirectory
            );

        } catch (Exception exception) {

            /*
             * 回滚：旧目录恢复。
             */
            if (Files.exists(
                    oldDirectory
            ) && !Files.exists(
                    packDirectory
            )) {

                try {

                    Files.move(
                            oldDirectory,
                            packDirectory
                    );

                } catch (Exception rollback) {

                    exception.addSuppressed(
                            rollback
                    );
                }
            }

            throw exception;
        }

        /*
         * 0.9.0更新：oldDirectory 保留到
         * commit 全部成功之后——zip/manifest 替换失败时
         * 需要它回滚目录（此前先删导致目录无法回滚，
         * 形成"目录 V2 + ZIP V1"的分裂）。
         */

        /*
         * 0.9.0更新：manifest 内容在 commit 之前
         * 就绪（sha1 从已验证的 temp zip 计算），
         * 写入临时文件；commit 时 zip 与 manifest
         * 一并原子替换。任何一步失败 → 回滚，
         * 不再出现"ZIP 已换、manifest 旧"的分裂。
         */
        String sha1 =
                sha1Hex(
                        tempZip
                );

        String generatorVersion =
                plugin.getDescription() == null
                        ? "unknown"
                        : plugin.getDescription()
                                .getVersion();

        Path tempManifest =
                dataFolder.resolve(
                        "resource-pack-manifest.json.tmp"
                );

        Files.writeString(
                tempManifest,
                "{\"version\":\""
                        + generatorVersion
                        + "\",\"fingerprint\":\""
                        + fingerprint
                        + "\",\"sha1\":\""
                        + sha1
                        + "\"}",
                StandardCharsets.UTF_8
        );

        /*
         * 旧 zip / 旧 manifest 先挪开（可回滚），再原子换入。
         */
        Path backupZip =
                dataFolder.resolve(
                        "nekoyume-resource-pack.zip.old"
                );

        Path backupManifest =
                dataFolder.resolve(
                        "resource-pack-manifest.json.old"
                );

        Files.deleteIfExists(
                backupZip
        );

        Files.deleteIfExists(
                backupManifest
        );

        boolean hadOldZip =
                Files.exists(
                        zipPath
                );

        boolean hadOldManifest =
                Files.exists(
                        manifestPath
                );

        if (hadOldZip) {

            Files.move(
                    zipPath,
                    backupZip
            );
        }

        if (hadOldManifest) {

            Files.move(
                    manifestPath,
                    backupManifest
            );
        }

        try {

            Files.move(
                    tempZip,
                    zipPath,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            try {

                Files.move(
                        tempManifest,
                        manifestPath,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );

            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {

                Files.move(
                        tempManifest,
                        manifestPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (Exception commitException) {

            /*
             * 0.9.0更新：三件套回滚——
             * zip、manifest、目录任一失败都恢复旧状态，
             * 不再出现"目录 V2 + ZIP V1"的分裂。
             */
            if (hadOldZip &&
                    Files.exists(
                            backupZip
                    )) {

                try {

                    Files.move(
                            backupZip,
                            zipPath,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );

                } catch (Exception rollback) {

                    commitException.addSuppressed(
                            rollback
                    );
                }
            }

            if (hadOldManifest &&
                    Files.exists(
                            backupManifest
                    )) {

                try {

                    Files.move(
                            backupManifest,
                            manifestPath,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );

                } catch (Exception rollback) {

                    commitException.addSuppressed(
                            rollback
                    );

                }

            } else {

                try {

                    Files.deleteIfExists(
                            manifestPath
                    );

                } catch (Exception rollback) {

                    commitException.addSuppressed(
                            rollback
                    );
                }
            }

            /*
             * 回滚目录：新目录退回临时名，旧目录复位。
             */
            try {

                Files.move(
                        packDirectory,
                        newDirectory,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );

                Files.move(
                        oldDirectory,
                        packDirectory,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );

            } catch (Exception rollback) {

                commitException.addSuppressed(
                        rollback
                );
            }

            throw commitException;
        }

        /*
         * 全部成功：清理备份与旧目录。
         */
        Files.deleteIfExists(
                backupZip
        );

        Files.deleteIfExists(
                backupManifest
        );

        deleteRecursively(
                oldDirectory
        );

        Files.deleteIfExists(
                tempManifest
        );

        logger.info(
                () -> "NekoNYume resource pack built: "
                        + zipPath
                        + " (sha1=" + sha1 + ")"
        );
    }

    /**
     * 删除全部资源包产物（pack 目录 / ZIP / manifest）。
     */
    private void deletePackArtifacts() {

        Path dataFolder =
                plugin.getDataFolder()
                        .toPath();

        for (String name :
                new String[]{
                        "resource-pack",
                        "resource-pack-new",
                        "resource-pack-old",
                        "nekoyume-resource-pack.zip",
                        "nekoyume-resource-pack.zip.tmp",
                        "resource-pack-manifest.json"
                }) {

            try {

                Path path =
                        dataFolder.resolve(
                                name
                        );

                if (Files.isDirectory(
                        path
                )) {

                    deleteRecursively(
                            path
                    );

                } else {

                    Files.deleteIfExists(
                            path
                    );
                }

            } catch (IOException exception) {

                logger.log(
                        Level.WARNING,
                        "Failed to remove stale pack artifact '"
                                + name + "'.",
                        exception
                );
            }
        }
    }

    /**
     * 递归删除（幂等）。
     */
    private static void deleteRecursively(
            Path directory
    ) throws IOException {

        if (!Files.exists(
                directory
        )) {

            return;
        }

        try (Stream<Path> stream =
                Files.walk(
                        directory
                )) {

            for (Path path :
                    stream.sorted(
                                    java.util.Comparator
                                            .reverseOrder()
                            )
                            .toList()) {

                Files.deleteIfExists(
                        path
                );
            }
        }
    }

    /**
     * 外部纹理路径沙箱化（0.9.0更新）：复用
     * {@link mizukichou.nekonyume.model.resourcepack.PathGuard}。
     */
    private static Path resolveExternalTexture(
            Path modelFile,
            String externalPath
    ) throws IOException {

        return mizukichou.nekonyume.model.resourcepack.PathGuard
                .resolveWithin(
                        modelFile.getParent(),
                        externalPath
                );
    }

    /**
     * 指纹 = 扫描期收集的资产哈希快照（0.9.0更新：
     * 绝不重新读磁盘——否则扫描与构建之间的文件变更
     * 会让 manifest 指纹与 ZIP 内容错位）。
     */
    private String computeFingerprint(
            List<String> assetFingerprints
    ) throws Exception {

        java.util.Collections.sort(
                assetFingerprints
        );

        StringBuilder joined =
                new StringBuilder();

        for (String part :
                assetFingerprints) {

            joined.append(
                            part
                    )
                    .append(
                            '\n'
                    );
        }

        /*
         * P0-8：生成器元数据进入指纹——插件版本、
         * 目标 MC 版本、schema 版本任一变化都必须重建。
         */
        joined.append(
                        "generator:"
                )
                .append(
                        plugin.getDescription() == null
                                ? "unknown"
                                : plugin.getDescription()
                                        .getVersion()
                )
                .append(
                        '\n'
                );

        joined.append(
                        "target-mc:"
                )
                .append(
                        MinecraftResourcePackVersion
                                .MINECRAFT_26_2
                )
                .append(
                        '\n'
                );

        joined.append(
                "schema:1"
        );

        /*
         * 0.9.0更新：模型目录路径进入指纹——目录
         * 配置本身改变扫描输入，必须触发重建。
         */
        joined.append(
                        "\n"
                )
                .append(
                        "models-directory:"
                )
                .append(
                        configManager.snapshot()
                                .getModels()
                                .getDirectory()
                );

        return sha256Hex(
                joined.toString()
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );
    }



    private String readManifestFingerprint(
            Path manifestPath
    ) {

        return readManifestField(
                manifestPath,
                "fingerprint"
        );
    }

    private String readManifestSha1(
            Path manifestPath
    ) {

        return readManifestField(
                manifestPath,
                "sha1"
        );
    }

    private String readManifestField(
            Path manifestPath,
            String field
    ) {

        try {

            String content =
                    Files.readString(
                            manifestPath,
                            StandardCharsets.UTF_8
                    );

            String needle =
                    "\"" + field + "\":\"";

            int index =
                    content.indexOf(
                            needle
                    );

            if (index < 0) {
                return null;
            }

            int start =
                    index + needle.length();

            int end =
                    content.indexOf(
                            '"',
                            start
                    );

            if (end < 0) {
                return null;
            }

            return content.substring(
                    start,
                    end
            );

        } catch (Exception ignored) {

            return null;
        }
    }

    /**
     * 0.9.0更新：流式 SHA-1——绝不把整个 ZIP
     * 读进内存（分块喂 MessageDigest）。
     */
    private static String sha1Hex(
            Path file
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-1"
                );

        try (InputStream in =
                Files.newInputStream(
                        file
                )) {

            byte[] buffer =
                    new byte[8192];

            int n;

            while ((n = in.read(
                    buffer
            )) != -1) {

                digest.update(
                        buffer,
                        0,
                        n
                );
            }
        }

        return HexFormat.of()
                .formatHex(
                        digest.digest()
                );
    }

    private static String sha1Hex(
            byte[] data
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-1"
                );

        return HexFormat.of()
                .formatHex(
                        digest.digest(
                                data
                        )
                );
    }

    private static String sha256Hex(
            byte[] data
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        return HexFormat.of()
                .formatHex(
                        digest.digest(
                                data
                        )
                );
    }

    /**
     * 为猫选择模型定义；null = 原版视觉。
     */
    public ModelDefinition resolveFor(
            Cat logicalCat
    ) {

        if (logicalCat == null) {
            return null;
        }

        String raw =
                logicalCat.getModelId();

        if (raw == null || raw.isBlank()) {

            raw =
                    configManager.snapshot()
                            .getModels()
                            .getDefaultModelId();
        }

        if (raw == null || raw.isBlank()) {
            return null;
        }

        return definitions.get(
                raw.trim()
        );
    }

    /**
     * 已加载模型 ID 列表（字典序，命令展示用）。
     */
    public List<String> listModels() {

        return definitions.keySet()
                .stream()
                .sorted()
                .toList();
    }

    /**
     * 为玩家猫设置模型（命令入口）。
     *
     * @param modelId 模型 ID；blank 表示清除（回退默认/原版视觉）
     * @return 是否成功（玩家无猫 / ID 非法返回 false）
     */
    public boolean setModel(
            UUID playerUUID,
            String modelId
    ) {

        if (playerUUID == null) {
            return false;
        }

        String normalized =
                modelId == null
                        ? ""
                        : modelId.trim();

        if (!normalized.isEmpty() &&
                ResourceId.parse(
                        normalized
                ) == null) {

            return false;
        }

        /*
         * 0.9.0更新：设置的模型必须已加载
         * （先放文件 reload 再 set），否则返回 false
         * 而非"成功但实际原版视觉"。
         */
        if (!normalized.isEmpty() &&
                !definitions.containsKey(
                        normalized
                )) {

            logger.warning(
                    "setModel: model '" + normalized
                            + "' is not loaded — rejected."
            );

            return false;
        }

        if (!store.hasCat(
                playerUUID
        )) {

            return false;
        }

        /*
         * 0.9.0更新：双真相修复——setModel
         * 只写 store 不改内存 Cat.modelId，下次自动保存
         * （CatCache.saveCat 对称写）会把新模型覆盖回旧值。
         * 现在同步更新内存 Cat（Cat = runtime truth，
         * Store = persistence projection）。
         */
        Cat cat =
                catCache.getCat(
                        playerUUID
                );

        if (cat == null) {

            cat =
                    catCache.loadCat(
                            playerUUID
                    );
        }

        if (cat != null) {

            cat.setModelId(
                    normalized.isEmpty()
                            ? null
                            : normalized
            );
        }

        store.setCatModelId(
                playerUUID,
                normalized
        );

        applyModelChange(
                playerUUID
        );

        runtime.callEvent(
                new CatModelChangedEvent(
                        playerUUID,
                        store.getCatUUID(
                                playerUUID
                        ),
                        normalized
                )
        );

        return true;
    }

    /**
     * 模型变更立即生效：销毁旧视觉、reveal 原版猫、
     * 再按新模型重建（无模型则保持原版视觉）。
     */
    private void applyModelChange(
            UUID playerUUID
    ) {

        UUID entityUuid =
                store.getCatEntityUUID(
                        playerUUID
                );

        if (entityUuid == null) {
            return;
        }

        /*
         * 复用销毁路径（含 stop + 异常保护的 destroy）。
         */
        destroyActiveModel(
                entityUuid
        );

        visualController.reveal(
                entityUuid
        );

        Entity entity =
                runtime.getEntity(
                        entityUuid
                );

        if (!(entity instanceof org.bukkit.entity.Cat catEntity) ||
                !catEntity.isValid()) {

            return;
        }

        Cat logicalCat =
                catCache.getCatByEntity(
                        entityUuid
                );

        if (logicalCat == null) {

            logicalCat =
                    catCache.getCat(
                            playerUUID
                    );
        }

        if (logicalCat == null) {
            return;
        }

        onCatEntityReady(
                catEntity,
                logicalCat
        );
    }

    /**
     * 资源包推送（PlayerJoin 时调用）：
     * url 非空 + auto-send 才发送；zip 未构建则跳过；
     * 客户端已应用相同 hash 的包时不再重复推送。
     */
    public void sendResourcePack(
            Player player
    ) {

        if (player == null) {
            return;
        }

        ConfigSnapshot.Models models =
                configManager.snapshot()
                        .getModels();

        if (models.getResourcePackUrl()
                        .isEmpty() ||
                !models.isResourcePackAutoSend()) {

            return;
        }

        Path dataFolder =
                plugin.getDataFolder()
                        .toPath();

        Path zip =
                dataFolder.resolve(
                        "nekoyume-resource-pack.zip"
                );

        if (!Files.exists(zip)) {
            return;
        }

        try {

            /*
             * 优先读 manifest 缓存的 SHA-1（几 KB），
             * 缺失/损坏时回退全量计算（兜底）。
             */
            String sha1 =
                    readManifestSha1(
                            dataFolder.resolve(
                                    "resource-pack-manifest.json"
                            )
                    );

            if (sha1 == null) {

                sha1 =
                        sha1Hex(
                                zip
                        );
            }

            /*
             * 客户端已收到该 hash → 不重复推送
             * （避免每次登录都弹提示）；hash 变化时重新推送。
             */
            UUID playerUuid =
                    player.getUniqueId();

            String lastSent =
                    sentResourcePacks.get(
                            playerUuid
                    );

            if (sha1.equals(
                    lastSent
            )) {

                return;
            }

            /*
             * P1-7：已成功加载同 hash 的玩家不再推送
             * （sent ≠ loaded：只有 SUCCESSFULLY_LOADED
             * 才算客户端就绪）。
             */
            String loaded =
                    loadedResourcePacks.get(
                            playerUuid
                    );

            if (sha1.equals(
                    loaded
            )) {

                return;
            }

            UUID requestId =
                    UUID.randomUUID();

            /*
             * 0.9.0更新：运维可见性——URL 与本地 zip 的
             * hash 必须成对出现在日志中，管理员可据此核对
             * 托管内容与本地产物是否一致（客户端会以本地
             * hash 校验下载内容，不一致即加载失败）。
             */
            logger.info(
                    "Sending resource pack to "
                            + player.getName()
                            + " — url: "
                            + models.getResourcePackUrl()
                            + " sha1: "
                            + sha1
            );

            player.setResourcePack(
                    requestId,
                    models.getResourcePackUrl(),
                    hexToBytes(
                            sha1
                    ),
                    (net.kyori.adventure.text.Component) null,
                    false
            );

            resourcePackRequests.put(
                    requestId,
                    new ResourcePackRequest(
                            playerUuid,
                            sha1
                    )
            );

            /*
             * 0.9.0更新：记录玩家当前最新请求——
             * 状态回调乱序（网络异步）时旧请求回调不覆盖
             * 新状态。
             */
            latestRequestByPlayer.put(
                    playerUuid,
                    requestId
            );

            sentResourcePacks.put(
                    playerUuid,
                    sha1
            );

        } catch (Exception exception) {

            logger.log(
                    Level.WARNING,
                    "Failed to send resource pack to "
                            + player.getName() + ".",
                    exception
            );
        }
    }

    /**
     * 玩家退出：清理已推送记录（防缓存无界增长）。
     */

    /*
     * 测试访问器（0.9.0更新：清理语义验证）。
     */
    public int pendingRequestCount() {

        return resourcePackRequests.size();
    }

    public String loadedPackHashOf(
            UUID playerUuid
    ) {

        return loadedResourcePacks.get(
                playerUuid
        );
    }

    public void forgetResourcePack(
            UUID playerUuid
    ) {

        if (playerUuid == null) {
            return;
        }

        sentResourcePacks.remove(
                playerUuid
        );

        loadedResourcePacks.remove(
                playerUuid
        );

        latestRequestByPlayer.remove(
                playerUuid
        );

        /*
         * 0.9.0更新：清理该玩家未完成的
         * 请求——迟到的回调不再把已退出玩家写回
         * loaded 状态。
         */
        resourcePackRequests.values()
                .removeIf(
                        request -> request.playerUuid()
                                .equals(
                                        playerUuid
                                )
                );
    }

    /**
     * P1-7：客户端资源包状态回调（sent ≠ loaded）——
     * SUCCESSFULLY_LOADED 记录已加载 hash；拒绝/失败
     * 仅告警（不做无限重发，玩家可随时更换资源包）。
     */
    public void onResourcePackStatus(
            org.bukkit.event.player.PlayerResourcePackStatusEvent event
    ) {

        if (event == null) {
            return;
        }

        org.bukkit.event.player.PlayerResourcePackStatusEvent.Status status =
                event.getStatus();

        UUID playerUuid =
                event.getPlayer()
                        .getUniqueId();

        ResourcePackRequest request =
                resourcePackRequests.remove(
                        event.getID()
                );

        /*
         * 0.9.0更新：陈旧回调检查——
         * 不是玩家当前最新请求的回调直接忽略。
         */
        if (request != null &&
                !request.playerUuid()
                        .equals(
                                playerUuid
                        )) {

            return;
        }

        if (request != null &&
                latestRequestByPlayer.get(
                        playerUuid
                ) != null &&
                !latestRequestByPlayer.get(
                        playerUuid
                )
                        .equals(
                                event.getID()
                        )) {

            return;
        }

        String requestedSha1 =
                request == null
                        ? null
                        : request.sha1();

        switch (status) {

            case SUCCESSFULLY_LOADED -> {

                if (requestedSha1 != null) {

                    loadedResourcePacks.put(
                            playerUuid,
                            requestedSha1
                    );
                }
            }

            case DECLINED, FAILED_DOWNLOAD, INVALID_URL,
                    FAILED_RELOAD, DISCARDED ->
                    logger.warning(
                            "Resource pack " + status.name()
                                    + " for player "
                                    + event.getPlayer()
                                            .getName()
                                    + " (sha1 "
                                    + requestedSha1 + ")."
                    );

            default ->
                    /* ACCEPTED/DOWNLOADED 等中间态忽略 */ {
                    }
        }
    }

    /**
     * SHA-1 hex → 20 字节（0.9.0更新）：
     * 严格校验 40 位十六进制，非法输入抛
     * IllegalArgumentException（绝不静默截断）。
     */
    private static byte[] hexToBytes(
            String hex
    ) {

        if (hex == null ||
                !hex.matches(
                        "^[0-9a-fA-F]{40}$"
                )) {

            throw new IllegalArgumentException(
                    "invalid sha1 hex: "
                            + (hex == null
                            ? "null"
                            : hex.length() + " chars")
            );
        }

        byte[] out =
                new byte[20];

        for (int i = 0;
                i < out.length;
                i++) {

            out[i] =
                    (byte) Integer.parseInt(
                            hex.substring(
                                    i * 2,
                                    i * 2 + 2
                            ),
                            16
                    );
        }

        return out;
    }

    public EntityVisualController visualController() {

        return visualController;
    }

    public int activeModelCount() {

        return activeModels.size();
    }

    public int pendingVisualCount() {

        return pendingVisuals.size();
    }

    public int definitionCount() {

        return definitions.size();
    }

    /*
     * ============================================================
     * ModelBinding 契约
     * ============================================================
     */

    /**
     * 实体确认存在（召唤成功/恢复成功/区块重载）。
     * 幂等：已存在渲染器则跳过。
     */
    @Override
    public void onCatEntityReady(
            org.bukkit.entity.Cat entity,
            Cat logicalCat
    ) {

        if (entity == null || logicalCat == null) {
            return;
        }

        UUID entityUuid =
                entity.getUniqueId();

        if (activeModels.containsKey(
                entityUuid
        )) {

            return;
        }

        ModelDefinition definition =
                resolveFor(
                        logicalCat
                );

        if (definition == null) {

            /*
             * 模型未加载/未配置：登记待重试，
             * 模型文件后补 + reload 后统一重建。
             */
            pendingVisuals.add(
                    entityUuid
            );

            return;
        }

        ModelRenderer renderer =
                null;

        /*
         * 0.9.0更新：运行时 Display 总量预算。
         */
        int boneCount =
                definition.getPrimaryGeometry()
                        .boneCount();

        if (totalRenderObjects + boneCount >
                MAX_TOTAL_RENDER_OBJECTS) {

            logger.severe(
                    "Render object budget exceeded ("
                            + MAX_TOTAL_RENDER_OBJECTS
                            + "): refusing model '"
                            + definition.getId()
                            + "' for cat entity "
                            + entityUuid + "."
            );

            return;
        }

        try {

            ModelInstance instance =
                    new ModelInstance(
                            definition,
                            entityUuid
                    );

            renderer =
                    ModelRenderer.create(
                            instance,
                            backend,
                            entity.getLocation(),
                            entityUuid
                    );

            ActiveModel active =
                    new ActiveModel(
                            entityUuid,
                            definition.getId(),
                            instance,
                            renderer,
                            createController(
                                    definition,
                                    instance
                            ),
                            entity.getWorld()
                                    .getUID()
                    );

            activeModels.put(
                    entityUuid,
                    active
            );

            totalRenderObjects +=
                    renderer.objectCount();

            /*
             * 0.9.0更新：
             * 登记全部骨骼 Display 的反向映射——只登记 Root
             * 则 Head/Ear 被外部移除时无法自修复。
             */
            for (UUID displayUuid :
                    renderer.allObjectIds()) {

                displayRootUuids.put(
                        displayUuid,
                        entityUuid
                );
            }

            /*
             * 0.9.0更新：创建成功后清理待重试登记
             * （若先前 resolve 失败登记过）。
             */
            pendingVisuals.remove(
                    entityUuid
            );

            /*
             * P2-2：立即写入初始变换——避免首 tick 前
             * （≤50ms）Display 停在未定位状态。
             */
            renderer.tick(
                    entity.getLocation(),
                    entity.getYaw()
            );

            /*
             * 0.9.0更新：生成交互代理实体——
             * hideEntity 后玩家右键命中 Interaction，
             * 由监听器转发为对猫的交互。
             *
             * 0.9.0更新：不再用 instanceof 检测真后端——
             * spawnInteraction 已是 ModelRenderBackend 接口的一
             * 部分（seam），Fake 后端返回假代理，交互生命周期
             * 从此可被完整测试。
             */
            org.bukkit.entity.Interaction interaction =
                    backend.spawnInteraction(
                            entity.getLocation(),
                            entityUuid
                    );

            if (interaction != null) {

                interactionUuids.put(
                        interaction.getUniqueId(),
                        entityUuid
                );

                interactionByCatUuid.put(
                        entityUuid,
                        interaction
                );

                /*
                 * Interaction 已 spawn 在猫当前位置：初始化
                 * 同步基准，首次 tick 不重复传送。
                 */
                active.lastInteractionPosition =
                        new Vec3(
                                entity.getLocation().getX(),
                                entity.getLocation().getY(),
                                entity.getLocation().getZ()
                        );
            }

            boolean recovering =
                    battleState.isRecovering(
                            entityUuid
                    );

            active.recovering = recovering;

            if (recovering) {

                renderer.setVisible(
                        false
                );

                visualController.show(
                        entity
                );

            } else {

                visualController.hide(
                        entity
                );
            }

        } catch (Exception exception) {

            /*
             * 创建/绑定中途失败：清理半成品，
             * 绝不留半绑定状态或孤儿渲染对象。
             */
            ActiveModel failed =
                    activeModels.remove(
                            entityUuid
                    );

            if (failed != null) {

                if (failed.controller != null) {

                    failed.controller.stop();
                }

                try {

                    failed.renderer.destroy();

                } catch (RuntimeException ignored) {
                    // 清理尽力而为。
                }
            }

            logger.log(
                    Level.WARNING,
                    "Failed to create model renderer for cat entity "
                            + entityUuid + ".",
                    exception
            );
        }
    }

    /**
     * 为主人猫即将被移除（删除前回调，store 仍可读）。
     */
    @Override
    public void onOwnerCatRemoved(
            UUID playerUUID,
            UUID entityUuid
    ) {

        if (playerUUID == null) {
            return;
        }

        if (entityUuid == null) {
            return;
        }

        destroyActiveModel(
                entityUuid
        );

        pendingVisuals.remove(
                entityUuid
        );

        visualController.forget(
                entityUuid
        );
    }

    @Override
    public void shutdown() {

        /*
         * 0.9.0更新：关停使全部在飞异步 reload 失效
         * （generation 失配 → 不 commit），并清理资源包
         * 请求/发送/加载状态。
         */
        shuttingDown = true;

        reloadGeneration++;

        pendingVisuals.clear();

        for (UUID uuid :
                new LinkedHashMap<>(activeModels)
                        .keySet()) {

            destroyActiveModel(
                    uuid
            );
        }

        activeModels.clear();
        visualController.clearAll();

        resourcePackRequests.clear();
        sentResourcePacks.clear();
        loadedResourcePacks.clear();
    }

    /**
     * 每 tick 同步（ModelVisualSyncTask 调用）：
     * 失效实体清理、恢复期视觉切换、渲染器位置同步。
     */
    public void tick() {

        if (activeModels.isEmpty()) {
            return;
        }

        /*
         * 0.9.0更新：跨世界重建不能在迭代内直接
         * onCatEntityReady（内部 put 会修改 map →
         * fail-fast 迭代器抛 CME）。收集后循环外重建。
         */
        java.util.List<UUID> crossWorldRebuilds =
                new java.util.ArrayList<>();

        Iterator<Map.Entry<UUID, ActiveModel>> iterator =
                activeModels.entrySet()
                        .iterator();

        while (iterator.hasNext()) {

            ActiveModel active =
                    iterator.next()
                            .getValue();

            Entity entity =
                    runtime.getEntity(
                            active.entityUuid
                    );

            if (entity == null ||
                    !entity.isValid()) {

                /*
                 * 清理尽力而为：异常只告警，绝不反复阻塞
                 * 后续条目的处理。
                 *
                 * 0.9.0更新：此处此前未做 Display 预算
                 * 计账扣减——实体失效销毁后 totalRenderObjects
                 * 永久高估，长期运行会错误拒绝新模型渲染。
                 * objectCount 必须在 destroy 之前取值
                 * （destroy 会清空 objects 表）。
                 */
                int released =
                        active.renderer.objectCount();

                try {

                    active.renderer.destroy();

                } catch (Exception exception) {

                    logger.log(
                            Level.WARNING,
                            "Failed to destroy stale model renderer for cat entity "
                                    + active.entityUuid + ".",
                            exception
                    );
                }

                totalRenderObjects =
                        Math.max(
                                0,
                                totalRenderObjects - released
                        );

                visualController.forget(
                        active.entityUuid
                );

                pendingVisuals.remove(
                        active.entityUuid
                );

                iterator.remove();

                continue;
            }

            boolean recovering =
                    battleState.isRecovering(
                            active.entityUuid
                    );

            if (recovering != active.recovering) {

                active.recovering = recovering;

                if (recovering) {

                    /*
                     * 进入恢复期：隐藏模型，show 原版半透明猫
                     * （D3：复用原版隐身药水表现）。
                     */
                    active.renderer.setVisible(
                            false
                    );

                    visualController.show(
                            entity
                    );

                } else {

                    active.renderer.setVisible(
                            true
                    );

                    visualController.hide(
                            entity
                    );
                }
            }

            if (!recovering) {

                /*
                 * P1-11：跨世界传送——Display 还在旧世界，
                 * 必须销毁重建（新世界 spawn）。
                 */
                UUID currentWorld =
                        entity.getWorld()
                                .getUID();

                if (!currentWorld.equals(
                        active.worldUuid
                )) {

                    iterator.remove();

                    destroyActiveModel(
                            active.entityUuid
                    );

                    visualController.reveal(
                            active.entityUuid
                    );

                    crossWorldRebuilds.add(
                            active.entityUuid
                    );

                    continue;
                }

                /*
                 * P1-12：动态取当前 Cat 缓存对象——
                 * 绝不持有 reload 可能替换的旧引用。
                 *
                 * 整段（动画信号/姿态采样/渲染器写入/交互
                 * 同步）以每实体异常隔离包裹：任何单实体
                 * 异常只跳过该实体并节流日志，绝不中断
                 * 其余猫的同步循环（循环任务因异常退出时
                 * 表现为"全部模型完全不动"）。
                 */
                try {

                Cat logicalCat =
                        catCache.getCatByEntity(
                                active.entityUuid
                        );

                /*
                 * 动画信号驱动（§47 Adapter）：
                 * 每 tick 推导行为信号并下发给控制器；
                 * 控制器推进采样，活跃时刷新渲染姿态。
                 */
                if (active.controller != null &&
                        logicalCat != null) {

                    /*
                     * 候选名逐个尝试（P1-21）：第一个存在的
                     * 动画命中；全部缺失时表尾必为 idle
                     * （存在则播放，否则保持 base 姿态——
                     * 绝不残留上一姿态）。
                     */
                    java.util.List<String> candidates =
                            CatAnimationAdapter
                                    .resolveAnimationCandidates(
                                            logicalCat,
                                            entity,
                                            battleState
                                    );

                    String signal =
                            candidates.isEmpty()
                                    ? CatAnimationAdapter.ANIM_IDLE
                                    : candidates.get(
                                            0
                                    );

                    if (CatAnimationAdapter.isActionSignal(
                            signal
                    )) {

                        /*
                         * 0.9.0更新：动作信号
                         * 边沿触发——同一次攻击/受伤只启动
                         * 一次动作动画（窗口内绝不重播）。
                         */
                        if (!signal.equals(
                                active.lastActionSignal
                        )) {

                            active.lastActionSignal =
                                    signal;

                            for (String candidate :
                                    candidates) {

                                if (active.controller.play(
                                        candidate
                                )) {

                                    break;
                                }
                            }
                        }

                    } else {

                        active.lastActionSignal =
                                null;

                        for (String candidate :
                                candidates) {

                            if (active.controller.play(
                                    candidate
                            )) {

                                break;
                            }
                        }
                    }

                    if (active.controller.tick()) {

                        active.renderer.refreshPoses();
                    }
                }

                active.renderer.tick(
                        entity.getLocation(),
                        entity.getYaw()
                );

                /*
                 * 0.9.0更新：交互代理实体
                 * 跟随猫（Interaction 无变换概念，直接
                 * teleport）。0.9.0更新：此前的
                 * “频率由变更检测保证”注释与实现不符——
                 * 实际每 tick 无条件 teleport（每猫每 tick
                 * 一次实体传送 + 同步包）。补上真正的
                 * 变更检测：猫位置变化超过阈值才传送。
                 */
                org.bukkit.entity.Interaction interaction =
                        interactionByCatUuid.get(
                                active.entityUuid
                        );

                if (interaction != null &&
                        interaction.isValid()) {

                    Vec3 current =
                            new Vec3(
                                    entity.getLocation().getX(),
                                    entity.getLocation().getY(),
                                    entity.getLocation().getZ()
                            );

                    if (active.lastInteractionPosition == null ||
                            current.subtract(
                                    active.lastInteractionPosition
                            )
                                    .lengthSquared() >
                                    INTERACTION_SYNC_EPSILON_SQ) {

                        interaction.teleport(
                                entity.getLocation()
                        );

                        active.lastInteractionPosition =
                                current;
                    }
                }

                } catch (Exception exception) {

                    long now =
                            System.currentTimeMillis();

                    Long lastLogged =
                            syncFailureLogs.get(
                                    active.entityUuid
                            );

                    if (lastLogged == null ||
                            now - lastLogged >
                                    10_000L) {

                        syncFailureLogs.put(
                                active.entityUuid,
                                now
                        );

                        logger.log(
                                Level.WARNING,
                                "Failed to sync model visuals "
                                        + "for cat entity "
                                        + active.entityUuid
                                        + " (skipping this tick; "
                                        + "other cats unaffected): "
                                        + exception.getMessage(),
                                exception
                        );
                    }
                }
            }
        }

        /*
         * 0.9.0更新：循环外统一重建跨世界实体
         * （迭代中 put 会触发 fail-fast CME）。
         */
        for (UUID uuid :
                crossWorldRebuilds) {

            Entity entity =
                    runtime.getEntity(
                            uuid
                    );

            if (!(entity instanceof org.bukkit.entity.Cat catEntity) ||
                    !catEntity.isValid()) {

                continue;
            }

            Cat logicalCat =
                    catCache.getCatByEntity(
                            uuid
                    );

            if (logicalCat != null) {

                onCatEntityReady(
                        catEntity,
                        logicalCat
                );
            }
        }

        /*
         * 0.9.0更新：待修复队列的 tick 级
         * 重试——自修复路径（Display/Interaction 被外部移除）
         * 在逻辑猫暂态不可得时挂起，这里恢复后重建；
         * 实体失效则清理（防泄漏）。
         */
        if (!pendingVisuals.isEmpty()) {

            for (UUID uuid :
                    new ArrayList<>(
                            pendingVisuals
                    )) {

                Entity entity =
                        runtime.getEntity(
                                uuid
                        );

                if (entity instanceof
                        org.bukkit.entity.Cat cat &&
                        cat.isValid()) {

                    Cat logicalCat =
                            catCache.getCatByEntity(
                                    uuid
                            );

                    if (logicalCat == null) {

                        continue;
                    }

                    pendingVisuals.remove(
                            uuid
                    );

                    onCatEntityReady(
                            cat,
                            logicalCat
                    );

                } else if (entity == null ||
                        !entity.isValid()) {

                    pendingVisuals.remove(
                            uuid
                    );
                }
            }
        }
    }

    /**
     * 重建全部已绑定实体的视觉（定义表换发后调用）。
     *
     * <p>
     * 销毁旧渲染 → reveal 原版视觉 → 按新定义重建；
     * 实体失效或领域数据缺失则跳过（下次 addToWorld 兜底）。
     * </p>
     */

    /**
     * 猫实体被移出世界（外部 remove / 世界卸载，知识包 P1-10）：
     * 其 Display 渲染已随之消失（non-persistent），
     * 必须同步销毁本地渲染状态；否则实体重新出现时
     * activeModels 的假活跃条目会让 onCatEntityReady
     * 幂等跳过，模型永久消失。
     */
    public void onEntityRemovedFromWorld(
            Entity entity
    ) {

        if (entity == null) {
            return;
        }

        UUID uuid =
                entity.getUniqueId();

        /*
         * 0.9.0更新：清理必须在 activeModels 早退之前
         * （无 renderer 的猫也可能在 pendingVisuals 中）。
         */
        pendingVisuals.remove(
                uuid
        );

        if (!activeModels.containsKey(
                uuid
        )) {

            return;
        }

        destroyActiveModel(
                uuid
        );

        visualController.forget(
                uuid
        );
    }

    /**
     * 世界卸载（知识包 P1-10）：该世界全部实体已销毁，
     * 清理对应渲染状态（Display 无从 remove，destroy 的
     * 异常被吞并告警）。
     */
    public void onWorldUnloaded(
            UUID worldUuid
    ) {

        if (worldUuid == null) {
            return;
        }

        for (UUID uuid :
                new java.util.ArrayList<>(
                        activeModels.keySet()
                )) {

            if (worldUuid.equals(
                    activeModels.get(uuid)
                            .worldUuid
            )) {

                destroyActiveModel(
                        uuid
                );

                visualController.forget(
                        uuid
                );
            }
        }
    }

    private void rebuildActiveVisuals() {

        Set<UUID> candidates =
                new LinkedHashSet<>(
                        activeModels.keySet()
                );

        candidates.addAll(
                pendingVisuals
        );

        pendingVisuals.clear();

        for (UUID uuid :
                candidates) {

            destroyActiveModel(
                    uuid
            );

            visualController.reveal(
                    uuid
            );

            Entity entity =
                    runtime.getEntity(
                            uuid
                    );

            if (!(entity instanceof org.bukkit.entity.Cat catEntity) ||
                    !catEntity.isValid()) {

                continue;
            }

            Cat logicalCat =
                    catCache.getCatByEntity(
                            uuid
                    );

            if (logicalCat == null) {

                continue;
            }

            onCatEntityReady(
                    catEntity,
                    logicalCat
            );
        }
    }

    private void destroyActiveModel(
            UUID entityUuid
    ) {

        ActiveModel active =
                activeModels.remove(
                        entityUuid
                );

        if (active == null) {
            return;
        }

        if (active.controller != null) {

            active.controller.stop();
        }

        /*
         * 0.9.0更新：清理 Root Display 反向映射。
         */
        /*
         * 0.9.0更新：移除全部骨骼 Display
         * 的反向映射（destroy 前取值——destroy 会清空）。
         */
        for (UUID displayUuid :
                active.renderer.allObjectIds()) {

            displayRootUuids.remove(
                    displayUuid
            );
        }

        /*
         * 0.9.0更新：移除交互代理实体并清理双向映射。
         */
        org.bukkit.entity.Interaction interaction =
                interactionByCatUuid.remove(
                        entityUuid
                );

        if (interaction != null) {

            interactionUuids.remove(
                    interaction.getUniqueId()
            );

            try {

                interaction.remove();

            } catch (Exception exception) {

                logger.log(
                        Level.WARNING,
                        "Failed to remove interaction entity for cat "
                                + entityUuid + ".",
                        exception
                );
            }
        }

        int released =
                active.renderer.objectCount();

        try {

            active.renderer.destroy();

        } catch (Exception exception) {

            logger.log(
                    Level.WARNING,
                    "Failed to destroy model renderer for cat entity "
                            + entityUuid + ".",
                    exception
            );
        }

        totalRenderObjects =
                Math.max(
                        0,
                        totalRenderObjects - released
                );
    }

    /**
     * Display / Interaction 的身份标记 key（监听器识别用）。
     */
    public org.bukkit.NamespacedKey displayOwnerKey() {

        return new org.bukkit.NamespacedKey(
                plugin,
                ItemDisplayRenderBackend.PDC_KEY_DISPLAY_OWNER
        );
    }

    /**
     * 交互代理实体 → 所属猫（右键转发用；无映射返回 null）。
     */
    public org.bukkit.entity.Cat catForInteraction(
            UUID interactionUuid
    ) {

        UUID catUuid =
                interactionUuids.get(
                        interactionUuid
                );

        if (catUuid == null) {
            return null;
        }

        Entity entity =
                runtime.getEntity(
                        catUuid
                );

        if (entity instanceof org.bukkit.entity.Cat cat &&
                cat.isValid()) {

            return cat;
        }

        return null;
    }

    /**
     * 交互代理实体被外部移除（/kill 等）：猫仍有效则
     * 重建（renderer + interaction 一并重建——罕见场景）。
     */
    public void onInteractionRemoved(
            UUID interactionUuid
    ) {

        UUID catUuid =
                interactionUuids.remove(
                        interactionUuid
                );

        if (catUuid == null) {
            return;
        }

        interactionByCatUuid.remove(
                catUuid
        );

        repairAfterExternalRemoval(
                catUuid
        );
    }

    /**
     * Display 被外部移除（/kill、清理插件）：若所属猫仍有效，
     * 重建模型视觉；猫已无效则仅清理映射。
     * （0.9.0更新——否则原版猫仍被 hide，
     * 玩家面对空气且无法交互。）
     */
    public void onDisplayRemoved(
            UUID displayUuid
    ) {

        UUID catUuid =
                displayRootUuids.remove(
                        displayUuid
                );

        if (catUuid == null) {
            return;
        }

        repairAfterExternalRemoval(
                catUuid
        );
    }

    /**
     * 自修复共用路径（0.9.0更新）：先查逻辑猫、
     * 再销毁残留——逻辑猫暂态不可得时挂起待重试（tick 级
     * pending 重试兜底），绝不留"原版猫仍 hide + 模型没了"
     * 的永久死猫。
     */
    private void repairAfterExternalRemoval(
            UUID catUuid
    ) {

        /*
         * 0.9.0更新：延迟到下一 tick 执行——Paper 26.2
         * 的 EntityRemoveFromWorldEvent 在区块系统处理
         * section 状态更新的窗口内触发，此时同步 remove()
         * 其它 Display 会被 "processing section status
         * updates" 守护拦截（实机日志刷屏 + 实体残留）。
         */
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> repairNow(
                                catUuid
                        )
                );
    }

    /**
     * 自修复实执行体（延迟一 tick 后调用）。
     */
    private void repairNow(
            UUID catUuid
    ) {

        Cat logicalCat =
                catCache.getCatByEntity(
                        catUuid
                );

        Entity entity =
                runtime.getEntity(
                        catUuid
                );

        destroyActiveModel(
                catUuid
        );

        visualController.reveal(
                catUuid
        );

        if (logicalCat == null ||
                !(entity instanceof
                        org.bukkit.entity.Cat catEntity) ||
                !catEntity.isValid()) {

            pendingVisuals.add(
                    catUuid
            );

            return;
        }

        onCatEntityReady(
                catEntity,
                logicalCat
        );
    }

    /**
     * 启动清理：移除世界里残留的本插件 Display
     * （0.9.0更新——spawn 与 setPersistent(false)
     * 之间崩溃可能把纸片 Display 写进存档）。
     */
    public void cleanupOrphanDisplays() {

        int removed = 0;

        for (org.bukkit.World world :
                runtime.onlineWorlds()) {

            for (org.bukkit.entity.ItemDisplay display :
                    world.getEntitiesByClass(
                            org.bukkit.entity.ItemDisplay.class
                    )) {

                String owner =
                        display.getPersistentDataContainer()
                                .get(
                                        new org.bukkit.NamespacedKey(
                                                plugin,
                                                ItemDisplayRenderBackend
                                                        .PDC_KEY_DISPLAY_OWNER
                                        ),
                                        org.bukkit.persistence
                                                .PersistentDataType
                                                .STRING
                                );

                if (owner != null) {

                    display.remove();
                    removed++;
                }
            }
        }

        if (removed > 0) {

            logger.warning(
                    "Removed " + removed
                            + " orphan model displays on startup."
            );
        }
    }

    /**
     * 创建动画控制器；无动画的模型返回 null。
     */
    private static AnimationController createController(
            ModelDefinition definition,
            ModelInstance instance
    ) {

        if (definition.getAnimations()
                .isEmpty()) {

            return null;
        }

        AnimationController controller =
                new AnimationController(
                        instance
                );

        controller.play(
                CatAnimationAdapter.ANIM_IDLE
        );

        return controller;
    }

    /**
     * 0.9.0更新：纹理冲突检测——同一 ResourceId 的
     * 不同内容 = 冲突失败（绝不静默覆盖）；相同内容 = 幂等。
     */
    private static long putTexturesChecked(
            Map<ResourceId, byte[]> sink,
            Map<ResourceId, byte[]> incoming,
            ResourceId modelId,
            java.nio.file.Path modelFile
    ) {

        /*
         * 0.9.0更新：返回"新增字节数"——
         * 只有新 key 才计费（共享纹理幂等去重），
         * 调用方据此累计总容量上限。
         * （此前把累加放在 put 之后的 containsKey 判断，
         * 恒为 true，64MB 上限形同虚设。）
         */
        long added = 0L;

        for (Map.Entry<ResourceId, byte[]> entry :
                incoming.entrySet()) {

            byte[] existing =
                    sink.get(
                            entry.getKey()
                    );

            if (existing == null) {

                /*
                 * 0.9.0更新：解码尺寸防爆——
                 * 解析 PNG IHDR 宽高，超限直接拒绝。
                 */
                validateTextureDimension(
                        entry.getKey(),
                        entry.getValue()
                );

                sink.put(
                        entry.getKey(),
                        entry.getValue()
                );

                added +=
                        entry.getValue().length;

                continue;
            }

            if (!java.util.Arrays.equals(
                    existing,
                    entry.getValue()
            )) {

                throw new IllegalStateException(
                        "Texture resource "
                                + entry.getKey()
                                + " is produced by two different "
                                + "sources (model "
                                + modelId
                                + ", file "
                                + modelFile
                                + ")."
                );
            }
        }

        return added;
    }

    /**
     * PNG IHDR 宽高校验（字节 16-19 宽、20-23 高，大端）。
     */
    private static void validateTextureDimension(
            ResourceId texture,
            byte[] bytes
    ) {

        if (bytes == null ||
                bytes.length < 24) {

            throw new IllegalStateException(
                    "Texture " + texture
                            + " is not a valid PNG (too short)."
            );
        }

        int width =
                ((bytes[16] & 0xFF) << 24) |
                        ((bytes[17] & 0xFF) << 16) |
                        ((bytes[18] & 0xFF) << 8) |
                        (bytes[19] & 0xFF);

        int height =
                ((bytes[20] & 0xFF) << 24) |
                        ((bytes[21] & 0xFF) << 16) |
                        ((bytes[22] & 0xFF) << 8) |
                        (bytes[23] & 0xFF);

        if (width <= 0 ||
                height <= 0 ||
                width > MAX_TEXTURE_DIMENSION ||
                height > MAX_TEXTURE_DIMENSION) {

            throw new IllegalStateException(
                    "Texture " + texture + " dimensions "
                            + width + "x" + height
                            + " exceed the " + MAX_TEXTURE_DIMENSION
                            + " limit."
            );
        }
    }

    private ModelSnapshot scanInto(
            ModelSnapshot previous
    ) {

        Map<String, ModelDefinition> target =
                new LinkedHashMap<>();

        Map<ResourceId, byte[]> textureSink =
                new LinkedHashMap<>();

        Map<String, String> modelSources =
                new LinkedHashMap<>();

        List<String> assetFingerprints =
                new ArrayList<>();

        /*
         * 0.9.0更新：总容量上限（单文件 16MB 之外
         * 的全局门槛）。
         */
        long totalTextureBytes = 0L;

        int loadedModelCount = 0;


        ConfigSnapshot.Models models =
                configManager.snapshot()
                        .getModels();

        Path dataFolder =
                plugin.getDataFolder()
                        .toPath();

        Path directory =
                dataFolder
                        .resolve(
                                models.getDirectory()
                        )
                        .normalize();

        /*
         * 0.9.0更新：models.directory 必须在插件
         * 数据目录内（Path.resolve 对绝对路径/.. 会逃逸）。
         */
        if (!directory.startsWith(
                dataFolder.normalize()
        )) {

            logger.severe(
                    "models.directory '" + models.getDirectory()
                            + "' escapes the plugin data folder "
                            + "— models disabled."
            );

            return null;
        }

        /*
         * 0.9.0更新：词法检查不够——目录本身可能是
         * 符号链接（plugin/models → D:\secret-models）。
         * 目录存在时做 real path 校验。
         */
        if (Files.isDirectory(
                directory
        )) {

            try {

                Path realDataFolder =
                        dataFolder.toRealPath();

                Path realDirectory =
                        directory.toRealPath();

                if (!realDirectory.startsWith(
                        realDataFolder
                )) {

                    logger.severe(
                            "models.directory resolves outside "
                                    + "the plugin data folder "
                                    + "(symlink?) — models "
                                    + "disabled."
                    );

                    return null;
                }

            } catch (IOException exception) {

                logger.log(
                        Level.WARNING,
                        "Failed to resolve models directory: "
                                + exception.getMessage(),
                        exception
                );

                return null;
            }
        }

        if (!Files.isDirectory(
                directory
        )) {

            logger.warning(
                    () -> "Models directory not found: "
                            + directory
                            + " (models disabled)."
            );

            /*
             * 0.9.0更新： 配套——目录整体被删除是
             * 合法状态（模型全部移除，P0-37）：返回空快照，
             * 让 build（空 → 删除旧包）与 commit（空定义）
             * 正常流转，而不是把"目录缺失"当扫描失败
             * 而保留旧定义。
             */
            try {

                return new ModelSnapshot(
                        Map.of(),
                        Map.of(),
                        computeFingerprint(
                                new java.util.ArrayList<>()
                        ),
                        Map.of()
                );

            } catch (Exception exception) {

                logger.log(
                        Level.SEVERE,
                        "Failed to compute empty-model fingerprint.",
                        exception
                );

                return null;
            }
        }

        try (Stream<Path> stream =
                Files.walk(directory)) {

            /*
             * 0.9.0更新：两阶段扫描——先收集全部模型
             * 文件并做 modelId 重复检测，再逐个导入。
             * 重复 id 直接拒绝（两个都不加载 + 严重日志），
             * 绝不允许"后加载者覆盖前者"，也不允许
             * "A 成功、B 失败回退旧版覆盖 A"的隐蔽回滚。
             */
            List<Path> modelFiles =
                    new java.util.ArrayList<>();

            Map<String, Path> firstByModelId =
                    new LinkedHashMap<>();

            Set<String> duplicateIds =
                    new LinkedHashSet<>();

            for (Path file :
                    stream.filter(
                                    Files::isRegularFile
                            )
                            .sorted()
                            .toList()) {

                if (!file.toString()
                        .endsWith(
                                ".bbmodel"
                        )) {

                    continue;
                }

                /*
                 * 0.9.0更新：拒绝文件级符号链接——
                 * Files.walk 只保证不跟随目录 symlink，
                 * isRegularFile / newInputStream 会跟随文件
                 * symlink（models/x.bbmodel → /etc/passwd 会被
                 * 当模型读）。与纹理侧 PathGuard 同规则。
                 */
                if (Files.isSymbolicLink(
                        file
                )) {

                    logger.severe(
                            "Rejecting symlinked model file: "
                                    + file
                                    + "."
                    );

                    continue;
                }

                /*
                 * 0.9.0更新：大小限制由 importer 的受限
                 * 流式读取保证（删除 stat 检查——TOCTOU）。
                 */
                ResourceId modelId =
                        modelIdFromPath(
                                directory,
                                file
                        );

                if (modelId == null) {

                    logger.warning(
                            "Skipping model file with invalid "
                                    + "id path: "
                                    + file
                                    + " (expected: models/<name>.bbmodel "
                                    + "or models/<ns>/<name>.bbmodel, "
                                    + "lowercase a-z 0-9 _ . -)"
                    );

                    continue;
                }

                Path first =
                        firstByModelId.putIfAbsent(
                                modelId.toString(),
                                file
                        );

                if (first != null) {

                    /*
                     * 重复 modelId：两个都不加载（不依赖
                     * 文件系统枚举顺序决定赢家）。
                     */
                    duplicateIds.add(
                            modelId.toString()
                    );

                    logger.severe(
                            "Duplicate model id '"
                                    + modelId
                                    + "': "
                                    + first
                                    + " and "
                                    + file
                                    + " — both rejected."
                    );
                }

                modelFiles.add(
                        file
                );
            }

            for (Path file :
                    modelFiles) {

                ResourceId modelId =
                        modelIdFromPath(
                                directory,
                                file
                        );

                if (modelId == null) {

                    continue;
                }

                if (duplicateIds.contains(
                        modelId.toString()
                )) {

                    continue;
                }

                try {

                    ImportResult result =
                            BBModelImporter.importFile(
                                    file,
                                    modelId,
                                    logger
                            );

                    /*
                     * 0.9.0更新：per-model 事务
                     * 边界——定义 / 纹理 / 指纹 / 字节全部先入
                     * 局部容器，全部成功后才一次性 merge 到全局
                     * 快照；中途失败不留半成品（此前只回退
                     * definition，纹理/指纹/字节残留导致 orphan
                     * 与预算污染）。
                     */
                    String sourcePath =
                            directory.relativize(
                                            file
                                    )
                                    .toString()
                                    .replace(
                                            '\\',
                                            '/'
                                    );

                    Map<ResourceId, byte[]> localTextures =
                            new LinkedHashMap<>();

                    List<String> localFingerprints =
                            new ArrayList<>();

                    long localBytes =
                            0L;

                    localFingerprints.add(
                            sourcePath
                                    + ":"
                                    + sha256Hex(
                                            mizukichou.nekonyume
                                                    .model.bbmodel
                                                    .BBModelSupport
                                                    .readLimited(
                                                            file,
                                                            mizukichou.nekonyume
                                                                    .model.bbmodel
                                                                    .BBModelImporter
                                                                    .MAX_MODEL_FILE_BYTES
                                                    )
                                    )
                    );

                    localBytes +=
                            putTexturesChecked(
                                    localTextures,
                                    result.getEmbeddedTextures(),
                                    modelId,
                                    file
                            );

                    for (Map.Entry<ResourceId, String> external :
                            result.getExternalTextureSources()
                                    .entrySet()) {

                        Path texturePath =
                                resolveExternalTexture(
                                        file,
                                        external
                                                .getValue()
                                );

                        byte[] textureBytes;

                        try {

                            /*
                             * 0.9.0更新：受限流式读取。
                             */
                            textureBytes =
                                    mizukichou.nekonyume.model
                                            .bbmodel
                                            .BBModelSupport
                                            .readLimited(
                                                    texturePath,
                                                    MAX_TEXTURE_BYTES
                                            );

                        } catch (IOException exception) {

                            throw new java.io.UncheckedIOException(
                                    "External texture "
                                            + external.getValue()
                                            + " of model " + modelId
                                            + " not found at "
                                            + texturePath + ".",
                                    exception
                            );
                        }

                        if (!mizukichou.nekonyume.model.bbmodel
                                .BBModelSupport
                                .hasPngMagic(
                                        textureBytes
                                )) {

                            throw new java.io.UncheckedIOException(
                                    new IOException(
                                            "External texture "
                                                    + external.getValue()
                                                    + " of model " + modelId
                                                    + " is not a PNG file."
                                    )
                            );
                        }

                        localBytes +=
                                putTexturesChecked(
                                        localTextures,
                                        Map.of(
                                                external.getKey(),
                                                textureBytes
                                        ),
                                        modelId,
                                        file
                                );

                        localFingerprints.add(
                                "texture:"
                                        + external.getValue()
                                        + ":"
                                        + sha256Hex(
                                                textureBytes
                                        )
                        );
                    }

                    /*
                     * 全部成功：一次性 merge（容量检查在
                     * merge 之前，保证不半 merge）。
                     */
                    loadedModelCount++;

                    if (loadedModelCount >
                            MAX_TOTAL_MODELS) {

                        throw new CapacityExceededException(
                                "Too many models (max "
                                        + MAX_TOTAL_MODELS
                                        + ")."
                        );
                    }

                    if (totalTextureBytes + localBytes >
                            MAX_TOTAL_TEXTURE_BYTES) {

                        throw new CapacityExceededException(
                                "Total texture bytes exceed "
                                        + MAX_TOTAL_TEXTURE_BYTES
                                        + "."
                        );
                    }

                    target.put(
                            modelId.toString(),
                            result.getDefinition()
                    );

                    modelSources.put(
                            modelId.toString(),
                            sourcePath
                    );

                    assetFingerprints.addAll(
                            localFingerprints
                    );

                    /*
                     * merge 预检：全局已有同 ResourceId 且
                     * 内容不同 → 先抛（保证 merge 原子，不
                     * 半写全局纹理表）。
                     */
                    for (Map.Entry<ResourceId, byte[]> incoming :
                            localTextures.entrySet()) {

                        byte[] existing =
                                textureSink.get(
                                        incoming.getKey()
                                );

                        if (existing != null &&
                                !java.util.Arrays.equals(
                                        existing,
                                        incoming.getValue()
                                )) {

                            throw new IllegalStateException(
                                    "Texture resource "
                                            + incoming.getKey()
                                            + " is produced by two "
                                            + "different sources."
                            );
                        }
                    }

                    textureSink.putAll(
                            localTextures
                    );

                    totalTextureBytes +=
                            localBytes;

                } catch (CapacityExceededException exception) {

                    throw exception;

                } catch (Exception exception) {

                    logger.log(
                            Level.WARNING,
                            "Failed to load model " + modelId
                                    + " from " + file + ": "
                                    + exception.getMessage(),
                            exception
                    );

                    if (previous != null) {

                        ModelDefinition old =
                                previous.definitions.get(
                                        modelId.toString()
                                );

                        if (old != null) {

                            target.put(
                                    modelId.toString(),
                                    old
                            );

                            /*
                             * 0.9.0更新：回退的
                             * 旧定义需要回退的旧纹理——否则
                             * 资源包构建时该模型 missing
                             * texture，整个 reload 失败。
                             * 按模型命名空间路径前缀找回。
                             */
                            String modelPrefix =
                                    "textures/model/"
                                            + modelId.getPath()
                                            + "/";

                            for (Map.Entry<ResourceId, byte[]> oldTexture :
                                    previous.textures.entrySet()) {

                                if (oldTexture.getKey()
                                                .getNamespace()
                                                .equals(
                                                        modelId.getNamespace()
                                                ) &&
                                        oldTexture.getKey()
                                                .getPath()
                                                .startsWith(
                                                        modelPrefix
                                                )) {

                                    totalTextureBytes +=
                                            putTexturesChecked(
                                                    textureSink,
                                                    Map.of(
                                                            oldTexture.getKey(),
                                                            oldTexture.getValue()
                                                    ),
                                                    modelId,
                                                    file
                                            );
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception exception) {

            /*
             * 0.9.0更新：目录遍历异常 = 整个快照无效
             * （walk 可能读到中途才失败——绝不允许
             * "部分模型"状态被当作成功的 reload）。
             */
            logger.log(
                    Level.SEVERE,
                    "Models scan failed (snapshot rejected): "
                            + directory + ".",
                    exception
            );

            return null;
        }
  
        /*
         * 0.9.0更新：快照组装（指纹在扫描期资产
         * 哈希快照上计算——绝不重新读磁盘）。
         */
        String fingerprint;

        try {

            fingerprint =
                    computeFingerprint(
                            assetFingerprints
                    );

        } catch (Exception exception) {

            logger.log(
                    Level.SEVERE,
                    "Failed to compute model fingerprint.",
                    exception
            );

            return null;
        }

        return new ModelSnapshot(
                target,
                textureSink,
                fingerprint,
                modelSources
        );
  }

    /**
     * 文件 → 模型 id：models/x.bbmodel → cats:x；
     * models/ns/x.bbmodel → ns:x。
     */
    private static ResourceId modelIdFromPath(
            Path directory,
            Path file
    ) {

        Path relative =
                directory.relativize(
                        file
                );

        String fileName =
                relative.getFileName()
                        .toString();

        String name =
                fileName.substring(
                        0,
                        fileName.length() -
                                ".bbmodel".length()
                );

        if (name.isBlank()) {
            return null;
        }

        Path parent =
                relative.getParent();

        String namespace =
                parent == null ||
                        parent.toString().isBlank()
                        ? DEFAULT_NAMESPACE
                        : parent.toString()
                                .replace('\\', '/');

        return ResourceId.parse(
                namespace + ":" + name
        );
    }

    /**
     * 活跃渲染条目。
     */
        /**
     * 0.9.0更新：一次扫描的不可变快照——
     * 定义 / 纹理 / 指纹三者永远同版本，
     * 从根上消除"A 定义 + B 纹理 + C 指纹"错位。
     */
    /**
     * 0.9.0更新：容量类异常不是"单模型问题"——
     * 冒泡到扫描层，使整个快照无效（绝不产生
     * "部分新模型 + 部分回退旧模型"的混合状态）。
     */
    private static final class CapacityExceededException
            extends IllegalStateException {

        CapacityExceededException(
                String message
        ) {

            super(
                    message
            );
        }
    }

    private static final class ModelSnapshot {

        final Map<String, ModelDefinition> definitions;

        final Map<ResourceId, byte[]> textures;

        final String fingerprint;

        /**
         * 0.9.0更新：来源绑定（modelId → 相对路径），
         * 诊断"重复 id / 回退来自哪个文件"。
         */
        final Map<String, String> modelSources;

        ModelSnapshot(
                Map<String, ModelDefinition> definitions,
                Map<ResourceId, byte[]> textures,
                String fingerprint,
                Map<String, String> modelSources
        ) {

            this.definitions =
                    java.util.Collections.unmodifiableMap(
                            new LinkedHashMap<>(
                                    definitions
                            )
                    );

            /*
             * 0.9.0更新：不可变快照必须深拷贝字节——
             * unmodifiableMap 只保护 Map 结构，byte[] 仍可
             * 被外部持有者修改。
             */
            java.util.LinkedHashMap<ResourceId, byte[]> copiedTextures =
                    new java.util.LinkedHashMap<>();

            for (java.util.Map.Entry<ResourceId, byte[]> entry :
                    textures.entrySet()) {

                copiedTextures.put(
                        entry.getKey(),
                        entry.getValue()
                                .clone()
                );
            }

            this.textures =
                    java.util.Collections.unmodifiableMap(
                            copiedTextures
                    );

            this.fingerprint =
                    fingerprint;

            this.modelSources =
                    java.util.Collections.unmodifiableMap(
                            new LinkedHashMap<>(
                                    modelSources
                            )
                    );
        }
    }

private static final class ActiveModel {

        final UUID entityUuid;

        final ResourceId modelId;

        final ModelInstance instance;

        final ModelRenderer renderer;

        final AnimationController controller;

        /**
         * 所在世界（跨世界检测，P1-11）：实体传送换世界
         * 时 Display 必须在新世界重建。
         */
        UUID worldUuid;

        boolean recovering;

        /**
         * 0.9.0更新：最近一次触发的动作
         * 信号（边沿检测——同信号窗口内不重播）。
         */
        String lastActionSignal =
                null;

        /**
         * 0.9.0更新：交互代理实体的上次同步位置——
         * 变更检测阈值未达不重传（消除每 tick 无条件
         * teleport 的性能问题）。
         */
        Vec3 lastInteractionPosition =
                null;

        ActiveModel(
                UUID entityUuid,
                ResourceId modelId,
                ModelInstance instance,
                ModelRenderer renderer,
                AnimationController controller,
                UUID worldUuid
        ) {

            this.entityUuid = entityUuid;
            this.modelId = modelId;
            this.instance = instance;
            this.renderer = renderer;
            this.controller = controller;
            this.worldUuid = worldUuid;
        }
    }
}
