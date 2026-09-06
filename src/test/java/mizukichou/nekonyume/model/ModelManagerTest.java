package mizukichou.nekonyume.model;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.storage.MemoryCatStore;
import mizukichou.nekonyume.testutil.FakeBukkit;
import mizukichou.nekonyume.testutil.FakeCatEntityRuntime;
import mizukichou.nekonyume.testutil.FakeRenderBackend;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ModelManager：模型选择回退、生命周期创建/幂等/销毁、
 * 恢复期视觉切换、模型扫描与重载保留旧版。
 */
class ModelManagerTest {

    private static final Logger LOGGER =
            Logger.getAnonymousLogger();

    @TempDir
    Path tempDir;

    private Plugin plugin;

    private FakeCatEntityRuntime runtime;

    private MemoryCatStore store;

    private CatBattleState battleState;

    private ConfigManager configManager;

    private ModelManager modelManager;

    private FakeRenderBackend backend;

    private World world;

    private org.bukkit.entity.Cat entity;

    private UUID ownerUuid;

    private Cat logicalCat;

    @BeforeEach
    void setUp() throws Exception {

        plugin =
                FakeBukkit.proxy(
                        Plugin.class,
                        Map.of(
                                "getDataFolder",
                                tempDir.toFile()
                        ),
                        null
                );

        runtime =
                new FakeCatEntityRuntime();

        store =
                new MemoryCatStore();

        battleState =
                new CatBattleState();

        Files.createDirectories(
                tempDir.resolve("models")
        );

        Files.writeString(
                tempDir.resolve("models/black_cat.bbmodel"),
                minimalCatModel()
        );

        configManager =
                new ConfigManager(
                        () -> config(
                                """
                                models:
                                  directory: "models"
                                  default-model: "cats:black_cat"
                                """
                        ),
                        LOGGER
                );

        modelManager =
                new ModelManager(
                        plugin,
                        LOGGER,
                        runtime,
                        configManager,
                        store,
                        new CatCache(
                                store,
                                LOGGER
                        ),
                        battleState,
                        backend = new FakeRenderBackend()
                );

        modelManager.loadModels();

        world =
                FakeBukkit.proxy(
                        World.class,
                        Map.of(
                                "getName", "test",
                                "getUID", UUID.randomUUID()
                        ),
                        null
                );

        entity =
                runtime.newCat(
                        UUID.randomUUID(),
                        new Location(world, 0, 64, 0),
                        world,
                        new FakeBukkit.FakePDC()
                );

        /*
         * 4 参重载只建 proxy；手动登记到运行时实体表。
         */
        runtime.entities.put(
                entity.getUniqueId(),
                entity
        );

        logicalCat =
                Cat.createNew(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Mikan"
                );
    }

    @Test
    void loadsModelsFromDirectory() {

        assertEquals(
                1,
                modelManager.definitionCount()
        );

        assertNotNull(
                modelManager.resolveFor(
                        logicalCat
                )
        );
    }

    @Test
    void resolveFallsBackToDefaultModel() {

        logicalCat.setModelId(
                null
        );

        ModelDefinition definition =
                modelManager.resolveFor(
                        logicalCat
                );

        assertNotNull(definition);
        assertEquals(
                "cats:black_cat",
                definition.getId().toString()
        );
    }

    @Test
    void resolveUsesExplicitModelIdFirst() throws Exception {

        Files.writeString(
                tempDir.resolve("models/white_cat.bbmodel"),
                minimalCatModel()
        );

        modelManager.reload();

        logicalCat.setModelId(
                "cats:white_cat"
        );

        ModelDefinition definition =
                modelManager.resolveFor(
                        logicalCat
                );

        assertNotNull(definition);
        assertEquals(
                "cats:white_cat",
                definition.getId().toString()
        );
    }

