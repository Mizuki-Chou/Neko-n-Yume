package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.event.CatLevelUpEvent;
import mizukichou.nekonyume.event.CatMeowRankUpEvent;
import mizukichou.nekonyume.event.CatSkillRollEvent;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.skill.CatSkillManager;
import mizukichou.nekonyume.skill.SkillRefreshCostProvider;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
 * 0.6.2：升级时同步猫实体最大生命（10 + 等级/4）。
 * 0.7.0：配置改走 ConfigManager 快照；
 * 玩家文案改走 Lang。
 * </p>
 */
public class CatProgressionService {

    private final CatStore store;
    private final CatCache cache;
    private final ConfigManager configManager;
    private final CatSkillManager skillManager;
    private final Lang lang;

    private final Random random =
            new Random();

    public CatProgressionService(
            CatStore store,
            CatCache cache,
            ConfigManager configManager,
            CatSkillManager skillManager,
            Lang lang
    ) {

        this.store = store;
        this.cache = cache;
        this.configManager = configManager;
        this.skillManager = skillManager;
        this.lang = lang;
    }

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

        ConfigSnapshot config =
                configManager.snapshot();

        int fromLevel = cat.getLevel();

        int gained =
                cat.addExperience(
                        amount,
                        config.getGrowth()
                                .getLevelCurveBase()
                );

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

        player.sendMessage(
                lang.forPlayer(player).message(
                        "progression.level-up",
                        cat.getName(),
                        String.valueOf(
                                cat.getLevel()
                        )
                )
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP,
                1.0f,
                1.0f
        );

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
         * 升级：同步实体最大生命（10 + 等级/4）。
         */
        applyLevelMaxHealth(
                player,
                cat
        );

        syncSkillSlots(player, cat);
    }

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
                        configManager.snapshot()
                                .getMeow()
                                .getRankCurveOffset()
                );

        store.setCatMeowPower(
                player.getUniqueId(),
                cat.getMeowPower()
        );

        store.setCatMeowRank(
                player.getUniqueId(),
                cat.getMeowRank()
        );

        player.sendMessage(
                lang.forPlayer(player).message(
                        "progression.meow-power",
                        cat.getName(),
                        String.valueOf(
                                amount
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

        player.sendMessage(
                lang.forPlayer(player).message(
                        "progression.meow-rank-up",
                        cat.getName(),
                        String.valueOf(
                                cat.getMeowRank()
                        )
                )
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP,
                1.0f,
                1.2f
        );

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

        Bukkit.getPluginManager()
                .callEvent(
                        new CatMeowRankUpEvent(
                                player,
                                cat,
                                fromRank,
                                cat.getMeowRank()
                        )
                );

        syncSkillSlots(player, cat);
    }

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
                        lang.forPlayer(player).message(
                                "progression.new-skill",
                                cat.getName(),
                                lang.forPlayer(player).text(
                                        "skill-name."
                                                + rolled.name()
                                                .toLowerCase(
                                                        java.util.Locale.ROOT
                                                )
                                )
                        )
                );
            }
        }

        if (cat.getSkills().size() > current) {

            persistSkills(player.getUniqueId(), cat);
        }
    }

    private CatSkill rollSkillForSlot(
            Cat cat,
            int slotIndex
    ) {

        List<CatSkill> pool =
                buildCandidatePool(
                        cat,
                        cat.isDreamSlot(slotIndex)
                );

        return weightedSkillRoll(pool);
    }

    /*
     * 构建"刷新候选池"：
     * 该槽位允许的品质池，剔除猫已拥有的技能。
     *
     * 梦槽 = 精确梦幻池；其余槽位 = 品质上限内的全池。
     * 抽取与刷新共用同一实现，杜绝两处逻辑漂移。
     */
    private List<CatSkill> buildCandidatePool(
            Cat cat,
            boolean dreamSlot
    ) {

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

        return pool;
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
                    lang.forPlayer(player).message(
                            "progression.refresh-cant-afford",
                            skillManager.getRefreshCostDisplay(
                                    lang.forPlayer(player),
                                    dreamSlot
                            )
                    )
            );

            return false;
        }

        CatSkill oldSkill =
                currentSkills.get(slotIndex);

        List<CatSkill> pool =
                buildCandidatePool(
                        cat,
                        dreamSlot
                );

        if (pool.isEmpty()) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "progression.refresh-no-pool"
                    )
            );

            return false;
        }

        /*
         * 经济安全不变量（先掷骰、后扣费、再写入）：
         * 在产生任何经济代价之前，必须先确定新的
         * 技能一定存在。roll 是纯函数，不会修改任何状态。
         */
        CatSkill newSkill =
                weightedSkillRoll(pool);

        if (newSkill == null) {

            /*
             * 防御分支：当前权重全为正，非空池理论上不可能
             * 返回 null；若未来权重配置允许 0 权重，
             * 这里必须在扣费前拦截，绝不产生"付费无结果"。
             */
            player.sendMessage(
                    lang.forPlayer(player).message(
                            "progression.refresh-no-pool"
                    )
            );

            return false;
        }

        if (!provider.charge(player, cost)) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "progression.refresh-charge-fail",
                            lang.forPlayer(player).text(
                                    skillManager.costDisplayKey()
                            )
                    )
            );

            return false;
        }

        /*
         * 写入防御：CatSkills.set 拒绝与既有槽位重复的技能；
         * 新技能来自"已剔除已拥有技能"的候选池，
         * 因此这里必然写入成功。
         * 若极端情况下写入被拒绝（防御），回退扣费，
         * 保证"付费必有结果、无结果必不付费"。
         */
        cat.setSkillAt(slotIndex, newSkill);

        if (cat.getSkills().get(slotIndex)
                != newSkill) {

            /*
             * 理论不可达：候选池已剔除已拥有技能，
             * CatSkills.set 必然写入成功。
             * 万一未来规则变化导致写入被拒，
             * 必须把扣费退回，保证"无结果必不付费"。
             */
            provider.refund(
                    player,
                    cost
            );

            return false;
        }

        persistSkills(player.getUniqueId(), cat);

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
                lang.forPlayer(player).message(
                        "progression.refresh-done",
                        lang.forPlayer(player).text(
                                "skill-name."
                                        + oldSkill.name()
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                        ),
                        lang.forPlayer(player).text(
                                "skill-name."
                                        + newSkill.name()
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                        )
                )
        );

        return true;
    }

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

    /*
     * 猫最大生命随等级成长：10 + 等级/4。
     * 升级且实体在线时刷新；
     * 离线时由 CatEntityService.updateCat 在绑定/恢复时补刷。
     */
    private void applyLevelMaxHealth(
            Player player,
            Cat cat
    ) {

        UUID entityUuid =
                cat.getEntityUuid();

        if (entityUuid == null) {
            return;
        }

        Entity entity =
                Bukkit.getEntity(
                        entityUuid
                );

        if (!(entity instanceof org.bukkit.entity.LivingEntity living) ||
                !living.isValid()) {

            return;
        }

        AttributeInstance attribute =
                living.getAttribute(
                        Attribute.MAX_HEALTH
                );

        if (attribute == null) {
            return;
        }

        double scaled =
                10.0 + cat.getLevel() / 4.0;

        if (Math.abs(
                attribute.getBaseValue()
                        - scaled
        ) > 0.01) {

            attribute.setBaseValue(
                    scaled
            );

            living.setHealth(
                    Math.min(
                            living.getHealth(),
                            scaled
                    )
            );
        }
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
