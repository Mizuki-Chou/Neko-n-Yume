package mizukichou.nekonyume.config;

import lombok.Getter;
import mizukichou.nekonyume.cat.MeowDanQuality;
import org.bukkit.Material;

/**
 * 礼物档位条目（数据定义）。
 *
 * <p>
 * material 与 meowDanQuality 二选一，
 * 另一个为 null。
 * </p>
 */
@Getter
public final class GiftItemEntry {

    private final Material material;

    private final MeowDanQuality meowDanQuality;

    private final int minAmount;

    private final int maxAmount;

    private final int weight;

    public GiftItemEntry(
            Material material,
            MeowDanQuality meowDanQuality,
            int minAmount,
            int maxAmount,
            int weight
    ) {

        this.material = material;
        this.meowDanQuality = meowDanQuality;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.weight = weight;
    }

    public boolean isMeowDan() {

        return meowDanQuality != null;
    }
}