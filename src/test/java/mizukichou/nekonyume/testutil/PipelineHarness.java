package mizukichou.nekonyume.testutil;

import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityBinding;
import mizukichou.nekonyume.cat.CatEntityIndex;
import mizukichou.nekonyume.cat.CatEntityRestorer;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatProgressionService;
import mizukichou.nekonyume.cat.CatVariantService;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.skill.CatSkillManager;
import mizukichou.nekonyume.storage.MemoryCatStore;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 0.8.4：恢复/召唤管线的测试组合根。
 *
 * <p>
 * 用真实的生产组件（MemoryCatStore、CatCache、CatEntityRestorer、
 * CatEntityService、CatEntityBinding、CatProgressionService、
 * CatSkillManager、Lang、ConfigManager），仅把 Bukkit 触点
 * 换成 {@link FakeCatEntityRuntime}——历史致命竞态第一次
 * 可以在无服务端环境里被端到端复现与守护。
 * </p>
 */
public final class PipelineHarness {

    public final Logger logger;
    public final MemoryCatStore store;
    public final CatCache cache;
    public final ConfigManager configManager;
    public final Lang lang;
    public final CatBattleState battleState;
    public final CatEntityIndex entityIndex;
    public final FakeCatEntityRuntime runtime;
    public final CatVariantService variantService;
    public final CatSkillManager skillManager;
    public final CatProgressionService progression;
    public final CatEntityBinding binding;
    public final CatEntityRestorer restorer;
    public final CatEntityService service;

    /*
     * 0.8.4 R17（社区上报）：成就奖励幂等协议测试用。
     */
    public final mizukichou.nekonyume.achievement.AchievementService achievementService;
    public final mizukichou.nekonyume.cat.CatFoodManager foodManager;

    public final NamespacedKey catKey;
    public final NamespacedKey ownerKey;

    public final UUID playerUuid = UUID.randomUUID();
    public final World world;
    public final Location homeLocation;
    public final Player player;
    public final List<String> playerCalls = new ArrayList<>();
    public final List<String> playerMessages = new ArrayList<>();

    /*
     * 玩家主手物品（喂食测试用；feedCat 会直接修改其数量）。
     */
    public org.bukkit.inventory.ItemStack mainHand;

    private PipelineHarness(String configYaml) {

        logger = Logger.getLogger("PipelineHarness");
        logger.setLevel(Level.WARNING);

        store = new MemoryCatStore();
        cache = new CatCache(store, logger);

        configManager = new ConfigManager(
                () -> loadConfig(configYaml),
                logger
        );

        lang = new Lang(
                PipelineHarness.class.getClassLoader(),
                java.nio.file.Path.of("build-test", "lang-data"),
                configManager,
                logger
        );

        battleState = new CatBattleState();
        entityIndex = new CatEntityIndex();
        runtime = new FakeCatEntityRuntime();

        catKey = new NamespacedKey("nekonyume", "cat-key");
        ownerKey = new NamespacedKey("nekonyume", "owner-key");

        world = fakeWorld("pipeline-world");
        homeLocation = new Location(world, 100, 64, 100);

        player = fakePlayer(playerUuid, "PipePlayer", world, homeLocation);

        runtime.worlds.add(world);
        runtime.players.put(playerUuid, player);

        runtime.tabbyType = FakeBukkit.proxy(
                Cat.Type.class,
                Map.of(
                        "toString", "minecraft:tabby",
                        "key", NamespacedKey.minecraft("tabby")
                ),
                null
        );

        variantService = new CatVariantService(store, runtime);

        skillManager = new CatSkillManager(
                logger,
                store,
                cache,
                configManager,
                battleState,
                lang,
                runtime
        );

        progression = new CatProgressionService(
                store,
                cache,
                configManager,
                skillManager,
                lang,
                runtime
        );

        binding = new CatEntityBinding(
                store,
                cache,
                progression,
                variantService,
                battleState,
                lang,
                catKey,
                ownerKey,
                entityIndex,
                runtime
        );

        restorer = new CatEntityRestorer(
                runtime,
                logger,
                store,
                cache,
                variantService,
                lang,
                binding,
                entityIndex
        );

        service = new CatEntityService(
                logger,
                store,
                cache,
                lang,
                binding,
                restorer
        );

        achievementService =
                new mizukichou.nekonyume.achievement.AchievementService(
                        store,
                        cache,
                        progression,
                        configManager,
                        lang,
                        runtime,
                        logger
                );

        foodManager = new mizukichou.nekonyume.cat.CatFoodManager(
                runtime,
                "nekonyume",
                store,
                cache,
                configManager,
                progression,
                service,
                lang
        );
    }

