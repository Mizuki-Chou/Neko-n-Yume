package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.event.CatLevelUpEvent;
import mizukichou.nekonyume.event.CatMeowRankUpEvent;
import mizukichou.nekonyume.event.CatSkillRollEvent;
import mizukichou.nekonyume.skill.CatSkillManager;
import mizukichou.nekonyume.skill.SkillRefreshCostProvider;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 猫咪成长与技能槽服务。
 *
 * <p>
 * 职责：
 * 1. 经验（统一入口，含升级反馈与事件）；
 * 2. 喵力（统一入口，含升阶反馈与事件）；
 * 3. 技能槽同步 / 抽取 / 刷新 / 管理员授予。
 * </p>
 *
 * <p>
 * 不接触 Bukkit 猫实体；实体效果由 CatEntityService / CatSkillManager 负责。
 * </p>
 */
public class CatProgressionService {

    private final CatStore store;
    private final CatCache cache;
    private final PluginConfig config;
    private final CatSkillManager skillManager;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    private final Random random =
            new Random();

    public CatProgressionService(
            CatStore store,
            CatCache cache,
            PluginConfig config,
            CatSkillManager skillManager
    ) {

        this.store = store;
        this.cache = cache;
        this.config = config;
        this.skillManager = skillManager;
    }

    /*
     * ============================================================
     * 增加经验
     * ============================================================
     *
     * 统一入口：所有经验来源都应该通过这里。
     * 等级曲线由配置 growth.level-curve-base 决定。
     */

    public void gainExperience(
            Player player,
            Cat cat,
            int amount
    ) {

        if (player == null ||
                cat == null ||
                amount <= 0) {

            return;
        }

        int fromLevel = cat.getLevel();

        int gained =
                cat.addExperience(
                        amount,
                        config.getLevelCurveBase()
                );

        /*
         * 持久化。
         */
        store.setCatExperience(
                player.getUniqueId(),
                cat.getExperience()
        );

        store.setCatLevel(
                player.getUniqueId(),
                cat.getLevel()
        );

        if (gained <= 0) {
            return;
        }

        /*
         * 升级反馈。
         */
        player.sendMessage(
                mm.deserialize(
                        "<gradient:#fde68a:#f59e0b>🎉 </gradient>"
                ).append(
                        Component.text(cat.getName())
                ).append(
                        mm.deserialize(
                                "<white> 升级到了 <yellow>"
                                        + cat.getLevel()
                                        + " 级</yellow>!</white>"
                        )
                )
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP,
                1.0f,
                1.0f
        );

        /*
         * 事件。
         */
        Bukkit.getPluginManager()
                .callEvent(
                        new CatLevelUpEvent(
                                player,
                                cat,
                                fromLevel,
                                cat.getLevel()
                        )
                );

        /*
         * 等级可能触发了技能槽拐点。
         */
        syncSkillSlots(player, cat);
    }

    /*
     * ============================================================
     * 增加喵力
     * ============================================================
     *
     * 统一入口：抚摸 / 喂食概率 / 喵丹都应该通过这里。
     * 喵阶曲线由配置 meow.rank-curve-offset 决定。
     */

