package mizukichou.nekonyume.cat;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatSkillsTest {

    @Test
    void addsUniquelyAndIgnoresNull() {

        CatSkills skills = new CatSkills();

        skills.add(CatSkill.SHARP_CLAW);
        skills.add(CatSkill.SHARP_CLAW);
        skills.add(null);

        assertEquals(1, skills.size());
        assertTrue(skills.contains(CatSkill.SHARP_CLAW));
        assertFalse(skills.contains(CatSkill.IRON_WALL));
    }

    @Test
    void toListIsDefensiveCopy() {

        CatSkills skills = new CatSkills();

        skills.add(CatSkill.SHARP_CLAW);

        List<CatSkill> snapshot = skills.toList();

        skills.add(CatSkill.IRON_WALL);

        assertEquals(1, snapshot.size());
        assertEquals(2, skills.size());
    }

    @Test
    void setReplacesAtIndex() {

        CatSkills skills = new CatSkills();

        skills.add(CatSkill.SHARP_CLAW);
        skills.set(0, CatSkill.IRON_WALL);

        assertEquals(CatSkill.IRON_WALL, skills.get(0));
        assertEquals(1, skills.size());

        skills.set(5, CatSkill.DRAIN);
        assertEquals(1, skills.size());

        skills.set(0, null);
        assertEquals(CatSkill.IRON_WALL, skills.get(0));
    }

    @Test
    void replaceAllFiltersDuplicatesAndNull() {

        /*
         * 注意：
         * List.of 不接受 null 元素（会直接抛 NPE），
         * 要构造"含 null 的初始集合"必须用 Arrays.asList。
         */
        CatSkills skills = new CatSkills(
                Arrays.asList(
                        CatSkill.SHARP_CLAW,
                        CatSkill.SHARP_CLAW,
                        null,
                        CatSkill.IRON_WALL
                )
        );

        assertEquals(2, skills.size());

        skills.replaceAll(null);
        assertTrue(skills.isEmpty());
    }

    @Test
    void setRejectsDuplicateSkill() {

        CatSkills skills = new CatSkills();

        skills.add(CatSkill.SHARP_CLAW);
        skills.add(CatSkill.IRON_WALL);

        skills.set(0, CatSkill.IRON_WALL);

        assertEquals(CatSkill.SHARP_CLAW, skills.get(0));
        assertEquals(2, skills.size());
    }
}