    public static PipelineHarness create() {
        return new PipelineHarness(null);
    }

    /**
     * 带自定义配置创建（食物表等测试需要）。
     */
    public static PipelineHarness createWithConfig(String configYaml) {
        return new PipelineHarness(configYaml);
    }

    private static YamlConfiguration loadConfig(String configYaml) {
        YamlConfiguration config = new YamlConfiguration();
        if (configYaml != null && !configYaml.isBlank()) {
            try {
                config.loadFromString(configYaml);
            } catch (org.bukkit.configuration.InvalidConfigurationException e) {
                throw new IllegalArgumentException("bad test config", e);
            }
        }
        return config;
    }

    /*
     * ============ 便捷构造 ============
     */

    /**
     * 建档一只逻辑猫（不生成实体）。
     */
    public mizukichou.nekonyume.cat.Cat createLogicalCat() {
        store.createCat(playerUuid);
        return cache.loadCat(player);
    }

    /**
     * 生成一只带主人 PDC 的假猫实体（未登记到 store 的 entity-uuid）。
     */
    public Cat fakeCatEntity(UUID entityUuid, Location location) {
        FakeBukkit.FakePDC pdc = new FakeBukkit.FakePDC();
        pdc.set(catKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        pdc.set(ownerKey, org.bukkit.persistence.PersistentDataType.STRING, playerUuid.toString());

        Cat cat = runtime.newCat(entityUuid, location, world, pdc);
        runtime.entities.put(entityUuid, cat);
        runtime.worldCats.computeIfAbsent(world, k -> new ArrayList<>()).add(cat);
        return cat;
    }

    public World fakeWorld(String name) {
        UUID uid = UUID.randomUUID();
        Map<String, Object> answers = new HashMap<>();
        answers.put("getName", name);
        answers.put("getUID", uid);
        return FakeBukkit.proxy(World.class, answers, null);
    }

    public Player fakePlayer(UUID uuid, String name, World w, Location location) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getUniqueId", uuid);
        answers.put("getName", name);
        answers.put("getWorld", w);
        answers.put("getLocation", location);
        answers.put("isOnline", true);
        answers.put("getGameMode", org.bukkit.GameMode.SURVIVAL);
        answers.put("sendMessage", (FakeBukkit.Answer) args -> {
            playerMessages.add(String.valueOf(args[0]));
            return null;
        });
        Map<String, Object> invAnswers = new HashMap<>();
        invAnswers.put("getItemInMainHand", (FakeBukkit.Answer) args -> mainHand);
        org.bukkit.inventory.PlayerInventory inventory = FakeBukkit.proxy(
                org.bukkit.inventory.PlayerInventory.class,
                invAnswers,
                null
        );
        answers.put("getInventory", inventory);
        return FakeBukkit.proxy(Player.class, answers, playerCalls);
    }

    public Chunk fakeChunk(World w, int x, int z, List<Entity> entities) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getWorld", w);
        answers.put("getX", x);
        answers.put("getZ", z);
        answers.put("isLoaded", true);
        answers.put("getEntities", entities);
        return FakeBukkit.proxy(Chunk.class, answers, null);
    }
}