    @Test
    void resolveUnknownIdReturnsNull() {

        logicalCat.setModelId(
                "cats:ghost"
        );

        assertNull(
                modelManager.resolveFor(
                        logicalCat
                )
        );
    }

    @Test
    void entityReadyCreatesRendererAndHidesEntity() {

        modelManager.onCatEntityReady(
                entity,
                logicalCat
        );

        assertEquals(
                1,
                modelManager.activeModelCount()
        );

        /*
         * 幂等：重复通知不重建。
         */
        modelManager.onCatEntityReady(
                entity,
                logicalCat
        );

        assertEquals(
                1,
                modelManager.activeModelCount()
        );

        /*
         * 原版视觉被隐藏（D1）。
         */
        assertEquals(
                1,
                modelManager.visualController()
                        .hiddenCount()
        );
    }

    @Test
    void noModelMeansNoRendererAndNoHide() {

        logicalCat.setModelId(
                "cats:ghost"
        );

        modelManager.onCatEntityReady(
                entity,
                logicalCat
        );

        assertEquals(
                0,
                modelManager.activeModelCount()
        );
        assertEquals(
                0,
                modelManager.visualController()
                        .hiddenCount()
        );
    }

    @Test
    void ownerRemovedDestroysRenderer() {

        UUID owner =
                UUID.randomUUID();

        store.createCat(
                owner
        );

        store.setCatEntityUUID(
                owner,
                entity.getUniqueId()
        );

        modelManager.onCatEntityReady(
                entity,
                logicalCat
        );

        assertEquals(
                1,
                modelManager.activeModelCount()
        );

        modelManager.onOwnerCatRemoved(
                owner,
                entity.getUniqueId()
        );

        assertEquals(
                0,
                modelManager.activeModelCount()
        );
        assertEquals(
                0,
                modelManager.visualController()
                        .hiddenCount()
        );
    }

    @Test
    void recoverySwitchesVisuals() {

        modelManager.onCatEntityReady(
                entity,
                logicalCat
        );

        /*
         * 进入恢复期：模型隐藏 + 原版 show。
         */
        battleState.markRecovering(
                entity.getUniqueId(),
                60_000L
        );

        modelManager.tick();

        assertEquals(
                0,
                modelManager.visualController()
                        .hiddenCount()
        );

        assertTrue(
                backend.objectFor("Body")
                        .isVisible() == false
        );

        /*
         * 恢复结束：模型可见 + 原版 hide。
         */
        battleState.markRecovering(
                entity.getUniqueId(),
                -1L
        );

        modelManager.tick();

        assertEquals(
                1,
                modelManager.visualController()
                        .hiddenCount()
        );
        assertTrue(
                backend.objectFor("Body")
                        .isVisible()
        );
    }

    @Test
    void invalidEntityIsCleanedUp() {

        modelManager.onCatEntityReady(
                entity,
                logicalCat
        );

        /*
         * 实体失效：tick 清理。
         */
        runtime.entities.remove(
                entity.getUniqueId()
        );

        modelManager.tick();

        assertEquals(
                0,
                modelManager.activeModelCount()
        );
    }

    @Test
    void shutdownDestroysEverything() {

        modelManager.onCatEntityReady(
                entity,
                logicalCat
        );

        modelManager.shutdown();

        assertEquals(
                0,
                modelManager.activeModelCount()
        );
        assertEquals(
                0,
                modelManager.visualController()
                        .hiddenCount()
        );
    }

    @Test
    void reloadKeepsOldDefinitionOnFailure() throws Exception {

        /*
         * 破坏模型文件后 reload：旧定义保留。
         */
        Files.writeString(
                tempDir.resolve("models/black_cat.bbmodel"),
                "{ broken json"
        );

        modelManager.reload();

        assertEquals(
                1,
                modelManager.definitionCount()
        );

        assertNotNull(
                modelManager.resolveFor(
                        logicalCat
                )
        );
    }

