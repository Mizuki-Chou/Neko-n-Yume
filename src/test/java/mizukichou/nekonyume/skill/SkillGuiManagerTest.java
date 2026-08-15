package mizukichou.nekonyume.skill;

import mizukichou.nekonyume.cat.CatTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillGuiManagerTest {

    @Test
    void commonCheckpointMapping() {

        assertEquals(1, SkillGuiManager.checkpointForSlot(CatTier.COMMON, 0));
        assertEquals(2, SkillGuiManager.checkpointForSlot(CatTier.COMMON, 1));
        assertEquals(3, SkillGuiManager.checkpointForSlot(CatTier.COMMON, 5));
    }

    @Test
    void rareCheckpointMapping() {

        assertEquals(1, SkillGuiManager.checkpointForSlot(CatTier.RARE, 0));
        assertEquals(2, SkillGuiManager.checkpointForSlot(CatTier.RARE, 1));
        assertEquals(3, SkillGuiManager.checkpointForSlot(CatTier.RARE, 2));
        assertEquals(3, SkillGuiManager.checkpointForSlot(CatTier.RARE, 9));
    }

    @Test
    void uniqueCheckpointMapping() {

        assertEquals(1, SkillGuiManager.checkpointForSlot(CatTier.UNIQUE, 0));
        assertEquals(1, SkillGuiManager.checkpointForSlot(CatTier.UNIQUE, 1));
        assertEquals(2, SkillGuiManager.checkpointForSlot(CatTier.UNIQUE, 2));
        assertEquals(2, SkillGuiManager.checkpointForSlot(CatTier.UNIQUE, 3));
        assertEquals(3, SkillGuiManager.checkpointForSlot(CatTier.UNIQUE, 4));
        assertEquals(3, SkillGuiManager.checkpointForSlot(CatTier.UNIQUE, 5));
    }

    @Test
    void dreamCheckpointMapping() {

        assertEquals(0, SkillGuiManager.checkpointForSlot(CatTier.DREAM, 0));
        assertEquals(1, SkillGuiManager.checkpointForSlot(CatTier.DREAM, 1));
        assertEquals(1, SkillGuiManager.checkpointForSlot(CatTier.DREAM, 3));
        assertEquals(2, SkillGuiManager.checkpointForSlot(CatTier.DREAM, 4));
        assertEquals(2, SkillGuiManager.checkpointForSlot(CatTier.DREAM, 6));
        assertEquals(3, SkillGuiManager.checkpointForSlot(CatTier.DREAM, 7));
        assertEquals(3, SkillGuiManager.checkpointForSlot(CatTier.DREAM, 9));
    }

    @Test
    void hintsAreReadable() {

        assertEquals("天生梦槽", SkillGuiManager.checkpointHint(0));
        assertEquals("喵阶 1", SkillGuiManager.checkpointHint(1));
        assertEquals("喵阶 10 且等级 30", SkillGuiManager.checkpointHint(2));
        assertEquals("喵阶 30 且等级 80", SkillGuiManager.checkpointHint(3));
    }
}