    public void grantMeowPower(
            Player player,
            Cat cat,
            int amount
    ) {

        if (player == null ||
                cat == null ||
                amount <= 0) {

            return;
        }

        int fromRank = cat.getMeowRank();

        int gained =
                cat.addMeowPower(
                        amount,
                        config.getMeowRankCurveOffset()
                );

        /*
         * 持久化。
         */
        store.setCatMeowPower(
                player.getUniqueId(),
                cat.getMeowPower()
        );

        store.setCatMeowRank(
                player.getUniqueId(),
                cat.getMeowRank()
        );

        /*
         * 获得喵力的惊喜反馈（无论是否升阶）。
         */
        player.sendMessage(
                mm.deserialize(
                        "<gradient:#c4b5fd:#a78bfa>✨ 喵光一闪!</gradient>"
                ).append(
                        Component.text(" " + cat.getName())
                ).append(
                        mm.deserialize(
                                "<white> 获得了 <light_purple>"
                                        + amount
                                        + " 点喵力</light_purple>!</white>"
                        )
                )
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                1.0f,
                1.5f
        );

        if (gained <= 0) {
            return;
        }

        /*
         * 升阶反馈。
         */
        player.sendMessage(
                mm.deserialize(
                        "<gradient:#c4b5fd:#a78bfa>🌟 喵阶提升!</gradient>"
                ).append(
                        Component.text(" " + cat.getName())
                ).append(
                        mm.deserialize(
                                "<white> 提升到了 <light_purple>喵阶 "
                                        + cat.getMeowRank()
                                        + "</light_purple>!</white>"
                        )
                )
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP,
                1.0f,
                1.2f
        );

        /*
         * 粒子：如果猫实体在线，在猫的位置生成粒子。
         */
        UUID entityUuid = cat.getEntityUuid();

        if (entityUuid != null) {

            Entity entity =
                    Bukkit.getEntity(entityUuid);

            if (entity != null && entity.isValid()) {

                entity.getWorld()
                        .spawnParticle(
                                Particle.HEART,
                                entity.getLocation().add(0, 1, 0),
                                30,
                                0.5,
                                0.5,
                                0.5,
                                0.05
                        );
            }
        }

        /*
         * 事件。
         */
        Bukkit.getPluginManager()
                .callEvent(
                        new CatMeowRankUpEvent(
                                player,
                                cat,
                                fromRank,
                                cat.getMeowRank()
                        )
                );

        /*
         * 喵阶可能触发了技能槽拐点。
         */
        syncSkillSlots(player, cat);
    }

    /*
     * ============================================================
     * 技能槽同步
     * ============================================================
     *
     * 在经验 / 喵力变化后调用。
     * 若技能数少于应有槽数，对每个新槽免费抽取一次并通知玩家。
     */

    public void syncSkillSlots(
            Player player,
            Cat cat
    ) {

        if (player == null ||
                cat == null ||
                !player.isOnline()) {

            return;
        }

        int current = cat.getSkills().size();

        int expected = cat.getSkillSlotCount();

        if (current >= expected) {
            return;
        }

        for (int slot = current; slot < expected; slot++) {

            CatSkill rolled =
                    rollSkillForSlot(cat, slot);

            if (rolled != null) {

                cat.addSkill(rolled);

                player.sendMessage(
                        mm.deserialize(
                                "<gradient:#fde68a:#f59e0b>🎉 </gradient>"
                        ).append(
                                Component.text(cat.getName())
                        ).append(
                                mm.deserialize(
                                        "<white> 学会了新技能：</white>"
                                )
                        ).append(
                                Component.text(
                                        rolled.getDisplayName()
                                )
                        )
                );
            }
        }

        if (cat.getSkills().size() > current) {

            persistSkills(player.getUniqueId(), cat);
        }
    }

    /*
     * ============================================================
     * 为指定槽位抽取技能（不修改任何状态）
     * ============================================================
     *
     * 品质上限：
     * 梦槽 → 只出梦幻级（精确品质池，补丁 1）；
     * 梦幻猫其他槽 → 独特；
     * 其余 → 自身底蕴。
     *
     * 排除已有技能，按品质权重抽取。
     */

    private CatSkill rollSkillForSlot(
            Cat cat,
            int slotIndex
    ) {

        boolean dreamSlot =
                cat.isDreamSlot(slotIndex);

        List<CatSkill> candidates =
                dreamSlot
                        ? CatSkill.poolOfTierExact(CatTier.DREAM)
                        : CatSkill.poolFor(
                        CatTier.maxSkillTierForSlot(
                                cat.getTier(),
                                dreamSlot
                        )
                );

        List<CatSkill> pool = new ArrayList<>();

        for (CatSkill skill : candidates) {

            if (!cat.hasSkill(skill)) {
                pool.add(skill);
            }
        }

        return weightedSkillRoll(pool);
    }

    private CatSkill weightedSkillRoll(
            List<CatSkill> pool
    ) {

        int totalWeight = 0;

        for (CatSkill skill : pool) {
            totalWeight += skill.getTier().getWeight();
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);

        CatSkill chosen =
                pool.get(pool.size() - 1);

        for (CatSkill skill : pool) {

            roll -= skill.getTier().getWeight();

            if (roll < 0) {

                chosen = skill;
                break;
            }
        }

        return chosen;
    }

