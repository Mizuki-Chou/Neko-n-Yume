package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.testutil.PipelineHarness;

import org.bukkit.Location;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.8.4：恢复/召唤管线端到端集成测试。
 *
 * <p>
 * 生产组件全部真实（CatEntityService / CatEntityRestorer /
 * CatEntityBinding / CatProgressionService / CatSkillManager /
 * MemoryCatStore / CatCache / Lang / ConfigManager），
 * 仅 Bukkit 触点由 FakeCatEntityRuntime 提供。
 * 历史上所有“致命竞态”第一次获得自动化守护。
 * </p>
 */
class CatEntityPipelineTest {

    @Test
    void summonSpawnsAndBindsNewCat() {

        PipelineHarness h = PipelineHarness.create();

        h.createLogicalCat();

        List<SummonResult> results = new ArrayList<>();

        h.service.spawnCat(h.player, "Mikan", results::add);

        assertEquals(List.of(SummonResult.SPAWNED), results);
        assertEquals(1, h.runtime.spawned.size());

        UUID entityUuid = h.store.getCatEntityUUID(h.playerUuid);
        assertNotNull(entityUuid);

        org.bukkit.entity.Cat spawned = h.runtime.spawned.get(0);
        assertTrue(
                spawned.getPersistentDataContainer().has(h.catKey, PersistentDataType.BYTE),
                "新实体必须写入猫标记 PDC"
        );

        assertEquals(
                h.playerUuid.toString(),
                spawned.getPersistentDataContainer().get(h.ownerKey, PersistentDataType.STRING),
                "新实体必须写入主人 PDC"
        );

        assertEquals(
                h.playerUuid,
                h.entityIndex.getOwner(entityUuid),
                "实体索引必须登记新实体"
        );
    }

    @Test
    void summonTeleportsExistingCatAndReportsAlreadyPresent() {

        PipelineHarness h = PipelineHarness.create();

        h.createLogicalCat();

        UUID existingUuid = UUID.randomUUID();
        Location farAway = new Location(h.world, 1100, 64, 1100);

        org.bukkit.entity.Cat existing = h.fakeCatEntity(existingUuid, farAway);
        h.store.setCatEntityUUID(h.playerUuid, existingUuid);

        h.runtime.registerChunk(
                h.world,
                6,
                6,
                h.fakeChunk(h.world, 6, 6, List.of())
        );

        List<SummonResult> results = new ArrayList<>();

        h.service.spawnCat(h.player, "Mikan", results::add);

        assertEquals(List.of(SummonResult.ALREADY_PRESENT), results);
        assertEquals(0, h.runtime.spawned.size(), "已有实体时不得新建实体");
        assertTrue(
                h.runtime.catCalls.get(existingUuid).contains("teleport"),
                "已有实体必须被传送回主人身边"
        );
        assertEquals(h.playerUuid, h.entityIndex.getOwner(existingUuid));
    }

    @Test
    void staleSummonAfterDeleteDoesNotResurrect() {

        PipelineHarness h = PipelineHarness.create();

        h.createLogicalCat();

        h.store.setCatLocation(
                h.playerUuid,
                h.world.getUID(),
                100,
                64,
                100
        );

        h.runtime.manualChunks = true;

        List<SummonResult> results = new ArrayList<>();

        h.service.spawnCat(h.player, "Mikan", results::add);

        assertEquals(1, h.runtime.pendingChunks.size(), "召唤必须挂起在区块 future 上");

        /*
         * 竞态窗口：流水线挂起期间，管理员删除玩家数据。
         */
        assertTrue(h.service.removePlayerCat(h.playerUuid));
        assertFalse(h.store.hasCat(h.playerUuid));

        /*
         * 旧区块回调回到主线程：代际已失效，必须被整体丢弃。
         */
        h.runtime.pendingChunks.get(0).complete(
                h.fakeChunk(h.world, 6, 6, List.of())
        );

        assertEquals(0, h.runtime.spawned.size(), "删除后的旧回调绝不能生成新实体");
        assertFalse(h.store.hasCat(h.playerUuid), "删除后的旧回调绝不能复活数据");
        assertTrue(results.isEmpty(), "过期召唤不得回调任何结果");
    }

