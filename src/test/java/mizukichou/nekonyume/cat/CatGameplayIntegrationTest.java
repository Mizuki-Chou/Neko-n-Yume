package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.event.CatLevelUpEvent;
import mizukichou.nekonyume.event.CatSkillActivatedEvent;
import mizukichou.nekonyume.skill.CatSkillManager;
import mizukichou.nekonyume.testutil.PipelineHarness;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.8.4：玩法域（食物/技能/成长）端到端集成测试。
 *
 * <p>
 * 与管线测试同用一套真实生产组合根：
 * feedCat 的物品经济、技能冷却与效果、升级生命同步
 * 全部在无服务端环境里端到端验证。
 * </p>
 */
class CatGameplayIntegrationTest {

    private static final String FOOD_CONFIG =
            "food:\n"
                    + "  values:\n"
                    + "    GOLD_NUGGET: 2\n";

    @Test
    void foodValuesFollowConfig() {

        PipelineHarness h = PipelineHarness.createWithConfig(FOOD_CONFIG);

        assertEquals(
                2,
                h.foodManager.getFoodValues().get(Material.GOLD_NUGGET),
                "配置的食物值必须生效"
        );
        assertEquals(
                1,
                h.foodManager.getFoodValues().size(),
                "配置食物表存在时替换内建默认表"
        );
    }

    @Test
    void skillActivationRecordsCooldownAndAppliesEffect() {

        PipelineHarness h = PipelineHarness.create();

        h.createLogicalCat();

        assertTrue(
                h.progression.grantSkill(h.player, CatSkill.SWIFT_PAWS),
                "授予技能必须成功"
        );

        CatSkillManager manager = h.skillManager;

        assertFalse(manager.isOnCooldown(h.player, CatSkill.SWIFT_PAWS));

        boolean activated = manager.activateSkill(h.player, CatSkill.SWIFT_PAWS);

        assertTrue(activated, "主动技能必须激活成功");
        assertTrue(
                manager.isOnCooldown(h.player, CatSkill.SWIFT_PAWS),
                "激活后必须进入冷却"
        );
        assertTrue(
                h.runtime.potions.stream().anyMatch(p -> p.startsWith("speed:")),
                "灵猫迅捷必须施加速度效果"
        );
        assertTrue(h.runtime.sounds.contains("purr"), "激活必须播放猫叫");
        assertTrue(
                h.runtime.events.stream().anyMatch(e -> e instanceof CatSkillActivatedEvent),
                "激活必须触发 CatSkillActivatedEvent"
        );
    }

    @Test
    void skillActivationFailsWhenCatDoesNotHaveSkill() {

        PipelineHarness h = PipelineHarness.create();

        h.createLogicalCat();

        assertFalse(
                h.skillManager.activateSkill(h.player, CatSkill.SWIFT_PAWS),
                "未拥有的技能不得激活"
        );
        assertTrue(
                h.runtime.potions.isEmpty(),
                "未拥有的技能不得施加任何效果"
        );
    }

    @Test
    void skillRefreshCostFallsBackToMeowPowerWithoutPlayerPoints() {

        PipelineHarness h = PipelineHarness.create();

        h.createLogicalCat();

        int cost = h.skillManager.getRefreshCost(false);

        assertTrue(cost >= 0, "无 PlayerPoints 时必须回退喵力消耗且不抛异常");
    }

    @Test
    void progressionLevelUpKeepsEquipHealthBonus() {

        PipelineHarness h = PipelineHarness.create();

        h.store.createCat(h.playerUuid);
        h.store.setCatEquipment(h.playerUuid, "collar-rare");

        List<SummonResult> results = new ArrayList<>();
        h.service.spawnCat(h.player, "Mikan", results::add);

        assertEquals(List.of(SummonResult.SPAWNED), results);

        Cat cat = h.cache.getCat(h.playerUuid);
        assertNotNull(cat);
        assertNotNull(cat.getEquippedItem(), "装备必须从存档恢复");

        h.progression.gainExperience(h.player, cat, 10_000);

        double expected = 10.0 + cat.getLevel() / 4.0 + 10.0;

        assertEquals(
                expected,
                h.runtime.lastMaxHealthBase,
                0.001,
                "升级后最大生命必须保留装备加成（0.8.1 R1 回归）"
        );
        assertTrue(
                h.runtime.events.stream().anyMatch(e -> e instanceof CatLevelUpEvent),
                "升级必须触发 CatLevelUpEvent"
        );
    }
}