    /*
     * ============================================================
     * 刷新技能槽
     * ============================================================
     *
     * 消耗由 CatSkillManager 的刷新消耗提供者处理。
     * 梦槽消耗 × 倍率。
     *
     * true = 刷新成功。
     */

    public boolean refreshSkillSlot(
            Player player,
            int slotIndex
    ) {

        if (player == null) {
            return false;
        }

        Cat cat = cache.getCat(player);

        if (cat == null) {
            cat = cache.loadCat(player);
        }

        if (cat == null) {
            return false;
        }

        List<CatSkill> currentSkills =
                cat.getSkills();

        if (slotIndex < 0 ||
                slotIndex >= currentSkills.size()) {

            return false;
        }

        boolean dreamSlot =
                cat.isDreamSlot(slotIndex);

        int cost =
                skillManager.getRefreshCost(dreamSlot);

        SkillRefreshCostProvider provider =
                skillManager.getRefreshCostProvider();

        if (!provider.canAfford(player, cost)) {

            player.sendMessage(
                    mm.deserialize(
                            "<red>❌ "
                                    + skillManager.getRefreshCostDisplay(dreamSlot)
                                    + " 不足，无法刷新。</red>"
                    )
            );

            return false;
        }

        CatSkill oldSkill =
                currentSkills.get(slotIndex);

        /*
         * 排除已有技能（重抽必然换新）。
         * 补丁 1：梦槽使用精确梦幻品质池。
         */
        List<CatSkill> candidates =
                dreamSlot
                        ? CatSkill.poolOfTierExact(CatTier.DREAM)
                        : CatSkill.poolFor(
                        CatTier.maxSkillTierForSlot(
                                cat.getTier(),
                                dreamSlot
                        )
                );

        List<CatSkill> pool = new ArrayList<>();

        for (CatSkill skill : candidates) {

            if (!cat.hasSkill(skill)) {
                pool.add(skill);
            }
        }

        if (pool.isEmpty()) {

            player.sendMessage(
                    mm.deserialize(
                            "<yellow>没有可抽取的新技能了。</yellow>"
                    )
            );

            return false;
        }

        /*
         * 扣费。
         */
        if (!provider.charge(player, cost)) {

            player.sendMessage(
                    mm.deserialize(
                            "<red>❌ 扣除"
                                    + provider.getDisplayName()
                                    + "失败。</red>"
                    )
            );

            return false;
        }

        CatSkill newSkill =
                weightedSkillRoll(pool);

        if (newSkill == null) {
            return false;
        }

        cat.setSkillAt(slotIndex, newSkill);

        persistSkills(player.getUniqueId(), cat);

        /*
         * 事件与反馈。
         */
        Bukkit.getPluginManager()
                .callEvent(
                        new CatSkillRollEvent(
                                player,
                                cat,
                                slotIndex,
                                oldSkill,
                                newSkill,
                                true
                        )
                );

        player.sendMessage(
                mm.deserialize(
                        "<gradient:#c4b5fd:#a78bfa>✨ 技能槽刷新成功：</gradient>"
                ).append(
                        Component.text(oldSkill.getDisplayName())
                ).append(
                        mm.deserialize("<white> → </white>")
                ).append(
                        Component.text(newSkill.getDisplayName())
                )
        );

        return true;
    }

    /*
     * ============================================================
     * 管理员发放技能
     * ============================================================
     *
     * 无视槽位上限，追加到技能列表末尾。
     */

    public boolean grantSkill(
            Player player,
            CatSkill skill
    ) {

        if (player == null || skill == null) {
            return false;
        }

        Cat cat = cache.loadCat(player);

        if (cat == null) {
            return false;
        }

        if (cat.hasSkill(skill)) {
            return false;
        }

        cat.addSkill(skill);

        persistSkills(player.getUniqueId(), cat);

        return true;
    }

    private void persistSkills(
            UUID playerUUID,
            Cat cat
    ) {

        List<String> names = new ArrayList<>();

        for (CatSkill skill : cat.getSkills()) {
            names.add(skill.name());
        }

        store.setCatSkills(playerUUID, names);
    }
}
