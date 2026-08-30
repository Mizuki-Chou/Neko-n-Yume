package mizukichou.nekonyume.cat;

import org.bukkit.Material;

import lombok.Getter;

/*
 * 装备类型（0.8.0 装备系统）。
 *
 * 五型：项圈（战斗向）、铃铛（辅助向）、围巾（续航向）、
 * 名牌（成长向）、毛线球（节奏向）。
 * 猫只有一个装备位，各类型互斥。
 */
@Getter
public enum CatEquipType {

    COLLAR(
            "collar",
            Material.LEAD,
            92000
    ),

    BELL(
            "bell",
            Material.BELL,
            92010
    ),

    SCARF(
            "scarf",
            Material.CYAN_WOOL,
            92020
    ),

    NAME_TAG(
            "name-tag",
            Material.NAME_TAG,
            92030
    ),

    YARN_BALL(
            "yarn-ball",
            Material.STRING,
            92040
    );

    private final String id;

    private final Material material;

    /*
     * 自定义模型数据基数：
     * 具体物品 = base + quality.ordinal() + 1。
     */
    private final int modelDataBase;

    CatEquipType(
            String id,
            Material material,
            int modelDataBase
    ) {

        this.id = id;
        this.material = material;
        this.modelDataBase = modelDataBase;
    }
}
