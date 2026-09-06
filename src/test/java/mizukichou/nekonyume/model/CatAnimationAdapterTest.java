package mizukichou.nekonyume.model;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.testutil.FakeBukkit;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatAnimationAdapterTest {

    private Cat logicalCat;

    private CatBattleState battleState;

    private UUID entityUuid;

    @BeforeEach
    void setUp() {

        entityUuid =
                UUID.randomUUID();

        logicalCat =
                new Cat(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Mikan"
                );

        battleState =
                new CatBattleState();
    }

    private Entity entity(
            Object... answers
    ) {

        java.util.Map<String, Object> map =
                new java.util.LinkedHashMap<>();

        map.put(
                "getUniqueId",
                entityUuid
        );

        for (int i = 0;
                i < answers.length;
                i += 2) {

            map.put(
                    (String) answers[i],
                    answers[i + 1]
            );
        }

        return FakeBukkit.proxy(
                org.bukkit.entity.Cat.class,
                map,
                null
        );
    }

    private List<String> resolve(
            Entity entity
    ) {

        return CatAnimationAdapter.resolveAnimationCandidates(
                logicalCat,
                entity,
                battleState
        );
    }

    @Test
    void recoveringWinsOverEverything() {

        battleState.markRecovering(
                entityUuid,
                60_000L
        );

        logicalCat.setBehaviorMode(
                CatBehaviorMode.SIT
        );

        assertEquals(
                List.of(
                        CatAnimationAdapter.ANIM_HURT
                ),
                resolve(
                        entity()
                )
        );
    }

    @Test
    void sitModeReturnsSitCandidates() {

        logicalCat.setBehaviorMode(
                CatBehaviorMode.SIT
        );

        List<String> candidates =
                resolve(
                        entity()
                );

        assertEquals(
                CatAnimationAdapter.ANIM_SIT,
                candidates.get(0)
        );

        /*
         * 别名表：真实模型动画名（"sit down" 等）。
         */
        org.junit.jupiter.api.Assertions.assertTrue(
                candidates.contains(
                        "sit_down"
                )
        );
    }

    @Test
    void recentAttackReturnsAttackCandidates() {

        battleState.markAttack(
                entityUuid
        );

        assertEquals(
                List.of(
                        CatAnimationAdapter.ANIM_ATTACK
                ),
                resolve(
                        entity(
                                "getTarget",
                                null
                        )
                )
        );
    }

    @Test
    void attackWindowExpiresBackToIdle() throws Exception {

        battleState.markAttack(
                entityUuid
        );

        /*
         * 600ms 动作窗口：过期后不再是攻击信号
         * （getTarget != null 绝不参与判定——那是状态不是动作）。
         */
        Thread.sleep(
                650L
        );

        logicalCat.setLastFedAt(
                0L
        );

        assertEquals(
                List.of(
                        CatAnimationAdapter.ANIM_IDLE,
                        "stand"
                ),
                resolve(
                        entity(
                                "getTarget",
                                null
                        )
                )
        );
    }

    @Test
    void recentHurtReturnsHurtCandidates() {

        battleState.markHurt(
                entityUuid
        );

        assertEquals(
                List.of(
                        CatAnimationAdapter.ANIM_HURT
                ),
                resolve(
                        entity()
                )
        );
    }

    @Test
    void movingResolvesToWalk() {

        assertEquals(
                List.of(
                        CatAnimationAdapter.ANIM_WALK
                ),
                resolve(
                        entity(
                                "getTarget",
                                null,
                                "getVelocity",
                                new Vector(
                                        0.5,
                                        0.0,
                                        0.5
                                )
                        )
                )
        );
    }

    @Test
    void recentFeedResolvesToEat() {

        logicalCat.setLastFedAt(
                System.currentTimeMillis()
        );

        assertEquals(
                List.of(
                        CatAnimationAdapter.ANIM_EAT
                ),
                resolve(
                        entity(
                                "getTarget",
                                null,
                                "getVelocity",
                                new Vector(
                                        0.0,
                                        0.0,
                                        0.0
                                )
                        )
                )
        );
    }

    @Test
    void idleIsDefault() {

        logicalCat.setLastFedAt(
                0L
        );

        assertEquals(
                List.of(
                        CatAnimationAdapter.ANIM_IDLE,
                        "stand"
                ),
                resolve(
                        entity(
                                "getTarget",
                                null,
                                "getVelocity",
                                new Vector(
                                        0.0,
                                        0.0,
                                        0.0
                                )
                        )
                )
        );
    }

    @Test
    void nullInputsFallBackToIdle() {

        assertEquals(
                List.of(
                        CatAnimationAdapter.ANIM_IDLE,
                        "stand"
                ),
                CatAnimationAdapter.resolveAnimationCandidates(
                        null,
                        null,
                        null
                )
        );
    }
}
