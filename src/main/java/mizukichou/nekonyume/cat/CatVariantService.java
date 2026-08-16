package mizukichou.nekonyume.cat;

import io.papermc.paper.registry.RegistryKey;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.Random;
import java.util.UUID;

/**
 * 猫咪花色服务。
 *
 * <p>
 * 从 CatEntityService 抽出，阻止其继续膨胀：
 * 花色注册表 / 随机花色 / 持久化 / 恢复规则。
 * </p>
 */
public class CatVariantService {

    private final CatStore store;

    private final Random random =
            new Random();

    public CatVariantService(
            CatStore store
    ) {

        this.store = store;
    }

    public Registry<org.bukkit.entity.Cat.Type>
    getRegistry() {

        return io.papermc.paper.registry.RegistryAccess
                .registryAccess()
                .getRegistry(
                        RegistryKey.CAT_VARIANT
                );
    }

    public org.bukkit.entity.Cat.Type getRandomType() {

        java.util.List<org.bukkit.entity.Cat.Type> types =
                getRegistry()
                        .stream()
                        .toList();

        if (types.isEmpty()) {

            throw new IllegalStateException(
                    "No cat variants are registered!"
            );
        }

        return types.get(
                random.nextInt(types.size())
        );
    }

    public String saveVariant(
            UUID playerUUID,
            org.bukkit.entity.Cat.Type variant
    ) {

        if (playerUUID == null ||
                variant == null) {

            return null;
        }

        NamespacedKey key =
                getRegistry()
                        .getKey(
                                variant
                        );

        if (key == null) {
            return null;
        }

        String variantString =
                key.toString();

        store.setCatVariant(
                playerUUID,
                variantString
        );

        return variantString;
    }

    /*
     * 恢复 / 建立永久花色。
     *
     * 规则：
     * 1. Cat 已有 variant → 使用 Cat 的 variant
     * 2. 存档已有 variant → 使用存档 variant
     * 3. 当前 Bukkit 实体存在 → 使用当前实体花色并永久保存
     * 4. 完全没有历史信息 → 随机一次，然后永久保存
     */
    public void restoreVariant(
            UUID playerUUID,
            org.bukkit.entity.Cat entity,
            Cat logicalCat
    ) {

        if (playerUUID == null ||
                entity == null ||
                logicalCat == null) {

            return;
        }

        /*
         * 1. 逻辑 Cat 已经有 variant。
         */
        String logicalVariant =
                logicalCat.getVariant();

        if (logicalVariant != null &&
                !logicalVariant.isBlank()) {

            org.bukkit.entity.Cat.Type variant =
                    getType(
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
         * 2. 从 CatStore 恢复。
         */
        String savedVariant =
                store.getCatVariant(
                        playerUUID
                );

        if (savedVariant != null &&
                !savedVariant.isBlank()) {

            org.bukkit.entity.Cat.Type variant =
                    getType(
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
        }

        /*
         * 3. 使用当前 Bukkit 实体已经拥有的花色。
         * 这个分支对老存档非常重要。
         */
        org.bukkit.entity.Cat.Type currentType =
                entity.getCatType();

        if (currentType == null) {

            /*
             * 4. 完全没有可用历史信息。
             * 只能随机一次。
             */
            currentType =
                    getRandomType();

            entity.setCatType(
                    currentType
            );
        }

        String variantString =
                saveVariant(
                        playerUUID,
                        currentType
                );

        if (variantString != null) {

            logicalCat.setVariant(
                    variantString
            );
        }
    }

    public org.bukkit.entity.Cat.Type getType(
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

        return getRegistry()
                .get(key);
    }
}