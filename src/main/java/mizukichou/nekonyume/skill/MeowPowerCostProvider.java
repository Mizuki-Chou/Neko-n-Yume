package mizukichou.nekonyume.skill;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.entity.Player;

/**
 * 默认刷新消耗：喵力。
 *
 * <p>
 * 注意：只扣除喵力资源，
 * 喵阶只升不降（历史最高阶保留）。
 * </p>
 */
public class MeowPowerCostProvider implements SkillRefreshCostProvider {

    private final CatCache cache;
    private final CatStore store;

    public MeowPowerCostProvider(
            CatCache cache,
            CatStore store
    ) {

        this.cache = cache;
        this.store = store;
    }

    @Override
    public String getDisplayName() {
        return "喵力";
    }

    @Override
    public boolean canAfford(
            Player player,
            int cost
    ) {

        if (cost < 0) {
            return false;
        }

        Cat cat =
                cache.loadCat(player);

        return cat != null &&
                cat.getMeowPower() >= cost;
    }

    @Override
    public boolean charge(
            Player player,
            int cost
    ) {

        if (cost < 0) {
            return false;
        }

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return false;
        }

        if (cat.getMeowPower() < cost) {
            return false;
        }

        cat.setMeowPower(
                cat.getMeowPower() - cost
        );

        store.setCatMeowPower(
                player.getUniqueId(),
                cat.getMeowPower()
        );

        return true;
    }
}

