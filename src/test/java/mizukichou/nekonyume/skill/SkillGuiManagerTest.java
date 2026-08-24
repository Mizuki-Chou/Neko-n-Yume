package mizukichou.nekonyume.skill;

import mizukichou.nekonyume.cat.CatTier;
import mizukichou.nekonyume.gui.SkillGuiManager;
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
    void hintsMapToLanguageKeys() {

        /*
         * 提示文案不再硬编码在代码里：
         * 统一映射到 lang 文件的 checkpoint.hint-N 键，
         * 四语言文件各提供对应翻译。
         */
        assertEquals(
                "checkpoint.hint-0",
                SkillGuiManager.checkpointHint(0)
        );

        assertEquals(
                "checkpoint.hint-1",
                SkillGuiManager.checkpointHint(1)
        );

        assertEquals(
                "checkpoint.hint-2",
                SkillGuiManager.checkpointHint(2)
        );

        assertEquals(
                "checkpoint.hint-3",
                SkillGuiManager.checkpointHint(3)
        );
    }

}
