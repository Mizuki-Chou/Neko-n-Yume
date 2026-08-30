package mizukichou.nekonyume.cat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 0.8.3：实体索引（EntityUUID ↔ 玩家 UUID 双向映射）。
 *
 * <p>
 * 替代恢复/清理路径上的全世界实体扫描：
 * 登录恢复（findLoadedCatForPlayer）、重复实体清理
 * （cleanupDuplicateCats）与删除清理（cleanupAllOwnedEntities）
 * 首先走 O(1) 的索引查找，找不到再回退全图扫描兜底。
 * </p>
 *
 * <p>
 * 正确性契约：本索引是<b>尽力而为的加速器</b>，
 * 不是唯一事实来源——
 * 所有使用方都必须保留全图扫描兜底；
 * 索引中的实体在返回前仍要经过
 * 有效性（getEntity）+ PDC 归属双重校验。
 * </p>
 *
 * <p>
 * 线程模型：仅主线程访问（与 Bukkit 事件/存储写入一致）。
 * </p>
 */
public final class CatEntityIndex {

    /*
     * 实体 UUID → 主人 UUID。
     */
    private final Map<UUID, UUID> entityToOwner =
            new HashMap<>();

    /*
     * 主人 UUID → 归属实体 UUID 集合。
     */
    private final Map<UUID, Set<UUID>> ownerToEntities =
            new HashMap<>();

    /**
     * 登记（或刷新）实体与主人的映射。
     * 重复登记同值幂等。
     */
    public void put(
            UUID entityUuid,
            UUID ownerUuid
    ) {

        if (entityUuid == null || ownerUuid == null) {
            return;
        }

        UUID previousOwner =
                entityToOwner.put(
                        entityUuid,
                        ownerUuid
                );

        if (ownerUuid.equals(previousOwner)) {
            return;
        }

        if (previousOwner != null) {

            Set<UUID> previousSet =
                    ownerToEntities.get(previousOwner);

            if (previousSet != null) {

                previousSet.remove(entityUuid);

                if (previousSet.isEmpty()) {

                    ownerToEntities.remove(previousOwner);
                }
            }
        }

        ownerToEntities
                .computeIfAbsent(
                        ownerUuid,
                        k -> new HashSet<>()
                )
                .add(entityUuid);
    }

    /**
     * 移除单个实体的索引条目。
     */
    public void removeEntity(UUID entityUuid) {

        if (entityUuid == null) {
            return;
        }

        UUID ownerUuid =
                entityToOwner.remove(entityUuid);

        if (ownerUuid == null) {
            return;
        }

        Set<UUID> set =
                ownerToEntities.get(ownerUuid);

        if (set != null) {

            set.remove(entityUuid);

            if (set.isEmpty()) {

                ownerToEntities.remove(ownerUuid);
            }
        }
    }

    /**
     * 移除某个主人的全部索引条目。
     */
    public void removeOwner(UUID ownerUuid) {

        if (ownerUuid == null) {
            return;
        }

        Set<UUID> entities =
                ownerToEntities.remove(ownerUuid);

        if (entities != null) {

            for (UUID entityUuid : entities) {

                /*
                 * 只移除仍指向该主人的条目，
                 * 已被其他主人抢占的条目不受影响。
                 */
                entityToOwner.remove(
                        entityUuid,
                        ownerUuid
                );
            }
        }
    }

    /**
     * 实体的主人 UUID（未知返回 null）。
     */
    public UUID getOwner(UUID entityUuid) {

        return entityUuid == null
                ? null
                : entityToOwner.get(entityUuid);
    }

    /**
     * 主人名下全部实体 UUID（快照，无则空集合）。
     */
    public Set<UUID> entitiesOf(UUID ownerUuid) {

        Set<UUID> set =
                ownerUuid == null
                        ? null
                        : ownerToEntities.get(ownerUuid);

        return set == null
                ? Set.of()
                : new HashSet<>(set);
    }

    /**
     * 索引中的实体条目总数。
     */
    public int size() {

        return entityToOwner.size();
    }

    /**
     * 清空全部索引（重载/测试）。
     */
    public void clear() {

        entityToOwner.clear();
        ownerToEntities.clear();
    }
}
