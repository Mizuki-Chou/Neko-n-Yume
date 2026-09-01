package mizukichou.nekonyume.testutil;

import mizukichou.nekonyume.cat.CatEntityRuntime;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 0.8.4：{@link CatEntityRuntime} 的测试实现。
 *
 * <p>
 * 关键能力：
 * 1. 实体/世界/玩家由测试显式登记；
 * 2. runTask 同步执行（流水线确定性跑完）；
 * 3. 区块 future 可切换为“手动完成”——测试在 future 完成前
 *    执行删除/退出操作，精确复现历史上的竞态窗口；
 * 4. 花色解析走固定映射，无需真实服务端注册表。
 * </p>
 */
public final class FakeCatEntityRuntime implements CatEntityRuntime {

    public final Map<UUID, Entity> entities =
            new HashMap<>();

    public final List<World> worlds =
            new ArrayList<>();

    public final Map<World, List<Cat>> worldCats =
            new HashMap<>();

    public final Map<UUID, Player> players =
            new HashMap<>();

    public final List<Cat> spawned =
            new ArrayList<>();

    /*
     * 每只 fake 猫的调用记录（remove/teleport 等断言用）。
     */
    public final Map<UUID, List<String>> catCalls =
            new HashMap<>();

    public final List<Runnable> tasks =
            new ArrayList<>();

    public boolean enabled = true;

    /*
     * manualChunks = true 时，每次 chunkAtAsync 都创建一个
     * 未完成 future 并记录在 pendingChunks，由测试择机完成。
     */
    public boolean manualChunks = false;

    public final List<CompletableFuture<Chunk>> pendingChunks =
            new ArrayList<>();

    public final Map<String, Chunk> chunks =
            new HashMap<>();

    public Cat.Type tabbyType;

    public NamespacedKey typeKey =
            NamespacedKey.minecraft("tabby");

    public Cat newCat(UUID entityUuid, Location location, World world, FakeBukkit.FakePDC pdc) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getUniqueId", entityUuid);
        answers.put("getLocation", location);
        answers.put("getWorld", world);
        answers.put("isDead", false);
        answers.put("isValid", true);
        answers.put("getPersistentDataContainer", pdc);
        answers.put("getCatType", tabbyType);
        answers.put("getAttribute", attribute());
        answers.put("teleport", true);
        answers.put("getPitch", 0f);
        answers.put("getHealth", 20.0);
        List<String> calls = new ArrayList<>();
        catCalls.put(entityUuid, calls);
        return FakeBukkit.proxy(
                Cat.class,
                answers,
                calls
        );
    }

    public final List<String> potions =
            new ArrayList<>();

    public final List<String> sounds =
            new ArrayList<>();

    public final List<org.bukkit.event.Event> events =
            new ArrayList<>();

    /*
     * 最近一次 setBaseValue 写入的最大生命值（升级同步断言用）。
     */
    public double lastMaxHealthBase = -1.0;

    private Object attribute() {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getBaseValue", 10.0);
        answers.put("getValue", 10.0);
        answers.put("setBaseValue", (FakeBukkit.Answer) args -> {
            lastMaxHealthBase = (double) args[0];
            return null;
        });
        return FakeBukkit.proxy(
                org.bukkit.attribute.AttributeInstance.class,
                answers,
                null
        );
    }

    @Override
    public Entity getEntity(UUID entityUuid) {
        return entities.get(entityUuid);
    }

    @Override
    public Collection<Cat> catsIn(World world) {
        List<Cat> list = worldCats.get(world);
        return list == null
                ? List.of()
                : new ArrayList<>(list);
    }

    @Override
    public Cat spawnCat(Location location) {
        UUID uuid = UUID.randomUUID();
        World world = location.getWorld();
        Cat cat = newCat(uuid, location, world, new FakeBukkit.FakePDC());
        entities.put(uuid, cat);
        worldCats.computeIfAbsent(world, k -> new ArrayList<>()).add(cat);
        spawned.add(cat);
        return cat;
    }

    @Override
    public void runTask(Runnable task) {
        tasks.add(task);
        task.run();
    }

    @Override
    public CompletableFuture<Chunk> chunkAtAsync(World world, int x, int z) {
        if (manualChunks) {
            CompletableFuture<Chunk> future = new CompletableFuture<>();
            pendingChunks.add(future);
            return future;
        }

        Chunk chunk = chunks.get(keyOf(world, x, z));
        if (chunk == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "no fake chunk for " + x + "/" + z
                    )
            );
        }

        return CompletableFuture.completedFuture(chunk);
    }

    private String keyOf(World world, int x, int z) {
        return world.getName() + "/" + x + "/" + z;
    }

    public void registerChunk(World world, int x, int z, Chunk chunk) {
        chunks.put(keyOf(world, x, z), chunk);
    }

    @Override
    public Collection<World> worlds() {
        return new ArrayList<>(worlds);
    }

    @Override
    public World worldByName(String name) {
        for (World world : worlds) {
            if (world.getName().equals(name)) {
                return world;
            }
        }
        return null;
    }

    @Override
    public World worldByUuid(UUID uid) {
        for (World world : worlds) {
            if (world.getUID().equals(uid)) {
                return world;
            }
        }
        return null;
    }

    @Override
    public boolean isPluginEnabled() {
        return enabled;
    }

    @Override
    public Cat.Type resolveCatType(String variantString) {
        return tabbyType;
    }

    @Override
    public Cat.Type randomCatType() {
        return tabbyType;
    }

    @Override
    public NamespacedKey typeKey(Cat.Type variant) {
        return typeKey;
    }

    @Override
    public Player playerByUuid(UUID playerUuid) {
        return players.get(playerUuid);
    }

    @Override
    public org.bukkit.attribute.AttributeInstance maxHealthAttribute(
            org.bukkit.entity.LivingEntity entity
    ) {

        return (org.bukkit.attribute.AttributeInstance) attribute();
    }

    @Override
    public void applyPotion(
            org.bukkit.entity.LivingEntity entity,
            String key,
            int durationTicks,
            int amplifier
    ) {

        potions.add(key + ":" + durationTicks + ":" + amplifier);
    }

    @Override
    public void playSound(
            Location location,
            String key,
            float volume,
            float pitch
    ) {

        sounds.add(key);
    }

    @Override
    public void callEvent(org.bukkit.event.Event event) {
        events.add(event);
    }
}
