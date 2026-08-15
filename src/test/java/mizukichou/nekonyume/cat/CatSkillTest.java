package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatSkillTest {

    @Test
    void poolHasExpectedDistribution() {

        assertEquals(
                6,
                countTier(CatTier.COMMON)
        );

        assertEquals(
                7,
                countTier(CatTier.RARE)
        );

        assertEquals(
                7,
                countTier(CatTier.UNIQUE)
        );

        assertEquals(
                7,
                countTier(CatTier.DREAM)
        );

        assertEquals(
                27,
                CatSkill.values().length
        );
    }

    private int countTier(
            CatTier tier
    ) {

        int count = 0;

        for (CatSkill skill :
                CatSkill.values()) {

            if (skill.getTier() == tier) {
                count++;
            }
        }

        return count;
    }

    @Test
    void poolForFiltersByTier() {

        for (CatSkill skill :
                CatSkill.poolFor(
                        CatTier.COMMON
                )) {

            assertEquals(
                    CatTier.COMMON,
                    skill.getTier()
            );
        }

        List<CatSkill> uniquePool =
                CatSkill.poolFor(
                        CatTier.UNIQUE
                );

        assertTrue(
                uniquePool.contains(
                        CatSkill.SHARP_CLAW
                )
        );

        assertTrue(
                uniquePool.contains(
                        CatSkill.IRON_WALL
                )
        );

        assertTrue(
                uniquePool.contains(
                        CatSkill.SPIRIT_SHOT
                )
        );

        assertFalse(
                uniquePool.contains(
                        CatSkill.DREAM_AWAKEN
                )
        );

        assertEquals(
                CatSkill.values().length,
                CatSkill.poolFor(
                                CatTier.DREAM
                        )
                        .size()
        );
    }

    @Test
    void everySkillHasCompleteMetadata() {

        for (CatSkill skill :
                CatSkill.values()) {

            assertNotNull(
                    skill.getDisplayName()
            );

            assertNotNull(
                    skill.getIcon()
            );

            assertNotNull(
                    skill.getType()
            );

            assertNotNull(
                    skill.getDescription()
            );
        }
    }

    @Test
    void nameParsing() {

        assertEquals(
                CatSkill.DREAM_AWAKEN,
                CatSkill.fromName("DREAM_AWAKEN")
        );

        assertEquals(
                CatSkill.SHARP_CLAW,
                CatSkill.fromName("sharp_claw")
        );

        assertNull(
                CatSkill.fromName(
                        "不存在的技能"
                )
        );

        assertNull(
                CatSkill.fromName(
                        null
                )
        );
    }

    @Test
    void poolOfTierExactReturnsOnlySkillsOfThatTier() {

        for (CatTier tier : CatTier.values()) {

            List<CatSkill> pool =
                    CatSkill.poolOfTierExact(tier);

            assertFalse(
                    pool.isEmpty(),
                    tier + " exact pool should not be empty"
            );

            for (CatSkill skill : pool) {

                assertEquals(
                        tier,
                        skill.getTier(),
                        skill + " should belong to " + tier
                );
            }
        }
    }

    @Test
    void poolOfTierExactCoversAllSkillsAndHandlesNull() {

        List<CatSkill> all =
                new ArrayList<>();

        for (CatTier tier : CatTier.values()) {

            all.addAll(
                    CatSkill.poolOfTierExact(tier)
            );
        }

        assertEquals(
                27,
                all.size(),
                "exact pools should cover all 27 skills"
        );

        assertTrue(
                CatSkill.poolOfTierExact(null)
                        .isEmpty()
        );
    }

}
