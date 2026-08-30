package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体索引（0.8.3）的双向映射语义测试。
 */
class CatEntityIndexTest {

    private static UUID uuid() {

        return UUID.randomUUID();
    }

    @Test
    void putAndLookupBothDirections() {

        CatEntityIndex index = new CatEntityIndex();

        UUID entity = uuid();
        UUID owner = uuid();

        assertNull(index.getOwner(entity));
        assertTrue(index.entitiesOf(owner).isEmpty());

        index.put(entity, owner);

        assertEquals(owner, index.getOwner(entity));
        assertEquals(1, index.entitiesOf(owner).size());
        assertTrue(index.entitiesOf(owner).contains(entity));
        assertEquals(1, index.size());
    }

    @Test
    void putIsIdempotentAndRebindsToNewOwner() {

        CatEntityIndex index = new CatEntityIndex();

        UUID entity = uuid();
        UUID firstOwner = uuid();
        UUID secondOwner = uuid();

        index.put(entity, firstOwner);
        index.put(entity, firstOwner);

        assertEquals(1, index.size());
        assertEquals(1, index.entitiesOf(firstOwner).size());

        /*
         * 同一实体重新绑定到新主人：旧主人的反向集合必须同步。
         */
        index.put(entity, secondOwner);

        assertEquals(secondOwner, index.getOwner(entity));
        assertTrue(index.entitiesOf(firstOwner).isEmpty());
        assertEquals(1, index.entitiesOf(secondOwner).size());
    }

    @Test
    void removeEntityCleansBothDirections() {

        CatEntityIndex index = new CatEntityIndex();

        UUID entity = uuid();
        UUID owner = uuid();

        index.put(entity, owner);
        index.removeEntity(entity);

        assertNull(index.getOwner(entity));
        assertTrue(index.entitiesOf(owner).isEmpty());
        assertEquals(0, index.size());

        /*
         * 重复移除幂等。
         */
        index.removeEntity(entity);
        assertEquals(0, index.size());
    }

    @Test
    void removeOwnerCleansOnlyItsEntities() {

        CatEntityIndex index = new CatEntityIndex();

        UUID firstEntity = uuid();
        UUID secondEntity = uuid();
        UUID firstOwner = uuid();
        UUID secondOwner = uuid();

        index.put(firstEntity, firstOwner);
        index.put(secondEntity, secondOwner);

        index.removeOwner(firstOwner);

        assertNull(index.getOwner(firstEntity));
        assertEquals(secondOwner, index.getOwner(secondEntity));
        assertEquals(1, index.size());
        assertEquals(1, index.entitiesOf(secondOwner).size());

        index.removeOwner(firstOwner);

        assertEquals(1, index.size());
    }

    @Test
    void nullArgumentsAreIgnored() {

        CatEntityIndex index = new CatEntityIndex();

        index.put(null, uuid());
        index.put(uuid(), null);
        index.put(null, null);
        index.removeEntity(null);
        index.removeOwner(null);

        assertEquals(0, index.size());
        assertNull(index.getOwner(null));
        assertTrue(index.entitiesOf(null).isEmpty());
    }

    @Test
    void clearEmptiesEverything() {

        CatEntityIndex index = new CatEntityIndex();

        UUID entity = uuid();
        UUID owner = uuid();

        index.put(entity, owner);
        index.clear();

        assertEquals(0, index.size());
        assertNull(index.getOwner(entity));
        assertTrue(index.entitiesOf(owner).isEmpty());
    }
}
