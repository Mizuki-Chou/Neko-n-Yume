package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.NamespacedKey;

import java.util.Random;
import java.util.UUID;

/**
 * 猫咪花色服务。
 *
 * <p>
 * 从 CatEntityService 抽出，阻止其继续膨胀：
 * 花色解析 / 随机花色 / 持久化 / 恢复规则。
 * 0.8.4 起注册表访问经 {@link CatEntityRuntime} 收口，可在无服务端环境测试。
 * </p>
 */
public class CatVariantService {

    private final CatStore store;

    private final CatEntityRuntime runtime;

    private final Random random =
            new Random();

    public CatVariantService(
            CatStore store,
            CatEntityRuntime runtime
    ) {

        this.store = store;
        this.runtime = runtime;
    }

    public org.bukkit.entity.Cat.Type getRandomType() {

        return runtime.randomCatType();
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
                runtime.typeKey(
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

        return runtime.resolveCatType(
                variantString
        );
    }
}