    @Test
    void missingDirectoryDisablesModels() throws Exception {

        Files.delete(
                tempDir.resolve("models/black_cat.bbmodel")
        );

        Files.delete(
                tempDir.resolve("models")
        );

        modelManager.reload();

        assertEquals(
                0,
                modelManager.definitionCount()
        );

        assertNull(
                modelManager.resolveFor(
                        logicalCat
                )
        );
    }

    private static FileConfiguration config(
            String yaml
    ) {

        return YamlConfiguration.loadConfiguration(
                new java.io.StringReader(
                        yaml
                )
        );
    }

    /*
     * ============================================================
     * Phase 8：模型设置 / 清除 / 列表
     * ============================================================
     */

    private void prepareOwner() {

        UUID owner =
                UUID.randomUUID();

        store.createCat(
                owner
        );

        store.setCatEntityUUID(
                owner,
                entity.getUniqueId()
        );

        ownerUuid = owner;
    }

    @Test
    void setModelUpdatesStoreAndFiresEvent() {

        prepareOwner();

        assertTrue(
                modelManager.setModel(
                        ownerUuid,
                        "cats:black_cat"
                )
        );

        assertEquals(
                "cats:black_cat",
                store.getCatModelId(
                        ownerUuid
                )
        );

        assertEquals(
                1,
                runtime.events.stream()
                        .filter(
                                e -> e instanceof
                                        mizukichou.nekonyume.event.CatModelChangedEvent
                        )
                        .count()
        );
    }

    @Test
    void clearModelResetsToEmpty() {

        prepareOwner();

        modelManager.setModel(
                ownerUuid,
                "cats:black_cat"
        );

        assertTrue(
                modelManager.setModel(
                        ownerUuid,
                        ""
                )
        );

        assertEquals(
                "",
                store.getCatModelId(
                        ownerUuid
                )
        );
    }

    @Test
    void setModelRejectsInvalidId() {

        prepareOwner();

        assertFalse(
                modelManager.setModel(
                        ownerUuid,
                        "BAD ID!"
                )
        );

        assertEquals(
                "",
                store.getCatModelId(
                        ownerUuid
                )
        );
    }

    @Test
    void setModelRejectsPlayerWithoutCat() {

        assertFalse(
                modelManager.setModel(
                        UUID.randomUUID(),
                        "cats:black_cat"
                )
        );
    }

    /*
     * P69（chunk 假活跃闭环）：实体被移除（chunk 卸载/
     * 世界卸载/外部删除）→ 渲染器销毁 + 条目清理，
     * 实体回归时 addToWorld 重建（幂等入口已验证）。
     */
    @Test
    void entityRemovedFromWorldDestroysRenderer() {

        modelManager.onCatEntityReady(
                entity,
                logicalCat
        );

        assertEquals(
                1,
                modelManager.activeModelCount()
        );

        modelManager.onEntityRemovedFromWorld(
                entity
        );

        assertEquals(
                0,
                modelManager.activeModelCount()
        );

        assertTrue(
                backend.getCreated()
                        .stream()
                        .allMatch(
                                FakeRenderBackend.FakeRenderObject::isRemoved
                        )
        );
    }

    /*
     * P69：世界卸载 → 该世界全部活动模型清理。
     */
    @Test
    void worldUnloadClearsThatWorld() {

        modelManager.onCatEntityReady(
                entity,
                logicalCat
        );

        assertEquals(
                1,
                modelManager.activeModelCount()
        );

        modelManager.onWorldUnloaded(
                world.getUID()
        );

        assertEquals(
                0,
                modelManager.activeModelCount()
        );
    }

    @Test
    void listModelsSorted() {

        modelManager.loadModels();

        assertEquals(
                java.util.List.of(
                        "cats:black_cat"
                ),
                modelManager.listModels()
        );
    }

