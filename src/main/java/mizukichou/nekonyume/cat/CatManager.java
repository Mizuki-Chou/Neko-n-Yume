package mizukichou.nekonyume.cat;

import io.papermc.paper.registry.RegistryKey;
import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.event.CatLevelUpEvent;
import mizukichou.nekonyume.event.CatMeowRankUpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CatManager {

    private final NekoNYume plugin;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    /*
     * MiniMessage 实例。
     *
     * 全局消息格式统一为 MiniMessage。
     * CatManager 中的玩家消息不含玩家可控文本，
     * 因此可以直接使用 MiniMessage。
     * 含名字的消息用 Component.text 拼接。
     */
    private final MiniMessage mm =
            MiniMessage.miniMessage();

    private final Random random =
            new Random();

    /*
     * ============================================================
     * 逻辑猫咪缓存
     * ============================================================
     */

    private final ConcurrentHashMap<
            UUID,
            mizukichou.nekonyume.cat.Cat
            > cats =
            new ConcurrentHashMap<>();

    /*
     * 防止同一个玩家同时执行多个 summon。
     */
    private final Set<UUID> summoning =
            ConcurrentHashMap.newKeySet();

    /*
     * ============================================================
     * 等待世界加载的实体恢复队列
     * ============================================================
     *
     * 玩家登录时，如果猫咪所在世界尚未加载，
     * 玩家会被放入这里。
     *
     * 世界加载完成后，
     * retryPendingWorldRestores() 会再次尝试恢复。
     *
     * key = World UUID
     * value = 等待恢复的玩家 UUID
     */
    private final ConcurrentHashMap<
            UUID,
            Set<UUID>
            > pendingWorldRestores =
            new ConcurrentHashMap<>();

    /*
     * 离线猫咪缓存驱逐阈值。
     *
     * 主人离线且超过该时长没有任何互动时，
     * 从运行时缓存卸载。
     *
     * 数据已经保存，不会丢失。
     * 下次需要时会重新从 players.yml 加载。
     */
    private static final long EVICT_OFFLINE_MS =
            10 * 60 * 1000L;

    public CatManager(
            NekoNYume plugin
    ) {

        this.plugin = plugin;

        this.catKey =
                new NamespacedKey(
                        plugin,
                        "nekonyume_cat"
                );

        this.ownerKey =
                new NamespacedKey(
                        plugin,
                        "owner_uuid"
                );
    }


    /*
     * ============================================================
     * 逻辑猫咪查询
     * ============================================================
     */

    public mizukichou.nekonyume.cat.Cat getCat(
            UUID ownerUUID
    ) {

        if (ownerUUID == null) {
            return null;
        }

        for (mizukichou.nekonyume.cat.Cat cat :
                cats.values()) {

            if (cat.getOwnerUuid()
                    .equals(ownerUUID)) {

                return cat;
            }
        }

        return null;
    }

    public mizukichou.nekonyume.cat.Cat getCat(
            Player player
    ) {

        if (player == null) {
            return null;
        }

        return getCat(
                player.getUniqueId()
        );
    }

    public mizukichou.nekonyume.cat.Cat getCatById(
            UUID catUUID
    ) {

        if (catUUID == null) {
            return null;
        }

        return cats.get(
                catUUID
        );
    }

    public mizukichou.nekonyume.cat.Cat getCatByEntity(
            UUID entityUUID
    ) {

        if (entityUUID == null) {
            return null;
        }

        for (mizukichou.nekonyume.cat.Cat cat :
                cats.values()) {

            if (entityUUID.equals(
                    cat.getEntityUuid()
            )) {

                return cat;
            }
        }

        return null;
    }

    public List<mizukichou.nekonyume.cat.Cat> getCats() {

        return List.copyOf(
                cats.values()
        );
    }

    /*
     * ============================================================
     * 保存一只完整猫咪
     * ============================================================
     */

    public void saveCat(
            mizukichou.nekonyume.cat.Cat cat
    ) {

        if (cat == null) {
            return;
        }

        UUID ownerUUID =
                cat.getOwnerUuid();

        if (ownerUUID == null) {
            return;
        }

        /*
         * 基础身份
         */
        plugin.getDataManager()
                .setCatUUID(
                        ownerUUID,
                        cat.getId()
                );

        plugin.getDataManager()
                .setCatName(
                        ownerUUID,
                        cat.getName()
                );

        /*
         * 成长
         */
        plugin.getDataManager()
                .setCatLevel(
                        ownerUUID,
                        cat.getLevel()
                );

        plugin.getDataManager()
                .setCatExperience(
                        ownerUUID,
                        cat.getExperience()
                );

        /*
         * 喵力 / 喵阶
         */
        plugin.getDataManager()
                .setCatMeowPower(
                        ownerUUID,
                        cat.getMeowPower()
                );

        plugin.getDataManager()
                .setCatMeowRank(
                        ownerUUID,
                        cat.getMeowRank()
                );

        /*
         * 状态
         */
        plugin.getDataManager()
                .setCatAffection(
                        ownerUUID,
                        cat.getAffection()
                );

        plugin.getDataManager()
                .setCatHunger(
                        ownerUUID,
                        cat.getHunger()
                );

        plugin.getDataManager()
                .setCatHealth(
                        ownerUUID,
                        cat.getHealth()
                );

        /*
         * 花色
         */
        if (cat.getVariant() != null &&
                !cat.getVariant().isBlank()) {

            plugin.getDataManager()
                    .setCatVariant(
                            ownerUUID,
                            cat.getVariant()
                    );
        }

        /*
         * 时间
         */
        plugin.getDataManager()
                .setCatCreatedAt(
                        ownerUUID,
                        cat.getCreatedAt()
                );

        plugin.getDataManager()
                .setCatLastFedAt(
                        ownerUUID,
                        cat.getLastFedAt()
                );

        plugin.getDataManager()
                .setCatLastInteractionAt(
                        ownerUUID,
                        cat.getLastInteractionAt()
                );

        /*
         * 当前 Bukkit Entity UUID
         */
        if (cat.getEntityUuid() != null) {

            plugin.getDataManager()
                    .setCatEntityUUID(
                            ownerUUID,
                            cat.getEntityUuid()
                    );
        }

        /*
         * 当前保存位置
         *
         * Cat 运行时只保存 worldName，
         * 这里根据世界名称获取 Bukkit World UUID。
         */
        if (cat.getWorldName() != null &&
                !cat.getWorldName().isBlank()) {

            World world =
                    Bukkit.getWorld(
                            cat.getWorldName()
                    );

            if (world != null) {

                plugin.getDataManager()
                        .setCatLocation(
                                ownerUUID,
                                world.getUID(),
                                cat.getX(),
                                cat.getY(),
                                cat.getZ()
                        );
            }
        }
    }


    /*
     * ============================================================
     * 保存当前内存中的全部猫咪
     * ============================================================
     */

    public void saveAllCats() {

        long now =
                System.currentTimeMillis();

        List<mizukichou.nekonyume.cat.Cat> toEvict =
                new ArrayList<>();

        for (mizukichou.nekonyume.cat.Cat cat :
                cats.values()) {

            UUID entityUUID =
                    cat.getEntityUuid();

            if (entityUUID != null) {

                Entity entity =
                        Bukkit.getEntity(
                                entityUUID
                        );

                if (entity instanceof Cat bukkitCat &&
                        !bukkitCat.isDead() &&
                        bukkitCat.isValid()) {

                    Registry<Cat.Type> registry =
                            getCatVariantRegistry();

                    NamespacedKey key =
                            registry.getKey(
                                    bukkitCat.getCatType()
                            );

                    if (key != null) {

                        cat.setVariant(
                                key.toString()
                        );
                    }

                    syncLogicalCatLocation(
                            cat,
                            bukkitCat
                    );
                }
            }

            saveCat(
                    cat
            );

            /*
             * 离线驱逐：
             *
             * 主人离线且长时间无互动时，
             * 从运行时缓存卸载，
             * 防止缓存长期累积离线玩家。
             *
             * 数据已通过 saveCat 写入，
             * 卸载不会丢失任何状态。
             */
            Player owner =
                    Bukkit.getPlayer(
                            cat.getOwnerUuid()
                    );

            if ((owner == null ||
                    !owner.isOnline()) &&
                    now - cat.getLastInteractionAt()
                            > EVICT_OFFLINE_MS) {

                toEvict.add(
                        cat
                );
            }
        }

        for (mizukichou.nekonyume.cat.Cat cat :
                toEvict) {

            cats.remove(
                    cat.getId()
            );
        }
    }


    /*
     * ============================================================
     * 增加经验
     * ============================================================
     *
     * 统一入口：
     * 所有经验来源都应该通过这里。
     *
     * 负责：
     * 1. 修改运行时 Cat
     * 2. 持久化
     * 3. 升级检测
     * 4. 升级反馈（消息 + 音效）
     * 5. 触发 CatLevelUpEvent
     */

    public void gainExperience(
            Player player,
            mizukichou.nekonyume.cat.Cat cat,
            int amount
    ) {

        if (player == null ||
                cat == null ||
                amount <= 0) {

            return;
        }

        int fromLevel =
                cat.getLevel();

        int gained =
                cat.addExperience(
                        amount
                );

        /*
         * 持久化。
         */
        plugin.getDataManager()
                .setCatExperience(
                        player.getUniqueId(),
                        cat.getExperience()
                );

        plugin.getDataManager()
                .setCatLevel(
                        player.getUniqueId(),
                        cat.getLevel()
                );

        if (gained <= 0) {
            return;
        }

        /*
         * 升级反馈。
         */
        player.sendMessage(
                mm.deserialize(
                        "<gradient:#fde68a:#f59e0b>🎉 </gradient>"
                ).append(
                        Component.text(
                                cat.getName()
                        )
                ).append(
                        mm.deserialize(
                                "<white> 升级到了 <yellow>"
                                        + cat.getLevel()
                                        + " 级</yellow>!</white>"
                        )
                )
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP,
                1.0f,
                1.0f
        );

        /*
         * 事件。
         */
        Bukkit.getPluginManager()
                .callEvent(
                        new CatLevelUpEvent(
                                player,
                                cat,
                                fromLevel,
                                cat.getLevel()
                        )
                );
    }


    /*
     * ============================================================
     * 增加喵力
     * ============================================================
     *
     * 统一入口：
     * 所有喵力来源都应该通过这里
     * （抚摸 / 喂食概率 / 未来喵丹）。
     *
     * 负责：
     * 1. 修改运行时 Cat
     * 2. 持久化
     * 3. 升阶检测
     * 4. 升阶反馈（消息 + 音效 + 粒子）
     * 5. 触发 CatMeowRankUpEvent
     */

    public void grantMeowPower(
            Player player,
            mizukichou.nekonyume.cat.Cat cat,
            int amount
    ) {

        if (player == null ||
                cat == null ||
                amount <= 0) {

            return;
        }

        int fromRank =
                cat.getMeowRank();

        int gained =
                cat.addMeowPower(
                        amount
                );

        /*
         * 持久化。
         */
        plugin.getDataManager()
                .setCatMeowPower(
                        player.getUniqueId(),
                        cat.getMeowPower()
                );

        plugin.getDataManager()
                .setCatMeowRank(
                        player.getUniqueId(),
                        cat.getMeowRank()
                );

        /*
         * 获得喵力的惊喜反馈。
         * 无论是否升阶都会提示。
         */
        player.sendMessage(
                mm.deserialize(
                        "<gradient:#c4b5fd:#a78bfa>✨ 喵光一闪!</gradient>"
                ).append(
                        Component.text(
                                " "
                                        + cat.getName()
                        )
                ).append(
                        mm.deserialize(
                                "<white> 获得了 <light_purple>"
                                        + amount
                                        + " 点喵力</light_purple>!</white>"
                        )
                )
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                1.0f,
                1.5f
        );

        if (gained <= 0) {
            return;
        }

        /*
         * 升阶反馈。
         */
        player.sendMessage(
                mm.deserialize(
                        "<gradient:#c4b5fd:#a78bfa>🌟 喵阶提升!</gradient>"
                ).append(
                        Component.text(
                                " "
                                        + cat.getName()
                        )
                ).append(
                        mm.deserialize(
                                "<white> 提升到了 <light_purple>喵阶 "
                                        + cat.getMeowRank()
                                        + "</light_purple>!</white>"
                        )
                )
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP,
                1.0f,
                1.2f
        );

        /*
         * 粒子：
         * 如果猫实体在线，
         * 在猫的位置生成粒子。
         */
        UUID entityUuid =
                cat.getEntityUuid();

        if (entityUuid != null) {

            Entity entity =
                    Bukkit.getEntity(
                            entityUuid
                    );

            if (entity != null &&
                    entity.isValid()) {

                entity.getWorld()
                        .spawnParticle(
                                Particle.HEART,
                                entity.getLocation()
                                        .add(
                                                0,
                                                1,
                                                0
                                        ),
                                30,
                                0.5,
                                0.5,
                                0.5,
                                0.05
                        );
            }
        }

        /*
         * 事件。
         */
        Bukkit.getPluginManager()
                .callEvent(
                        new CatMeowRankUpEvent(
                                player,
                                cat,
                                fromRank,
                                cat.getMeowRank()
                        )
                );
    }


    /*
     * ============================================================
     * 从玩家存档加载
     * ============================================================
     */

    public mizukichou.nekonyume.cat.Cat loadCat(
            Player player
    ) {

        if (player == null) {
            return null;
        }

        return loadCat(
                player.getUniqueId(),
                player.getName()
        );
    }


    /*
     * ============================================================
     * 从 UUID 加载
     * ============================================================
     */

    public mizukichou.nekonyume.cat.Cat loadCat(
            UUID ownerUUID
    ) {

        return loadCat(
                ownerUUID,
                ownerUUID == null
                        ? "unknown"
                        : ownerUUID.toString()
        );
    }

    private mizukichou.nekonyume.cat.Cat loadCat(
            UUID ownerUUID,
            String logName
    ) {

        if (ownerUUID == null) {
            return null;
        }

        /*
         * 已经存在于内存中就直接返回。
         */
        mizukichou.nekonyume.cat.Cat loaded =
                getCat(ownerUUID);

        if (loaded != null) {
            return loaded;
        }

        /*
         * 确保存档存在。
         */
        plugin.getDataManager()
                .ensureCat(
                        ownerUUID
                );

        /*
         * 猫咪永久 UUID。
         */
        UUID catUUID =
                plugin.getDataManager()
                        .getCatUUID(
                                ownerUUID
                        );

        if (catUUID == null) {

            catUUID =
                    UUID.randomUUID();

            plugin.getDataManager()
                    .setCatUUID(
                            ownerUUID,
                            catUUID
                    );
        }

        /*
         * 完整数据。
         */
        String name =
                plugin.getDataManager()
                        .getCatName(
                                ownerUUID
                        );

        int level =
                plugin.getDataManager()
                        .getCatLevel(
                                ownerUUID
                        );

        int affection =
                plugin.getDataManager()
                        .getCatAffection(
                                ownerUUID
                        );

        int hunger =
                plugin.getDataManager()
                        .getCatHunger(
                                ownerUUID
                        );

        int health =
                plugin.getDataManager()
                        .getCatHealth(
                                ownerUUID
                        );

        String variant =
                plugin.getDataManager()
                        .getCatVariant(
                                ownerUUID
                        );

        long createdAt =
                plugin.getDataManager()
                        .getCatCreatedAt(
                                ownerUUID
                        );

        long lastFedAt =
                plugin.getDataManager()
                        .getCatLastFedAt(
                                ownerUUID
                        );

        long lastInteractionAt =
                plugin.getDataManager()
                        .getCatLastInteractionAt(
                                ownerUUID
                        );

        /*
         * 从存档恢复完整 Cat。
         */
        mizukichou.nekonyume.cat.Cat logicalCat =
                mizukichou.nekonyume.cat.Cat.restore(
                        catUUID,
                        ownerUUID,
                        name,
                        level,
                        affection,
                        hunger,
                        health,
                        variant,
                        createdAt,
                        lastFedAt,
                        lastInteractionAt
                );

        /*
         * 双轨成长。
         */
        logicalCat.setExperience(
                plugin.getDataManager()
                        .getCatExperience(
                                ownerUUID
                        )
        );

        logicalCat.setMeowPower(
                plugin.getDataManager()
                        .getCatMeowPower(
                                ownerUUID
                        )
        );

        logicalCat.setMeowRank(
                plugin.getDataManager()
                        .getCatMeowRank(
                                ownerUUID
                        )
        );

        /*
         * Entity UUID。
         */
        logicalCat.setEntityUuid(
                plugin.getDataManager()
                        .getCatEntityUUID(
                                ownerUUID
                        )
        );

        /*
         * 世界。
         */
        UUID worldUUID =
                plugin.getDataManager()
                        .getCatWorldUUID(
                                ownerUUID
                        );

        if (worldUUID != null) {

            World world =
                    Bukkit.getWorld(
                            worldUUID
                    );

            if (world != null) {

                logicalCat.setWorldName(
                        world.getName()
                );
            }
        }

        /*
         * 位置。
         */
        logicalCat.setX(
                plugin.getDataManager()
                        .getCatX(
                                ownerUUID
                        )
        );

        logicalCat.setY(
                plugin.getDataManager()
                        .getCatY(
                                ownerUUID
                        )
        );

        logicalCat.setZ(
                plugin.getDataManager()
                        .getCatZ(
                                ownerUUID
                        )
        );

        /*
         * 放入运行时缓存。
         */
        cats.put(
                logicalCat.getId(),
                logicalCat
        );

        plugin.getLogger().info(
                "Loaded cat "
                        + logicalCat.getName()
                        + " ("
                        + logicalCat.getId()
                        + ") for "
                        + logName
        );

        return logicalCat;
    }


    /*
     * ============================================================
     * 登录时恢复猫实体
     * ============================================================
     */

    public void restoreCatEntity(
            Player player
    ) {

        if (player == null) {
            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        mizukichou.nekonyume.cat.Cat logicalCat =
                loadCat(
                        player
                );

        if (logicalCat == null) {
            return;
        }

        /*
         * ========================================================
         * 1. 根据 Entity UUID 找原实体
         * ========================================================
         */

        UUID savedEntityUUID =
                logicalCat.getEntityUuid();

        if (savedEntityUUID != null) {

            Entity entity =
                    Bukkit.getEntity(
                            savedEntityUUID
                    );

            if (entity instanceof Cat cat &&
                    !cat.isDead() &&
                    cat.isValid()) {

                String owner =
                        cat.getPersistentDataContainer()
                                .get(
                                        ownerKey,
                                        PersistentDataType.STRING
                                );

                if (playerUUID.toString()
                        .equals(owner)) {

                    updateCat(
                            cat,
                            player,
                            logicalCat
                                    .getName()
                    );

                    restoreCatVariant(
                            playerUUID,
                            cat,
                            logicalCat
                    );

                    syncLogicalCatLocation(
                            logicalCat,
                            cat
                    );

                    plugin.getDataManager()
                            .setCatEntityUUID(
                                    playerUUID,
                                    cat.getUniqueId()
                            );

                    cleanupDuplicateCats(
                            playerUUID,
                            cat
                    );

                    return;
                }
            }
        }

        /*
         * ========================================================
         * 2. 根据最后位置加载区块
         * ========================================================
         */

        UUID worldUUID =
                plugin.getDataManager()
                        .getCatWorldUUID(
                                playerUUID
                        );

        if (worldUUID == null) {

            restoreCatEntityAtFallback(
                    player,
                    logicalCat
            );

            return;
        }

        World world =
                Bukkit.getWorld(
                        worldUUID
                );

        if (world == null) {

            /*
             * 猫咪所在世界尚未加载。
             *
             * 放入等待队列，
             * 世界加载完成后通过
             * retryPendingWorldRestores() 重试。
             */
            plugin.getLogger().warning(
                    "Cannot restore cat "
                            + logicalCat.getId()
                            + " for "
                            + player.getName()
                            + ": world "
                            + worldUUID
                            + " is not loaded. Waiting for world load."
            );

            pendingWorldRestores
                    .computeIfAbsent(
                            worldUUID,
                            key ->
                                    ConcurrentHashMap.newKeySet()
                    )
                    .add(
                            playerUUID
                    );

            return;
        }

        double x =
                logicalCat.getX();

        double z =
                logicalCat.getZ();

        int chunkX =
                ((int) Math.floor(x))
                        >> 4;

        int chunkZ =
                ((int) Math.floor(z))
                        >> 4;

        world.getChunkAtAsync(
                chunkX,
                chunkZ
        ).thenAccept(chunk -> {

            /*
             * 插件已禁用：
             * 不再调度主线程任务。
             * 数据保存由 onDisable 负责。
             */
            if (!plugin.isEnabled()) {
                return;
            }

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                try {

                                    /*
                                     * 玩家在恢复完成前退出。
                                     */
                                    if (!player.isOnline()) {
                                        return;
                                    }

                                    /*
                                     * 再次确认 Entity UUID。
                                     */
                                    if (logicalCat
                                            .getEntityUuid() != null) {

                                        Entity existing =
                                                Bukkit.getEntity(
                                                        logicalCat
                                                                .getEntityUuid()
                                                );

                                        if (existing
                                                instanceof Cat cat &&
                                                !cat.isDead() &&
                                                cat.isValid()) {

                                            String owner =
                                                    cat
                                                            .getPersistentDataContainer()
                                                            .get(
                                                                    ownerKey,
                                                                    PersistentDataType.STRING
                                                            );

                                            if (playerUUID
                                                    .toString()
                                                    .equals(owner)) {

                                                updateCat(
                                                        cat,
                                                        player,
                                                        logicalCat
                                                                .getName()
                                                );

                                                restoreCatVariant(
                                                        playerUUID,
                                                        cat,
                                                        logicalCat
                                                );

                                                syncLogicalCatLocation(
                                                        logicalCat,
                                                        cat
                                                );

                                                return;
                                            }
                                        }
                                    }

                                    /*
                                     * 最后已知区块寻找。
                                     */
                                    Cat oldCat =
                                            findCatInChunk(
                                                    chunk,
                                                    playerUUID
                                            );

                                    if (oldCat != null &&
                                            !oldCat.isDead() &&
                                            oldCat.isValid()) {

                                        updateCat(
                                                oldCat,
                                                player,
                                                logicalCat
                                                        .getName()
                                        );

                                        restoreCatVariant(
                                                playerUUID,
                                                oldCat,
                                                logicalCat
                                        );

                                        logicalCat
                                                .setEntityUuid(
                                                        oldCat
                                                                .getUniqueId()
                                                );

                                        syncLogicalCatLocation(
                                                logicalCat,
                                                oldCat
                                        );

                                        plugin.getDataManager()
                                                .setCatEntityUUID(
                                                        playerUUID,
                                                        oldCat
                                                                .getUniqueId()
                                                );

                                        cleanupDuplicateCats(
                                                playerUUID,
                                                oldCat
                                        );

                                        return;
                                    }

                                    /*
                                     * 扫描当前已加载世界。
                                     */
                                    Cat loadedCat =
                                            findLoadedCatForPlayer(
                                                    playerUUID
                                            );

                                    if (loadedCat != null &&
                                            !loadedCat.isDead() &&
                                            loadedCat.isValid()) {

                                        updateCat(
                                                loadedCat,
                                                player,
                                                logicalCat
                                                        .getName()
                                        );

                                        restoreCatVariant(
                                                playerUUID,
                                                loadedCat,
                                                logicalCat
                                        );

                                        logicalCat
                                                .setEntityUuid(
                                                        loadedCat
                                                                .getUniqueId()
                                                );

                                        syncLogicalCatLocation(
                                                logicalCat,
                                                loadedCat
                                        );

                                        plugin.getDataManager()
                                                .setCatEntityUUID(
                                                        playerUUID,
                                                        loadedCat
                                                                .getUniqueId()
                                                );

                                        cleanupDuplicateCats(
                                                playerUUID,
                                                loadedCat
                                        );

                                        return;
                                    }

                                    /*
                                     * 原实体确实不存在。
                                     */
                                    restoreCatEntityAtSavedLocation(
                                            player,
                                            logicalCat,
                                            world
                                    );

                                } catch (Exception exception) {

                                    plugin.getLogger().severe(
                                            "Failed to restore cat entity for "
                                                    + player.getName()
                                    );

                                    exception.printStackTrace();
                                }
                            }
                    );

        }).exceptionally(exception -> {

            if (!plugin.isEnabled()) {
                return null;
            }

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                try {

                                    plugin.getLogger().warning(
                                            "Failed to restore cat "
                                                    + logicalCat.getId()
                                                    + " for "
                                                    + player.getName()
                                                    + ": "
                                                    + exception.getMessage()
                                    );

                                } catch (Exception ex) {

                                    plugin.getLogger().severe(
                                            "Failed to finish cat restore after chunk failure for "
                                                    + player.getName()
                                    );

                                    ex.printStackTrace();
                                }
                            }
                    );

            return null;
        });
    }


    /*
     * ============================================================
     * 在保存位置恢复
     * ============================================================
     */

    private void restoreCatEntityAtSavedLocation(
            Player player,
            mizukichou.nekonyume.cat.Cat logicalCat,
            World world
    ) {

        Location location =
                new Location(
                        world,
                        logicalCat.getX(),
                        logicalCat.getY(),
                        logicalCat.getZ(),
                        logicalCat.getYaw(),
                        logicalCat.getPitch()
                );

        Cat cat =
                (Cat) world.spawnEntity(
                        location,
                        EntityType.CAT
                );

        updateCat(
                cat,
                player,
                logicalCat.getName()
        );

        /*
         * 恢复永久花色。
         */
        restoreCatVariant(
                player.getUniqueId(),
                cat,
                logicalCat
        );

        /*
         * 更新 Entity UUID。
         */
        logicalCat.setEntityUuid(
                cat.getUniqueId()
        );

        syncLogicalCatLocation(
                logicalCat,
                cat
        );

        plugin.getDataManager()
                .setCatEntityUUID(
                        player.getUniqueId(),
                        cat.getUniqueId()
                );

        cleanupDuplicateCats(
                player.getUniqueId(),
                cat
        );

        plugin.getLogger().info(
                "Restored cat "
                        + logicalCat.getName()
                        + " at saved location for "
                        + player.getName()
        );
    }


    /*
     * ============================================================
     * 没有位置时的兜底恢复
     * ============================================================
     */

    private void restoreCatEntityAtFallback(
            Player player,
            mizukichou.nekonyume.cat.Cat logicalCat
    ) {

        Location location =
                player.getLocation()
                        .clone();

        World world =
                location.getWorld();

        if (world == null) {

            plugin.getLogger().warning(
                    "Cannot restore cat "
                            + logicalCat.getId()
                            + " for "
                            + player.getName()
                            + ": player world is null."
            );

            return;
        }

        Cat cat =
                (Cat) world.spawnEntity(
                        location,
                        EntityType.CAT
                );

        updateCat(
                cat,
                player,
                logicalCat.getName()
        );

        restoreCatVariant(
                player.getUniqueId(),
                cat,
                logicalCat
        );

        logicalCat.setEntityUuid(
                cat.getUniqueId()
        );

        syncLogicalCatLocation(
                logicalCat,
                cat
        );

        plugin.getDataManager()
                .setCatEntityUUID(
                        player.getUniqueId(),
                        cat.getUniqueId()
                );

        saveCatLocation(
                player,
                cat
        );

        cleanupDuplicateCats(
                player.getUniqueId(),
                cat
        );

        plugin.getLogger().info(
                "Restored cat "
                        + logicalCat.getName()
                        + " at fallback location for "
                        + player.getName()
        );
    }


    /*
     * ============================================================
     * 世界加载后重试实体恢复
     * ============================================================
     */

    public void retryPendingWorldRestores(
            World world
    ) {

        if (world == null) {
            return;
        }

        Set<UUID> players =
                pendingWorldRestores.remove(
                        world.getUID()
                );

        if (players == null ||
                players.isEmpty()) {

            return;
        }

        for (UUID playerUUID :
                players) {

            Player player =
                    Bukkit.getPlayer(
                            playerUUID
                    );

            if (player != null &&
                    player.isOnline()) {

                restoreCatEntity(
                        player
                );
            }
        }
    }


    /*
     * ============================================================
     * 清除玩家的待恢复记录
     * ============================================================
     */

    public void clearPendingRestore(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return;
        }

        for (Set<UUID> players :
                pendingWorldRestores.values()) {

            players.remove(
                    playerUUID
            );
        }
    }


    /*
     * ============================================================
     * 清除实体绑定（不删除逻辑猫）
     * ============================================================
     *
     * 实体死亡 / 被移除时使用。
     *
     * 只清除运行时与存档中的 entity-uuid，
     * 逻辑猫与全部状态数据完整保留。
     *
     * 玩家下次 /nekoyume summon 时
     * 会自然走恢复流程。
     */

    public void clearEntityBinding(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return;
        }

        mizukichou.nekonyume.cat.Cat cat =
                getCat(playerUUID);

        if (cat != null) {

            cat.setEntityUuid(
                    null
            );
        }

        plugin.getDataManager()
                .removeCatEntityUUID(
                        playerUUID
                );
    }


    /*
     * ============================================================
     * 从存档重新加载
     * ============================================================
     */

    public mizukichou.nekonyume.cat.Cat reloadCat(
            Player player
    ) {

        if (player == null) {
            return null;
        }

        removeLogicalCat(
                player.getUniqueId()
        );

        return loadCat(
                player
        );
    }


    /*
     * ============================================================
     * 创建 / 绑定逻辑猫
     * ============================================================
     */

    private mizukichou.nekonyume.cat.Cat bindLogicalCat(
            Player player,
            Cat entity,
            String name
    ) {

        UUID ownerUUID =
                player.getUniqueId();

        mizukichou.nekonyume.cat.Cat existing =
                getCat(ownerUUID);

        if (existing != null) {

            existing.setEntityUuid(
                    entity.getUniqueId()
            );

            existing.setName(
                    name
            );

            syncLogicalCatLocation(
                    existing,
                    entity
            );

            /*
             * 如果实体已经有明确花色，
             * 将其与逻辑 Cat 对齐。
             */
            restoreCatVariant(
                    ownerUUID,
                    entity,
                    existing
            );

            return existing;
        }

        /*
         * 确保玩家存在存档。
         */
        plugin.getDataManager()
                .ensureCat(
                        ownerUUID
                );

        UUID catUUID =
                plugin.getDataManager()
                        .getCatUUID(
                                ownerUUID
                        );

        if (catUUID == null) {

            catUUID =
                    UUID.randomUUID();

            plugin.getDataManager()
                    .setCatUUID(
                            ownerUUID,
                            catUUID
                    );
        }

        String variant =
                plugin.getDataManager()
                        .getCatVariant(
                                ownerUUID
                        );

        mizukichou.nekonyume.cat.Cat logicalCat =
                mizukichou.nekonyume.cat.Cat.restore(
                        catUUID,
                        ownerUUID,
                        name,
                        plugin.getDataManager()
                                .getCatLevel(
                                        ownerUUID
                                ),
                        plugin.getDataManager()
                                .getCatAffection(
                                        ownerUUID
                                ),
                        plugin.getDataManager()
                                .getCatHunger(
                                        ownerUUID
                                ),
                        plugin.getDataManager()
                                .getCatHealth(
                                        ownerUUID
                                ),
                        variant,
                        plugin.getDataManager()
                                .getCatCreatedAt(
                                        ownerUUID
                                ),
                        plugin.getDataManager()
                                .getCatLastFedAt(
                                        ownerUUID
                                ),
                        plugin.getDataManager()
                                .getCatLastInteractionAt(
                                        ownerUUID
                                )
                );

        /*
         * 双轨成长。
         */
        logicalCat.setExperience(
                plugin.getDataManager()
                        .getCatExperience(
                                ownerUUID
                        )
        );

        logicalCat.setMeowPower(
                plugin.getDataManager()
                        .getCatMeowPower(
                                ownerUUID
                        )
        );

        logicalCat.setMeowRank(
                plugin.getDataManager()
                        .getCatMeowRank(
                                ownerUUID
                        )
        );

        logicalCat.setEntityUuid(
                entity.getUniqueId()
        );

        syncLogicalCatLocation(
                logicalCat,
                entity
        );

        /*
         * 确保花色也一致。
         */
        restoreCatVariant(
                ownerUUID,
                entity,
                logicalCat
        );

        cats.put(
                logicalCat.getId(),
                logicalCat
        );

        return logicalCat;
    }


    /*
     * ============================================================
     * 删除逻辑猫
     * ============================================================
     */

    public void removeLogicalCat(
            UUID ownerUUID
    ) {

        mizukichou.nekonyume.cat.Cat cat =
                getCat(ownerUUID);

        if (cat == null) {
            return;
        }

        cats.remove(
                cat.getId()
        );
    }

    public void clearLogicalCats() {

        cats.clear();
    }


    /*
     * ============================================================
     * 同步位置
     * ============================================================
     */

    private void syncLogicalCatLocation(
            mizukichou.nekonyume.cat.Cat logicalCat,
            Cat entity
    ) {

        Location location =
                entity.getLocation();

        if (location.getWorld() == null) {
            return;
        }

        logicalCat.setWorldName(
                location.getWorld().getName()
        );

        logicalCat.setX(
                location.getX()
        );

        logicalCat.setY(
                location.getY()
        );

        logicalCat.setZ(
                location.getZ()
        );

        logicalCat.setYaw(
                location.getYaw()
        );

        logicalCat.setPitch(
                location.getPitch()
        );

        logicalCat.setEntityUuid(
                entity.getUniqueId()
        );
    }


    /*
     * ============================================================
     * 主动召唤
     * ============================================================
     */

    public void spawnCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        if (!summoning.add(
                playerUUID
        )) {

            player.sendMessage(
                    mm.deserialize(
                            "<yellow>🐱 正在寻找你的猫咪，请稍等一下!</yellow>"
                    )
            );

            return;
        }

        /*
         * 包装回调：
         *
         * 无论成功还是失败，
         * 只要回调被执行，
         * 就保证释放 summoning 标记，
         * 防止玩家永远无法再次召唤。
         */
        Consumer<Boolean> wrappedCallback =
                result -> {

                    try {

                        callback.accept(
                                result
                        );

                    } finally {

                        summoning.remove(
                                playerUUID
                        );
                    }
                };

        try {

            findCat(
                    player,
                    name,
                    wrappedCallback
            );

        } catch (Exception exception) {

            summoning.remove(
                    playerUUID
            );

            plugin.getLogger().severe(
                    "Failed to summon cat for "
                            + player.getName()
            );

            exception.printStackTrace();

            player.sendMessage(
                    mm.deserialize(
                            "<red>🐱 召唤猫咪时发生错误，请查看服务器日志。</red>"
                    )
            );
        }
    }


    /*
     * ============================================================
     * 主动召唤 - 寻找实体
     * ============================================================
     */

    private void findCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        loadCat(
                player
        );

        UUID savedEntityUUID =
                plugin.getDataManager()
                        .getCatEntityUUID(
                                playerUUID
                        );

        if (savedEntityUUID != null) {

            Entity entity =
                    Bukkit.getEntity(
                            savedEntityUUID
                    );

            if (entity instanceof Cat cat &&
                    !cat.isDead() &&
                    cat.isValid()) {

                cleanupDuplicateCats(
                        playerUUID,
                        cat
                );

                bindLogicalCat(
                        player,
                        cat,
                        name
                );

                prepareTeleport(
                        player,
                        cat,
                        name,
                        callback,
                        false
                );

                return;
            }
        }

        loadLastKnownChunk(
                player,
                name,
                callback
        );
    }


    /*
     * ============================================================
     * 主动召唤 - 加载旧位置区块
     * ============================================================
     */

    private void loadLastKnownChunk(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        UUID worldUUID =
                plugin.getDataManager()
                        .getCatWorldUUID(
                                playerUUID
                        );

        if (worldUUID == null) {

            restoreNewCat(
                    player,
                    name,
                    callback
            );

            return;
        }

        World world =
                Bukkit.getWorld(
                        worldUUID
                );

        if (world == null) {

            Cat loadedCat =
                    findLoadedCatForPlayer(
                            playerUUID
                    );

            if (loadedCat != null) {

                plugin.getDataManager()
                        .setCatEntityUUID(
                                playerUUID,
                                loadedCat.getUniqueId()
                        );

                cleanupDuplicateCats(
                        playerUUID,
                        loadedCat
                );

                bindLogicalCat(
                        player,
                        loadedCat,
                        name
                );

                prepareTeleport(
                        player,
                        loadedCat,
                        name,
                        callback,
                        false
                );

                return;
            }

            restoreNewCat(
                    player,
                    name,
                    callback
            );

            return;
        }

        double x =
                plugin.getDataManager()
                        .getCatX(
                                playerUUID
                        );

        double z =
                plugin.getDataManager()
                        .getCatZ(
                                playerUUID
                        );

        int chunkX =
                ((int) Math.floor(x))
                        >> 4;

        int chunkZ =
                ((int) Math.floor(z))
                        >> 4;

        world.getChunkAtAsync(
                chunkX,
                chunkZ
        ).thenAccept(chunk -> {

            /*
             * 插件已禁用：
             * 不再调度主线程任务。
             * 数据保存由 onDisable 负责。
             */
            if (!plugin.isEnabled()) {
                return;
            }

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                try {

                                    Cat oldCat =
                                            findCatInChunk(
                                                    chunk,
                                                    playerUUID
                                            );

                                    if (oldCat != null &&
                                            !oldCat.isDead() &&
                                            oldCat.isValid()) {

                                        plugin.getDataManager()
                                                .setCatEntityUUID(
                                                        playerUUID,
                                                        oldCat.getUniqueId()
                                                );

                                        cleanupDuplicateCats(
                                                playerUUID,
                                                oldCat
                                        );

                                        bindLogicalCat(
                                                player,
                                                oldCat,
                                                name
                                        );

                                        prepareTeleport(
                                                player,
                                                oldCat,
                                                name,
                                                callback,
                                                false
                                        );

                                        return;
                                    }

                                    Cat loadedCat =
                                            findLoadedCatForPlayer(
                                                    playerUUID
                                            );

                                    if (loadedCat != null &&
                                            !loadedCat.isDead() &&
                                            loadedCat.isValid()) {

                                        plugin.getDataManager()
                                                .setCatEntityUUID(
                                                        playerUUID,
                                                        loadedCat.getUniqueId()
                                                );

                                        cleanupDuplicateCats(
                                                playerUUID,
                                                loadedCat
                                        );

                                        bindLogicalCat(
                                                player,
                                                loadedCat,
                                                name
                                        );

                                        prepareTeleport(
                                                player,
                                                loadedCat,
                                                name,
                                                callback,
                                                false
                                        );

                                        return;
                                    }

                                    restoreNewCat(
                                            player,
                                            name,
                                            callback
                                    );

                                } catch (Exception exception) {

                                    plugin.getLogger().severe(
                                            "Failed to process cat chunk for "
                                                    + player.getName()
                                    );

                                    exception.printStackTrace();

                                    callback.accept(
                                            false
                                    );
                                }
                            }
                    );

        }).exceptionally(exception -> {

            if (!plugin.isEnabled()) {
                return null;
            }

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                try {

                                    plugin.getLogger().warning(
                                            "Failed to load cat chunk for "
                                                    + player.getName()
                                                    + ": "
                                                    + exception.getMessage()
                                    );

                                    Cat loadedCat =
                                            findLoadedCatForPlayer(
                                                    playerUUID
                                            );

                                    if (loadedCat != null &&
                                            !loadedCat.isDead() &&
                                            loadedCat.isValid()) {

                                        plugin.getDataManager()
                                                .setCatEntityUUID(
                                                        playerUUID,
                                                        loadedCat.getUniqueId()
                                                );

                                        cleanupDuplicateCats(
                                                playerUUID,
                                                loadedCat
                                        );

                                        bindLogicalCat(
                                                player,
                                                loadedCat,
                                                name
                                        );

                                        prepareTeleport(
                                                player,
                                                loadedCat,
                                                name,
                                                callback,
                                                false
                                        );

                                        return;
                                    }

                                    restoreNewCat(
                                            player,
                                            name,
                                            callback
                                    );

                                } catch (Exception ex) {

                                    plugin.getLogger().severe(
                                            "Failed to recover cat after chunk failure for "
                                                    + player.getName()
                                    );

                                    ex.printStackTrace();

                                    callback.accept(
                                            false
                                    );
                                }
                            }
                    );

            return null;
        });
    }


    /*
     * ============================================================
     * 区块内寻找
     * ============================================================
     */

    private Cat findCatInChunk(
            Chunk chunk,
            UUID playerUUID
    ) {

        for (Entity entity :
                chunk.getEntities()) {

            if (!(entity instanceof Cat cat)) {
                continue;
            }

            if (cat.isDead() ||
                    !cat.isValid()) {

                continue;
            }

            if (!cat.getPersistentDataContainer()
                    .has(
                            catKey,
                            PersistentDataType.BYTE
                    )) {

                continue;
            }

            String ownerUUID =
                    cat.getPersistentDataContainer()
                            .get(
                                    ownerKey,
                                    PersistentDataType.STRING
                            );

            if (ownerUUID == null) {
                continue;
            }

            if (!playerUUID.toString()
                    .equals(ownerUUID)) {

                continue;
            }

            return cat;
        }

        return null;
    }


    /*
     * ============================================================
     * 全部已加载世界寻找
     * ============================================================
     */

    private Cat findLoadedCatForPlayer(
            UUID playerUUID
    ) {

        for (World world :
                Bukkit.getWorlds()) {

            for (Entity entity :
                    world.getEntities()) {

                if (!(entity instanceof Cat cat)) {
                    continue;
                }

                if (cat.isDead() ||
                        !cat.isValid()) {

                    continue;
                }

                if (!cat.getPersistentDataContainer()
                        .has(
                                catKey,
                                PersistentDataType.BYTE
                        )) {

                    continue;
                }

                String ownerUUID =
                        cat.getPersistentDataContainer()
                                .get(
                                        ownerKey,
                                        PersistentDataType.STRING
                                );

                if (ownerUUID == null) {
                    continue;
                }

                if (playerUUID.toString()
                        .equals(ownerUUID)) {

                    return cat;
                }
            }
        }

        return null;
    }


    /*
     * ============================================================
     * 清理重复猫
     * ============================================================
     */

    private void cleanupDuplicateCats(
            UUID playerUUID,
            Cat keepCat
    ) {

        for (World world :
                Bukkit.getWorlds()) {

            for (Entity entity :
                    world.getEntities()) {

                if (!(entity instanceof Cat cat)) {
                    continue;
                }

                if (cat.isDead()) {
                    continue;
                }

                if (cat.equals(keepCat)) {
                    continue;
                }

                if (!cat.getPersistentDataContainer()
                        .has(
                                catKey,
                                PersistentDataType.BYTE
                        )) {

                    continue;
                }

                String ownerUUID =
                        cat.getPersistentDataContainer()
                                .get(
                                        ownerKey,
                                        PersistentDataType.STRING
                                );

                if (ownerUUID == null) {
                    continue;
                }

                if (!playerUUID.toString()
                        .equals(ownerUUID)) {

                    continue;
                }

                cat.remove();
            }
        }
    }


    /*
     * ============================================================
     * 主动召唤 - 传送到玩家
     * ============================================================
     */

    private void prepareTeleport(
            Player player,
            Cat cat,
            String name,
            Consumer<Boolean> callback,
            boolean replacement
    ) {

        if (cat.isDead() ||
                !cat.isValid()) {

            restoreNewCat(
                    player,
                    name,
                    callback
            );

            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        updateCat(
                cat,
                player,
                name
        );

        mizukichou.nekonyume.cat.Cat logicalCat =
                bindLogicalCat(
                        player,
                        cat,
                        name
                );

        restoreCatVariant(
                playerUUID,
                cat,
                logicalCat
        );

        syncLogicalCatLocation(
                logicalCat,
                cat
        );

        Location target =
                player.getLocation()
                        .clone();

        World targetWorld =
                target.getWorld();

        if (targetWorld == null) {

            callback.accept(
                    replacement
            );

            return;
        }

        targetWorld.getChunkAtAsync(
                target.getBlockX() >> 4,
                target.getBlockZ() >> 4
        ).thenAccept(chunk -> {

            /*
             * 插件已禁用：
             * 不再调度主线程任务。
             */
            if (!plugin.isEnabled()) {
                return;
            }

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                try {

                                    if (cat.isDead() ||
                                            !cat.isValid()) {

                                        restoreNewCat(
                                                player,
                                                name,
                                                callback
                                        );

                                        return;
                                    }

                                    boolean success =
                                            cat.teleport(
                                                    target
                                            );

                                    if (!success) {

                                        success =
                                                cat.teleport(
                                                        target
                                                );
                                    }

                                    if (!success) {

                                        player.sendMessage(
                                                mm.deserialize(
                                                        "<red>🐱 猫咪暂时无法传送，请稍后再试。</red>"
                                                )
                                        );

                                        callback.accept(
                                                false
                                        );

                                        return;
                                    }

                                    syncLogicalCatLocation(
                                            logicalCat,
                                            cat
                                    );

                                    saveCatLocation(
                                            player,
                                            cat
                                    );

                                    plugin.getDataManager()
                                            .setCatEntityUUID(
                                                    playerUUID,
                                                    cat.getUniqueId()
                                            );

                                    cleanupDuplicateCats(
                                            playerUUID,
                                            cat
                                    );

                                    callback.accept(
                                            replacement
                                    );

                                } catch (Exception exception) {

                                    plugin.getLogger().severe(
                                            "Failed to teleport cat for "
                                                    + player.getName()
                                    );

                                    exception.printStackTrace();

                                    callback.accept(
                                            false
                                    );
                                }
                            }
                    );

        }).exceptionally(exception -> {

            if (!plugin.isEnabled()) {
                return null;
            }

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                try {

                                    player.sendMessage(
                                            mm.deserialize(
                                                    "<red>🐱 猫咪目标区块加载失败，请稍后再试。</red>"
                                            )
                                    );

                                    plugin.getLogger().warning(
                                            "Failed to load target chunk for "
                                                    + player.getName()
                                                    + ": "
                                                    + exception.getMessage()
                                    );

                                    callback.accept(
                                            false
                                    );

                                } catch (Exception ex) {

                                    plugin.getLogger().severe(
                                            "Failed to finish teleport after chunk failure for "
                                                    + player.getName()
                                    );

                                    ex.printStackTrace();

                                    callback.accept(
                                            false
                                    );
                                }
                            }
                    );

            return null;
        });
    }


    /*
     * ============================================================
     * 主动召唤 - 恢复 / 新建实体
     * ============================================================
     */

    private void restoreNewCat(
            Player player,
            String name,
            Consumer<Boolean> callback
    ) {

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 最后一次检查当前世界中是否已经有猫。
         */
        Cat existing =
                findLoadedCatForPlayer(
                        playerUUID
                );

        if (existing != null &&
                !existing.isDead() &&
                existing.isValid()) {

            plugin.getDataManager()
                    .setCatEntityUUID(
                            playerUUID,
                            existing.getUniqueId()
                    );

            cleanupDuplicateCats(
                    playerUUID,
                    existing
            );

            bindLogicalCat(
                    player,
                    existing,
                    name
            );

            prepareTeleport(
                    player,
                    existing,
                    name,
                    callback,
                    false
            );

            return;
        }

        /*
         * 新建 Bukkit 猫实体。
         */
        Cat cat =
                (Cat) player.getWorld()
                        .spawnEntity(
                                player.getLocation(),
                                EntityType.CAT
                        );

        /*
         * 设置基础属性。
         */
        updateCat(
                cat,
                player,
                name
        );

        /*
         * 建立逻辑猫关系。
         */
        mizukichou.nekonyume.cat.Cat logicalCat =
                bindLogicalCat(
                        player,
                        cat,
                        name
                );

        /*
         * 确定并永久保存花色。
         */
        restoreCatVariant(
                playerUUID,
                cat,
                logicalCat
        );

        /*
         * Entity UUID。
         */
        plugin.getDataManager()
                .setCatEntityUUID(
                        playerUUID,
                        cat.getUniqueId()
                );

        /*
         * 位置。
         */
        saveCatLocation(
                player,
                cat
        );

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {

                            if (!cat.isDead() &&
                                    cat.isValid()) {

                                cleanupDuplicateCats(
                                        playerUUID,
                                        cat
                                );

                                syncLogicalCatLocation(
                                        logicalCat,
                                        cat
                                );
                            }
                        }
                );

        player.sendMessage(
                mm.deserialize(
                        "<yellow>🐱 原猫咪实体无法找到，<gray>已经恢复了一只相同的猫咪。</gray></yellow>"
                )
        );

        callback.accept(
                true
        );
    }


    /*
     * ============================================================
     * 更新 Bukkit 猫实体
     * ============================================================
     */

    private void updateCat(
            Cat cat,
            Player player,
            String name
    ) {

        /*
         * 过滤 §，防止名字注入传统颜色码。
         *
         * 命令层已经过滤过，
         * 这里是纵深防御。
         */
        String safeName =
                name == null
                        ? ""
                        : name.replace(
                        "§",
                        ""
                );

        cat.setCustomName(
                "§d🐱 " + safeName
        );

        cat.setCustomNameVisible(
                true
        );

        cat.setOwner(
                player
        );

        cat.setTamed(
                true
        );

        /*
         * 猫暂时无敌。
         *
         * 这是纪念性伴侣猫，
         * 不应死于岩浆 / 怪物 / 摔落。
         *
         * 注意：
         * /kill 与部分插件的直接致死仍然有效，
         * 因此 EntityDeathEvent 的兜底处理
         * 依然保留。
         */
        cat.setInvulnerable(
                true
        );

        cat.getPersistentDataContainer()
                .set(
                        catKey,
                        PersistentDataType.BYTE,
                        (byte) 1
                );

        cat.getPersistentDataContainer()
                .set(
                        ownerKey,
                        PersistentDataType.STRING,
                        player.getUniqueId()
                                .toString()
                );
    }


    /*
     * ============================================================
     * 保存位置
     * ============================================================
     */

    private void saveCatLocation(
            Player player,
            Cat entity
    ) {

        Location location =
                entity.getLocation();

        if (location.getWorld() == null) {
            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        plugin.getDataManager()
                .setCatLocation(
                        playerUUID,
                        location.getWorld()
                                .getUID(),
                        location.getX(),
                        location.getY(),
                        location.getZ()
                );

        mizukichou.nekonyume.cat.Cat logicalCat =
                getCat(playerUUID);

        if (logicalCat == null) {
            return;
        }

        syncLogicalCatLocation(
                logicalCat,
                entity
        );
    }


    /*
     * ============================================================
     * 更新名称
     * ============================================================
     */

    public void updateCatName(
            Player player,
            String name
    ) {

        if (player == null ||
                name == null ||
                name.isBlank()) {

            return;
        }

        /*
         * 过滤 §，防止名字注入传统颜色码。
         */
        String safeName =
                name.replace(
                        "§",
                        ""
                );

        UUID playerUUID =
                player.getUniqueId();

        mizukichou.nekonyume.cat.Cat logicalCat =
                loadCat(
                        player
                );

        if (logicalCat != null) {

            logicalCat.setName(
                    safeName
            );
        }

        plugin.getDataManager()
                .setCatName(
                        playerUUID,
                        safeName
                );

        UUID entityUUID =
                plugin.getDataManager()
                        .getCatEntityUUID(
                                playerUUID
                        );

        if (entityUUID == null) {
            return;
        }

        Entity entity =
                Bukkit.getEntity(
                        entityUUID
                );

        if (!(entity instanceof Cat cat)) {
            return;
        }

        if (cat.isDead() ||
                !cat.isValid()) {

            return;
        }

        cat.setCustomName(
                "§d🐱 " + safeName
        );

        cat.setCustomNameVisible(
                true
        );
    }


    /*
     * ============================================================
     * Cat Variant Registry
     * ============================================================
     */

    private Registry<Cat.Type>
    getCatVariantRegistry() {

        return io.papermc.paper.registry.RegistryAccess
                .registryAccess()
                .getRegistry(
                        RegistryKey.CAT_VARIANT
                );
    }


    /*
     * ============================================================
     * 随机花色
     * ============================================================
     */

    private Cat.Type getRandomCatType() {

        List<Cat.Type> types =
                getCatVariantRegistry()
                        .stream()
                        .toList();

        if (types.isEmpty()) {

            throw new IllegalStateException(
                    "No cat variants are registered!"
            );
        }

        return types.get(
                random.nextInt(
                        types.size()
                )
        );
    }


    /*
     * ============================================================
     * 保存花色
     * ============================================================
     */

    private String saveCatVariant(
            UUID playerUUID,
            Cat.Type variant
    ) {

        if (playerUUID == null ||
                variant == null) {

            return null;
        }

        Registry<Cat.Type> registry =
                getCatVariantRegistry();

        NamespacedKey key =
                registry.getKey(
                        variant
                );

        if (key == null) {
            return null;
        }

        String variantString =
                key.toString();

        plugin.getDataManager()
                .setCatVariant(
                        playerUUID,
                        variantString
                );

        return variantString;
    }


    /*
     * ============================================================
     * 恢复 / 建立永久花色
     * ============================================================
     *
     * 规则：
     *
     * 1. Cat 已有 variant
     *    → 使用 Cat 的 variant
     *
     * 2. 存档已有 variant
     *    → 使用存档 variant
     *
     * 3. 当前 Bukkit 实体存在
     *    → 使用当前实体花色并永久保存
     *
     * 4. 完全没有历史信息
     *    → 随机一次，然后永久保存
     */

    private void restoreCatVariant(
            UUID playerUUID,
            Cat entity,
            mizukichou.nekonyume.cat.Cat logicalCat
    ) {

        if (playerUUID == null ||
                entity == null ||
                logicalCat == null) {

            return;
        }

        /*
         * ========================================================
         * 1. 逻辑 Cat 已经有 variant
         * ========================================================
         */

        String logicalVariant =
                logicalCat.getVariant();

        if (logicalVariant != null &&
                !logicalVariant.isBlank()) {

            Cat.Type variant =
                    getCatType(
                            logicalVariant
                    );

            if (variant != null) {

                entity.setCatType(
                        variant
                );

                return;
            }
        }

        /*
         * ========================================================
         * 2. 从 PlayerDataManager 恢复
         * ========================================================
         */

        String savedVariant =
                plugin.getDataManager()
                        .getCatVariant(
                                playerUUID
                        );

        if (savedVariant != null &&
                !savedVariant.isBlank()) {

            Cat.Type variant =
                    getCatType(
                            savedVariant
                    );

            if (variant != null) {

                entity.setCatType(
                        variant
                );

                logicalCat.setVariant(
                        savedVariant
                );

                return;
            }

            /*
             * 存档中的 variant 无效。
             *
             * 不让插件崩溃。
             * 后面使用当前实体花色修复。
             */
        }

        /*
         * ========================================================
         * 3. 使用当前 Bukkit 实体已经拥有的花色
         * ========================================================
         *
         * 这个分支对老存档非常重要。
         *
         * 如果老存档没有保存 variant，
         * 但原实体还存在，
         * 我们就把它当前的真实花色记录下来。
         */

        Cat.Type currentType =
                entity.getCatType();

        if (currentType == null) {

            /*
             * ====================================================
             * 4. 完全没有可用历史信息
             * ====================================================
             *
             * 这种情况通常是：
             *
             * - 第一次生成
             * - 老存档没有 variant
             * - 原实体已经不存在
             *
             * 只能随机一次。
             */

            currentType =
                    getRandomCatType();

            entity.setCatType(
                    currentType
            );
        }

        String variantString =
                saveCatVariant(
                        playerUUID,
                        currentType
                );

        if (variantString != null) {

            logicalCat.setVariant(
                    variantString
            );
        }
    }

    /*
     * ============================================================
     * NamespacedKey → Cat.Type
     * ============================================================
     */

    private Cat.Type getCatType(
            String variantString
    ) {

        if (variantString == null ||
                variantString.isBlank()) {

            return null;
        }

        NamespacedKey key =
                NamespacedKey.fromString(
                        variantString
                );

        if (key == null) {
            return null;
        }

        return getCatVariantRegistry()
                .get(
                        key
                );
    }


    /*
     * ============================================================
     * Getter
     * ============================================================
     */

    public NamespacedKey getCatKey() {
        return catKey;
    }

    public NamespacedKey getOwnerKey() {
        return ownerKey;
    }
}
