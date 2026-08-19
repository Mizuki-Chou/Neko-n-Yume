package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.storage.CatStore;
import mizukichou.nekonyume.storage.MemoryCatStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CatCache 二级索引测试（纯内存，无 Bukkit 依赖）。
 *
 * <p>
 * 覆盖：
 * owner→catId 索引、entityUuid→catId 反向索引（P1-5）、
 * 索引自愈、移除清理，以及
 * "未知实体查询绝不抛 NPE"的回归防护
 * （ConcurrentHashMap.remove(key, null) 会抛 NPE）。
 * </p>
 */
class CatCacheTest {

    private CatStore store;
    private CatCache cache;

    @BeforeEach
    void setUp() {

        store = new MemoryCatStore();
        cache = new CatCache(
                store,
                Logger.getLogger("CatCacheTest")
        );
    }

    @Test
    void ownerIndexFindsLoadedCat() {

        UUID owner = UUID.randomUUID();

        store.createCat(owner);

        Cat loaded = cache.loadCat(owner);

        assertNotNull(loaded);
        assertEquals(
                loaded,
                cache.getCat(owner)
        );
    }

    @Test
    void entityIndexFindsCatByEntity() {

        UUID owner = UUID.randomUUID();
        UUID entity = UUID.randomUUID();

        store.createCat(owner);
        store.setCatEntityUUID(owner, entity);

        Cat loaded = cache.loadCat(owner);

        assertEquals(
                loaded,
                cache.getCatByEntity(entity)
        );
    }

    @Test
    void unknownEntityReturnsNullWithoutThrowing() {

        /*
         * 回归防护（BUG A）：
         * 无索引条目且扫描未命中时，
         * 双参 remove(key, null) 会抛 NPE，
         * 这里必须静默返回 null。
         */
        assertDoesNotThrow(
                () -> assertNull(
                        cache.getCatByEntity(
                                UUID.randomUUID()
                        )
                )
        );
    }

    @Test
    void staleEntityIndexSelfHeals() {

        UUID owner = UUID.randomUUID();
        UUID oldEntity = UUID.randomUUID();
        UUID newEntity = UUID.randomUUID();

        store.createCat(owner);
        store.setCatEntityUUID(owner, oldEntity);

        Cat loaded = cache.loadCat(owner);

        /*
         * 模拟外部直接 setter 改变绑定
         * （Cat.setEntityUuid 不经过缓存）。
         */
        loaded.setEntityUuid(newEntity);

        /*
         * 新绑定：索引陈旧 → 回退扫描命中并自愈。
         */
        assertEquals(
                loaded,
                cache.getCatByEntity(newEntity)
        );

        /*
         * 旧绑定：索引中的脏条目在校验后清除。
         */
        assertNull(
                cache.getCatByEntity(oldEntity)
        );
    }

    @Test
    void removeByOwnerClearsEntityIndex() {

        UUID owner = UUID.randomUUID();
        UUID entity = UUID.randomUUID();

        store.createCat(owner);
        store.setCatEntityUUID(owner, entity);

        cache.loadCat(owner);
        cache.removeByOwner(owner);

        assertNull(cache.getCat(owner));
        assertNull(cache.getCatByEntity(entity));
    }
}