    private static String minimalCatModel() {

        return """
                {
                  "meta": {
                    "format_version": "4.10",
                    "model_format": "java_block"
                  },
                  "name": "test cat",
                  "outliner": [
                    {
                      "name": "Root",
                      "pivot": [0, 0, 0],
                      "rotation": [0, 0, 0],
                      "origin": [0, 0, 0],
                      "children": [
                        {
                          "name": "Body",
                          "pivot": [0, 8, 0],
                          "rotation": [0, 0, 0],
                          "origin": [0, 0, 0],
                          "children": [
                            {
                              "name": "body_cube",
                              "type": "cube",
                              "from": [-4, 0, -8],
                              "to": [4, 8, 8],
                              "faces": {
                                "north": {"uv": [0, 0],  "uv_size": [8, 16],  "texture": 0},
                                "east":  {"uv": [8, 0],  "uv_size": [16, 16], "texture": 0},
                                "south": {"uv": [24, 0], "uv_size": [8, 16],  "texture": 0},
                                "west":  {"uv": [32, 0], "uv_size": [16, 16], "texture": 0},
                                "up":    {"uv": [8, 8],  "uv_size": [8, 16],  "texture": 0},
                                "down":  {"uv": [24, 8], "uv_size": [8, 16],  "texture": 0}
                              },
                              "mirror": false
                            }
                          ]
                        }
                      ]
                    }
                  ],
                  "textures": [
                    {
                      "name": "skin",
                      "source": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAABAgME"
                    }
                  ]
                }
                """;
    }
    /*
     * 模型文件后补场景：先 set 了未加载的模型 id（或未配默认），
     * 定义表换发后应自动重建视觉，而非卡在原版视觉直到实体重载。
     */
    @Test
    void reloadRebuildsPendingVisuals() {

        AtomicReference<FileConfiguration> configRef =
                new AtomicReference<>(
                        config(
                                """
                                models:
                                  directory: "models"
                                  default-model: ""
                                """
                        )
                );

        ConfigManager managerConfig =
                new ConfigManager(
                        () -> configRef.get(),
                        LOGGER
                );

        FakeRenderBackend managerBackend =
                new FakeRenderBackend();

        CatCache managerCache =
                new CatCache(
                        store,
                        LOGGER
                );

        ModelManager manager =
                new ModelManager(
                        plugin,
                        LOGGER,
                        runtime,
                        managerConfig,
                        store,
                        managerCache,
                        battleState,
                        managerBackend
                );

        manager.loadModels();

        UUID owner =
                UUID.randomUUID();

        store.createCat(
                owner
        );

        store.setCatEntityUUID(
                owner,
                entity.getUniqueId()
        );

        Cat loaded =
                managerCache.loadCat(
                        owner
                );

        assertNotNull(loaded);

        manager.onCatEntityReady(
                entity,
                loaded
        );

        /*
         * 默认模型未配置：无渲染对象、原版视觉。
         */
        assertEquals(
                0,
                managerBackend.getCreated().size()
        );

        /*
         * 补上默认模型 + 重载：应自动重建视觉。
         */
        configRef.set(
                config(
                        """
                        models:
                          directory: "models"
                          default-model: "cats:black_cat"
                        """
                )
        );

        /*
         * ConfigManager 是快照缓存模式：必须先 reload 换发新快照，
         * 模型重载时 resolveFor 才能读到新 default-model。
         */
        managerConfig.reload();

        manager.reload();

        assertTrue(
                managerBackend.getCreated().size() > 0
        );

        assertEquals(
                1,
                manager.visualController().hiddenCount()
        );
    }