    @Test
    void loginRestoreAfterDeleteDoesNotResurrect() {

        PipelineHarness h = PipelineHarness.create();

        h.createLogicalCat();

        UUID ghostEntityUuid = UUID.randomUUID();

        h.store.setCatEntityUUID(h.playerUuid, ghostEntityUuid);
        h.store.setCatLocation(
                h.playerUuid,
                h.world.getUID(),
                100,
                64,
                100
        );

        h.runtime.manualChunks = true;

        h.restorer.restoreCatEntity(h.player);

        assertEquals(1, h.runtime.pendingChunks.size(), "登录恢复必须挂起在区块 future 上");

        assertTrue(h.service.removePlayerCat(h.playerUuid));
        assertFalse(h.store.hasCat(h.playerUuid));

        h.runtime.pendingChunks.get(0).complete(
                h.fakeChunk(h.world, 6, 6, List.of())
        );

        assertEquals(0, h.runtime.spawned.size(), "登录恢复的旧回调绝不能生成实体");
        assertFalse(h.store.hasCat(h.playerUuid), "登录恢复的旧回调绝不能复活数据");
    }

    @Test
    void cleanupDuplicateCatsRemovesOnlyOthers() {

        PipelineHarness h = PipelineHarness.create();

        h.createLogicalCat();

        UUID keepUuid = UUID.randomUUID();
        UUID dupUuid = UUID.randomUUID();

        org.bukkit.entity.Cat keep = h.fakeCatEntity(keepUuid, h.homeLocation);
        org.bukkit.entity.Cat dup = h.fakeCatEntity(dupUuid, h.homeLocation);

        h.entityIndex.put(keepUuid, h.playerUuid);
        h.entityIndex.put(dupUuid, h.playerUuid);

        h.restorer.cleanupDuplicateCats(h.playerUuid, keep);

        assertTrue(
                h.runtime.catCalls.get(dupUuid).contains("remove"),
                "重复实体必须被移除"
        );
        assertFalse(
                h.runtime.catCalls.get(keepUuid).contains("remove"),
                "保留实体不得被误删"
        );
        assertEquals(
                List.of(keepUuid),
                new ArrayList<>(h.entityIndex.entitiesOf(h.playerUuid)),
                "索引必须只保留新实体"
        );
    }

    @Test
    void bindLogicalCatDoesNotRecreateDeletedData() {

        PipelineHarness h = PipelineHarness.create();

        org.bukkit.entity.Cat stray = h.fakeCatEntity(UUID.randomUUID(), h.homeLocation);

        Cat logical = h.binding.bindLogicalCat(h.player, stray, "Ghost");

        assertNull(logical, "无存档数据时绑定必须失败");
        assertFalse(h.store.hasCat(h.playerUuid), "绑定兜底绝不能重建已删除的数据");
    }

    @Test
    void chunkFailureFallsBackToNewSpawn() {

        PipelineHarness h = PipelineHarness.create();

        h.createLogicalCat();

        h.store.setCatLocation(
                h.playerUuid,
                h.world.getUID(),
                100,
                64,
                100
        );

        h.runtime.manualChunks = true;

        List<SummonResult> results = new ArrayList<>();

        h.service.spawnCat(h.player, "Mikan", results::add);

        h.runtime.pendingChunks.get(0).completeExceptionally(
                new IllegalStateException("chunk load boom")
        );

        assertEquals(List.of(SummonResult.SPAWNED), results, "区块加载失败必须兜底新建");
        assertEquals(1, h.runtime.spawned.size());
        assertNotNull(h.store.getCatEntityUUID(h.playerUuid));
    }
}
