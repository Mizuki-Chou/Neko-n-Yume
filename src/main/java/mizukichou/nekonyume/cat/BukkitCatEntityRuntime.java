package mizukichou.nekonyume.cat;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

/**
 * 0.8.4：生产环境的 {@link CatEntityRuntime}——原样委托 Bukkit。
 */
public final class BukkitCatEntityRuntime implements CatEntityRuntime {

    private final JavaPlugin plugin;

    public BukkitCatEntityRuntime(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Entity getEntity(UUID entityUuid) {
        return Bukkit.getEntity(entityUuid);
    }

    @Override
    public Collection<Cat> catsIn(World world) {
        return world.getEntitiesByClass(Cat.class);
    }

    @Override
    public Cat spawnCat(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        return (Cat) world.spawnEntity(
                location,
                org.bukkit.entity.EntityType.CAT
        );
    }

    @Override
    public void runTask(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public CompletableFuture<Chunk> chunkAtAsync(World world, int x, int z) {
        return world.getChunkAtAsync(x, z);
    }

    @Override
    public Collection<World> worlds() {
        return Bukkit.getWorlds();
    }

    @Override
    public World worldByName(String name) {
        return Bukkit.getWorld(name);
    }

    @Override
    public World worldByUuid(UUID uid) {
        return Bukkit.getWorld(uid);
    }

    @Override
    public boolean isPluginEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public Cat.Type resolveCatType(String variantString) {
        if (variantString == null || variantString.isBlank()) {
            return null;
        }

        NamespacedKey key = NamespacedKey.fromString(variantString);
        if (key == null) {
            return null;
        }

        return RegistryAccess
                .registryAccess()
                .getRegistry(RegistryKey.CAT_VARIANT)
                .get(key);
    }

    @Override
    public Cat.Type randomCatType() {
        java.util.List<Cat.Type> types =
                RegistryAccess
                        .registryAccess()
                        .getRegistry(RegistryKey.CAT_VARIANT)
                        .stream()
                        .toList();

        if (types.isEmpty()) {
            throw new IllegalStateException(
                    "No cat variants are registered!"
            );
        }

        return types.get(
                (int) (Math.random() * types.size())
        );
    }

    @Override
    public NamespacedKey typeKey(Cat.Type variant) {
        if (variant == null) {
            return null;
        }

        return RegistryAccess
                .registryAccess()
                .getRegistry(RegistryKey.CAT_VARIANT)
                .getKey(variant);
    }

    @Override
    public org.bukkit.entity.Player playerByUuid(UUID playerUuid) {
        return Bukkit.getPlayer(playerUuid);
    }

    @Override
    public org.bukkit.attribute.AttributeInstance maxHealthAttribute(
            org.bukkit.entity.LivingEntity entity
    ) {

        return entity.getAttribute(
                org.bukkit.attribute.Attribute.MAX_HEALTH
        );
    }

    @Override
    public void applyPotion(
            org.bukkit.entity.LivingEntity entity,
            String key,
            int durationTicks,
            int amplifier
    ) {

        org.bukkit.potion.PotionEffectType type = switch (key) {
            case "speed" -> org.bukkit.potion.PotionEffectType.SPEED;
            case "strength" -> org.bukkit.potion.PotionEffectType.STRENGTH;
            case "resistance" -> org.bukkit.potion.PotionEffectType.RESISTANCE;
            case "slowness" -> org.bukkit.potion.PotionEffectType.SLOWNESS;
            case "regeneration" -> org.bukkit.potion.PotionEffectType.REGENERATION;
            case "invisibility" -> org.bukkit.potion.PotionEffectType.INVISIBILITY;
            default -> null;
        };

        if (type == null) {
            return;
        }

        entity.addPotionEffect(
                new org.bukkit.potion.PotionEffect(
                        type,
                        durationTicks,
                        amplifier
                )
        );
    }

    @Override
    public void playSound(
            Location location,
            String key,
            float volume,
            float pitch
    ) {

        World world = location.getWorld();
        if (world == null) {
            return;
        }

        org.bukkit.Sound sound = switch (key) {
            case "purr" -> org.bukkit.Sound.ENTITY_CAT_PURR;
            case "levelup" -> org.bukkit.Sound.ENTITY_PLAYER_LEVELUP;
            case "exp-orb" -> org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "eat" -> org.bukkit.Sound.ENTITY_GENERIC_EAT;
            case "toast" -> org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE;
            case "cat-ambient" -> org.bukkit.Sound.ENTITY_CAT_AMBIENT;
            case "item-pickup" -> org.bukkit.Sound.ENTITY_ITEM_PICKUP;
            case "cat-eat" -> org.bukkit.Sound.ENTITY_CAT_EAT;
            default -> null;
        };

        if (sound == null) {
            return;
        }

        world.playSound(
                location,
                sound,
                volume,
                pitch
        );
    }

    @Override
    public void callEvent(org.bukkit.event.Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }
}
