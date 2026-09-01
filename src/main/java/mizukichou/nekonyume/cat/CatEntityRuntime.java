package mizukichou.nekonyume.cat;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;

/**
 * 0.8.4：猫实体运行时访问 seam。
 *
 * <p>
 * 把恢复/召唤管线（CatEntityRestorer、CatEntityService、CatEntityBinding）
 * 对 Bukkit 静态 API 与实体世界的全部触点收口到这里：
 * 生产实现 {@link BukkitCatEntityRuntime} 原样委托 Bukkit；
 * 测试实现用轻量 fake，让整条"实体恢复五级流水线"第一次可以被自动化测试覆盖。
 * </p>
 *
 * <p>
 * 契约：所有方法都可能在恢复管线里被反复调用，
 * 实现必须是无状态的只读访问或副作用明确可观测的操作。
 * </p>
 */
public interface CatEntityRuntime {

    /**
     * 按实体 UUID 查找实体（等价 Bukkit.getEntity）。
     */
    Entity getEntity(UUID entityUuid);

    /**
     * 世界内已加载的全部猫实体（等价 World.getEntitiesByClass(Cat.class)）。
     */
    Collection<Cat> catsIn(World world);

    /**
     * 在指定位置生成一只猫实体（等价 World.spawnEntity(location, Cat.class)）。
     */
    Cat spawnCat(Location location);

    /**
     * 主线程执行任务（等价 Bukkit.getScheduler().runTask(plugin, task)）。
     * 测试实现通常直接同步执行，让流水线确定性跑完。
     */
    void runTask(Runnable task);

    /**
     * 异步加载区块（等价 World.getChunkAtAsync(x, z)）。
     * 测试实现返回已完成或可手动完成的 future，精确控制竞态窗口。
     */
    CompletableFuture<Chunk> chunkAtAsync(World world, int x, int z);

    /**
     * 全部已加载世界（等价 Bukkit.getWorlds()）。
     */
    Collection<World> worlds();

    /**
     * 按名字查找世界（等价 Bukkit.getWorld(name)）。
     */
    World worldByName(String name);

    /**
     * 按 UID 查找世界（等价 Bukkit.getWorld(uid)）。
     */
    World worldByUuid(UUID uid);

    /**
     * 插件是否处于启用状态（等价 JavaPlugin.isEnabled()）。
     */
    boolean isPluginEnabled();

    /**
     * 花色字符串 → 猫花色类型（生产实现走 RegistryAccess）。
     * 无法解析时返回 null。
     */
    Cat.Type resolveCatType(String variantString);

    /**
     * 随机花色（生产实现走 RegistryAccess）。
     */
    Cat.Type randomCatType();

    /**
     * 花色类型 → 命名空间键（等价 Registry.getKey(type)）。
     * 生产实现走 RegistryAccess；无法反查时返回 null。
     */
    org.bukkit.NamespacedKey typeKey(Cat.Type variant);

    /**
     * 按 UUID 查找在线玩家（等价 Bukkit.getPlayer(uuid)）。
     */
    org.bukkit.entity.Player playerByUuid(UUID playerUuid);

    /**
     * 实体最大生命属性实例（等价 entity.getAttribute(Attribute.MAX_HEALTH)）。
     * <p>
     * Paper 26.2 的 Attribute 枚举初始化依赖真实服务端 RegistryAccess，
     * 测试环境无法触碰——因此属性访问必须经本 seam。
     * </p>
     */
    org.bukkit.attribute.AttributeInstance maxHealthAttribute(
            org.bukkit.entity.LivingEntity entity
    );

    /**
     * 施加药水效果（等价 entity.addPotionEffect(new PotionEffect(type, t, a))）。
     * <p>
     * Paper 26.2 的 PotionEffectType 常量同样依赖真实服务端
     * RegistryAccess，效果施加必须经本 seam；key 到类型的映射
     * 只存在于生产实现中。
     * </p>
     *
     * @param key 效果键：speed / strength / resistance / slowness /
     *            regeneration / invisibility
     */
    void applyPotion(
            org.bukkit.entity.LivingEntity entity,
            String key,
            int durationTicks,
            int amplifier
    );

    /**
     * 播放声音（等价 location.getWorld().playSound(location, sound, v, p)）。
     * <p>
     * Sound 常量同样依赖真实服务端注册表；key 到声音的映射
     * 只存在于生产实现中。
     * </p>
     *
     * @param key 声音键：purr / levelup / exp-orb / eat / toast /
     *            cat-ambient / item-pickup / cat-eat
     */
    void playSound(
            Location location,
            String key,
            float volume,
            float pitch
    );

    /**
     * 触发事件（等价 Bukkit.getPluginManager().callEvent(event)）。
     * 测试实现仅记录事件对象。
     */
    void callEvent(org.bukkit.event.Event event);
}
