package mizukichou.nekonyume.ranking;

import mizukichou.nekonyume.storage.CatStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * 排行服务：从存储组装全服猫咪排行。
 *
 * <p>
 * 0.8.5：/nya ranking 的数据层。
 * 本类只依赖 CatStore（MemoryCatStore 可直接单测），
 * 主人显示名经 nameProvider 注入（生产为 Bukkit 离线玩家名，
 * 测试为固定映射）。
 * </p>
 */
public final class CatRankingService {

    private final CatStore store;

    public CatRankingService(
            CatStore store
    ) {

        this.store = store;
    }

    /**
     * 组装指定排序模式的排行快照。
     *
     * <p>
     * 数据源为 store.getCatPlayers()——存储层全部已存猫
     * （含离线玩家的猫），与在线状态无关。
     * </p>
     *
     * @param mode          排序模式
     * @param nameProvider  主人 UUID → 显示名（null 时回退 "?"）
     */
    public CatRanking buildRanking(
            SortMode mode,
            Function<UUID, String> nameProvider
    ) {

        List<CatRankEntry> entries =
                new ArrayList<>();

        for (UUID ownerUuid :
                store.getCatPlayers()) {

            String ownerName =
                    nameProvider.apply(ownerUuid);

            entries.add(
                    new CatRankEntry(
                            ownerUuid,
                            ownerName == null
                                    ? "?" : ownerName,
                            store.getCatName(ownerUuid),
                            store.getCatMeowRank(ownerUuid),
                            store.getCatMeowPower(ownerUuid),
                            store.getCatLevel(ownerUuid),
                            store.getCatExperience(ownerUuid)
                    )
            );
        }

        return new CatRanking(
                mode,
                entries
        );
    }
}