    /*
     * 0.9.0更新：交互代理生命周期 + 节流同步。
     * spawnInteraction 现为 seam 的一部分——Fake 后端
     * 记录调用，交互路径可完整验证（此前 instanceof
     * 真后端导致本路径零测试覆盖）。
     */
    @Test
    void interactionProxySpawnsFollowsAndIsThrottled() {

        modelManager.onCatEntityReady(
                entity,
                logicalCat
        );

        FakeRenderBackend fake =
                (FakeRenderBackend) backend;

        assertEquals(
                1,
                fake.getSpawnedInteractions()
                        .size()
        );

        assertEquals(
                entity.getUniqueId(),
                fake.getInteractionCatUuids()
                        .get(
                                0
                        )
        );

        /*
         * 位置未变：不传送（第二十一轮阈值检测）。
         */
        modelManager.tick();

        assertEquals(
                0,
                countCalls(
                        fake.getInteractionCalls(
                                0
                        ),
                        "teleport"
                )
        );

        /*
         * 猫移动到新位置（替换运行时实体以模拟移动，
         * Fake proxy 的 getLocation 是固定答案）：传送一次。
         */
        UUID entityUuid =
                entity.getUniqueId();

        Entity moved =
                FakeBukkit.proxy(
                        org.bukkit.entity.Cat.class,
                        Map.of(
                                "getUniqueId",
                                entityUuid,
                                "isValid",
                                true,
                                "getWorld",
                                world,
                                "getLocation",
                                new Location(
                                        world,
                                        0.6,
                                        64.2,
                                        0.3
                                ),
                                "getYaw",
                                0.0f
                        ),
                        null
                );

        runtime.entities.put(
                entityUuid,
                moved
        );

        modelManager.tick();

        assertEquals(
                1,
                countCalls(
                        fake.getInteractionCalls(
                                0
                        ),
                        "teleport"
                )
        );

        /*
         * 位置未变：不重复传送。
         */
        modelManager.tick();

        assertEquals(
                1,
                countCalls(
                        fake.getInteractionCalls(
                                0
                        ),
                        "teleport"
                )
        );

        /*
         * 猫移出世界：交互代理实体销毁。
         */
        modelManager.onEntityRemovedFromWorld(
                moved
        );

        assertEquals(
                1,
                countCalls(
                        fake.getInteractionCalls(
                                0
                        ),
                        "remove"
                )
        );

        assertEquals(
                0,
                modelManager.activeModelCount()
        );
    }

    private static long countCalls(
            List<String> calls,
            String method
    ) {

        return calls.stream()
                .filter(
                        method::equals
                )
                .count();
    }

    /*
     * 0.9.0更新：玩家退出后未完成的资源包请求必须
     * 同步清理——迟到的状态回调不能把已退出玩家重新写回
     * loaded 状态。
     */
    @Test
    void forgetResourcePackClearsPendingRequests() {

        UUID packPlayerUuid =
                UUID.randomUUID();

        org.bukkit.entity.Player packPlayer =
                FakeBukkit.proxy(
                        org.bukkit.entity.Player.class,
                        Map.of(
                                "getName",
                                "PackTester",
                                "getUniqueId",
                                packPlayerUuid
                        ),
                        null
                );

        /*
         * 独立 manager：url + auto-send 配置齐备才会发出请求。
         */
        ConfigManager packConfig =
                new ConfigManager(
                        () -> config(
                                """
                                models:
                                  directory: "models"
                                  default-model: "cats:black_cat"
                                  resource-pack:
                                    url: "https://example.com/pack.zip"
                                    auto-send: true
                                """
                        ),
                        LOGGER
                );

        ModelManager packManager =
                new ModelManager(
                        plugin,
                        LOGGER,
                        runtime,
                        packConfig,
                        store,
                        new CatCache(
                                store,
                                LOGGER
                        ),
                        battleState,
                        new FakeRenderBackend()
                );

        packManager.loadModels();

        packManager.sendResourcePack(
                packPlayer
        );

        assertEquals(
                1,
                packManager.pendingRequestCount()
        );

        packManager.forgetResourcePack(
                packPlayerUuid
        );

        assertEquals(
                0,
                packManager.pendingRequestCount()
        );

        assertNull(
                packManager.loadedPackHashOf(
                        packPlayerUuid
                )
        );
    }

}
