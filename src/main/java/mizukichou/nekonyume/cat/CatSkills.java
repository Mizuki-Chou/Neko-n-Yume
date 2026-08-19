package mizukichou.nekonyume.cat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 猫咪技能槽的有序集合。
 *
 * <p>
 * 从 Cat 中抽取：
 * 维护"唯一 + 按槽位顺序"两个不变量，
 * 对外一律防御性复制。
 * </p>
 */
public final class CatSkills {

    private final List<CatSkill> skills =
            new ArrayList<>();

    public CatSkills() {
    }

    public CatSkills(
            Collection<CatSkill> initial
    ) {

        replaceAll(initial);
    }

    public int size() {
        return skills.size();
    }

    public boolean isEmpty() {
        return skills.isEmpty();
    }

    public CatSkill get(int index) {
        return skills.get(index);
    }

    public boolean contains(CatSkill skill) {

        return skill != null &&
                skills.contains(skill);
    }

    /**
     * 返回不可变快照。
     */
    public List<CatSkill> toList() {

        return List.copyOf(skills);
    }

    /**
     * 追加（null 与重复忽略）。
     */
    public void add(CatSkill skill) {

        if (skill == null ||
                skills.contains(skill)) {

            return;
        }

        skills.add(skill);
    }

    /**
     * 替换指定槽位（用于刷新）。
     */
    /**
     * 替换指定槽位（用于刷新）。
     *
     * <p>
     * 不变量：槽位集合必须保持唯一。
     * 若目标技能已存在于其他槽位，拒绝写入，
     * 防止产生重复技能。
     * </p>
     */
    public void set(
            int index,
            CatSkill skill
    ) {

        if (index < 0 ||
                index >= skills.size() ||
                skill == null) {

            return;
        }

        int existing =
                skills.indexOf(
                        skill
                );

        if (existing >= 0 &&
                existing != index) {

            return;
        }

        skills.set(index, skill);
    }

    public void clear() {
        skills.clear();
    }

    /**
     * 整体替换（null 与重复过滤）。
     */
    public void replaceAll(
            Collection<CatSkill> newSkills
    ) {

        skills.clear();

        if (newSkills == null) {
            return;
        }

        for (CatSkill skill :
                newSkills) {

            if (skill != null &&
                    !skills.contains(skill)) {

                skills.add(skill);
            }
        }
    }

    @Override
    public String toString() {

        return skills.toString();
    }
}

