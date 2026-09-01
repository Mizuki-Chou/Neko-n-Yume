package mizukichou.nekonyume.cat;

import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.event.CatFedEvent;
import mizukichou.nekonyume.event.CatTierUpgradeEvent;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 猫咪食物与喵丹管理。
 *
 * <p>
 * plugin 仅用于 NamespacedKey；
 * 数值全部来自 ConfigManager 快照；
 * 玩家文案全部来自 Lang。
 * </p>
 *
 * <p>
 * 喵丹：PDC 品质 + 批次；批次不匹配即过期。
 * 0.6.2：吃喵丹有概率提升底蕴。
 * </p>
 */
public class CatFoodManager {

    private static final int MAX_HUNGER = 100;

    private static final int MEOW_DAN_MAX_STACK = 64;

    private final Random random =
            new Random();

    private final Map<Material, Integer> foodValues =
            new EnumMap<>(Material.class);

    private final CatEntityRuntime runtime;
    private final String namespace;
    private final CatStore store;
    private final CatCache cache;
    private final ConfigManager configManager;
    private final CatProgressionService progression;
    private final CatEntityService entityService;
    private final Lang lang;

    /*
     * 含 § 色码的文本（喵丹名）进聊天组件前
     * 必须经 LegacyComponentSerializer 转换，
     * 避免 LegacyFormattingDetected 警告。
     */
    private final LegacyComponentSerializer legacySerializer =
            LegacyComponentSerializer.legacySection();

    private final NamespacedKey meowDanKey;
    private final NamespacedKey meowDanGenKey;

    /*
     * 0.7.4：经验丸 PDC 身份键（值 = XpPillTier.id）。
     */
    private final NamespacedKey xpPillKey;

    /*
     * 0.8.0：装备 PDC 身份键（值 = CatEquipItem.code）。
     */
    private final NamespacedKey equipKey;

    /*
     * 0.8.0：装备附加属性 PDC 键（值 = EquipBonusAttribute.code）。
     */
    private final NamespacedKey equipBonusKey;

    /*
     * 0.8.0：猫猫装备袋 PDC 身份键（值 = BYTE 1）。
     */
    private final NamespacedKey equipBagKey;

    public CatFoodManager(
            CatEntityRuntime runtime,
            String namespace,
            CatStore store,
            CatCache cache,
            ConfigManager configManager,
            CatProgressionService progression,
            CatEntityService entityService,
            Lang lang
    ) {

        this.runtime = runtime;
        this.namespace = namespace;
        this.store = store;
        this.cache = cache;
        this.configManager = configManager;
        this.progression = progression;
        this.entityService = entityService;
        this.lang = lang;

        this.meowDanKey =
                new NamespacedKey(
                        namespace,
                        "nekonyume_meowdan"
                );

        this.meowDanGenKey =
                new NamespacedKey(
                        namespace,
                        "nekonyume_meowdan_gen"
                );

        this.xpPillKey =
                new NamespacedKey(
                        namespace,
                        "nekonyume_xp_pill"
                );

        this.equipKey =
                new NamespacedKey(
                        namespace,
                        "nekonyume_equip"
                );

        this.equipBonusKey =
                new NamespacedKey(
                        namespace,
                        "nekonyume_equip_bonus"
                );

        this.equipBagKey =
                new NamespacedKey(
                        namespace,
                        "nekonyume_equip_bag"
                );

        registerFoods();
    }

    private void registerFoods() {

        foodValues.putAll(
                configManager.snapshot()
                        .getFood()
                        .getValues()
        );
    }

    public void reloadFoods() {

        foodValues.clear();

        registerFoods();
    }

    public boolean isFood(
            ItemStack item
    ) {

        if (item == null) {
            return false;
        }

        if (item.getType().isAir()) {
            return false;
        }

        return foodValues.containsKey(
                item.getType()
        );
    }

    public int getFoodValue(
            ItemStack item
    ) {

        if (!isFood(item)) {
            return 0;
        }

        return foodValues.getOrDefault(
                item.getType(),
                0
        );
    }

    public Map<Material, Integer> getFoodValues() {

        return Collections.unmodifiableMap(
                foodValues
        );
    }

    public NamespacedKey getMeowDanKey() {
        return meowDanKey;
    }

    /*
     * 品质顺序（与枚举声明顺序无关）：
     * 平凡 → 精良 → 独特 → 卓越 → 至极。
     */
    public static java.util.List<MeowDanQuality>
    orderedQualities() {

        java.util.List<MeowDanQuality> list =
                new java.util.ArrayList<>(
                        java.util.List.of(
                                MeowDanQuality.values()
                        )
                );

        list.sort(
                java.util.Comparator
                        .comparingInt(
                                MeowDanQuality::getMeowPowerGain
                        )
                        .thenComparingInt(
                                MeowDanQuality::getXpGain
                        )
        );

        return list;
    }

    public ItemStack createMeowDan(
            MeowDanQuality quality,
            int amount,
            Player player
    ) {

        if (quality == null) {
            quality = MeowDanQuality.COMMON;
        }

        int safeAmount =
                Math.max(
                        1,
                        Math.min(
                                amount,
                                MEOW_DAN_MAX_STACK
                        )
                );

        ItemStack item =
                new ItemStack(
                        Material.GOLD_NUGGET,
                        safeAmount
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            meta.setDisplayName(
                    lang.forPlayer(player).text(
                            "meowdan-name."
                                    + quality.name()
                                    .toLowerCase(
                                            java.util.Locale.ROOT
                                    )
                    )
            );

            meta.setLore(
                    Arrays.asList(
                            quality.getColorCode()
                                    + lang.forPlayer(player).text(
                                    "feed.meowdan-lore-use"
                            ),
                            quality.getColorCode()
                                    + lang.forPlayer(player).text(
                                    "feed.meowdan-lore-values",
                                    String.valueOf(
                                            quality.getMeowPowerGain()
                                    ),
                                    String.valueOf(
                                            quality.getAffectionGain()
                                    ),
                                    String.valueOf(
                                            quality.getXpGain()
                                    )
                            )
                    )
            );

            meta.setCustomModelData(
                    configManager.snapshot()
                            .getItems()
                            .meowdanCustomModelData(
                                    quality
                            )
            );

            meta.getPersistentDataContainer()
                    .set(
                            meowDanKey,
                            PersistentDataType.STRING,
                            quality.name()
                    );

            meta.getPersistentDataContainer()
                    .set(
                            meowDanGenKey,
                            PersistentDataType.INTEGER,
                            currentMeowDanGeneration()
                    );

            item.setItemMeta(
                    meta
            );
        }

        return item;
    }

    public boolean isMeowDan(
            ItemStack item
    ) {

        if (item == null ||
                item.getType().isAir()) {

            return false;
        }

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return false;
        }

        if (!meta.getPersistentDataContainer()
                .has(
                        meowDanKey,
                        PersistentDataType.STRING
                )) {

            return false;
        }

        Integer generation =
                meta.getPersistentDataContainer()
                        .get(
                                meowDanGenKey,
                                PersistentDataType.INTEGER
                        );

        return generation != null &&
                generation == currentMeowDanGeneration();
    }

    public boolean isLegacyMeowDan(
            ItemStack item
    ) {

        if (item == null ||
                item.getType().isAir()) {

            return false;
        }

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer()
                .has(
                        meowDanKey,
                        PersistentDataType.STRING
                ) &&
                !isMeowDan(item);
    }

    private int currentMeowDanGeneration() {

        return configManager.snapshot()
                .getItems()
                .getMeowdanGeneration();
    }

    public MeowDanQuality getMeowDanQuality(
            ItemStack item
    ) {

        if (!isMeowDan(item)) {
            return null;
        }

        String qualityName =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(
                                meowDanKey,
                                PersistentDataType.STRING
                        );

        if (qualityName == null) {
            return null;
        }

        for (MeowDanQuality quality :
                MeowDanQuality.values()) {

            if (quality.name()
                    .equalsIgnoreCase(qualityName)) {

                return quality;
            }
        }

        return null;
    }

    public boolean feedMeowDan(
            Player player,
            ItemStack item
    ) {

        if (player == null ||
                item == null) {

            return false;
        }

        MeowDanQuality quality =
                getMeowDanQuality(
                        item
                );

        if (quality == null) {
            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        if (!store.hasCat(playerUUID)) {
            return false;
        }

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return false;
        }

        int oldAffection =
                cat.getAffection();

        cat.addAffection(
                quality.getAffectionGain()
        );

        int actualAffectionGain =
                cat.getAffection()
                        - oldAffection;

        /*
         * 0.8.4 R18（社区上报 M-03）：
         * 消耗先行（与经验丸统一）——发放链路触发可重入
         * 事件前先扣除物品，杜绝"状态已加、物品未扣"。
         */
        if (player.getGameMode() != GameMode.CREATIVE) {

            if (item.getAmount() <= 1) {

                item.setAmount(0);

            } else {

                item.setAmount(
                        item.getAmount() - 1
                );
            }
        }

        store.setCatAffection(
                playerUUID,
                cat.getAffection()
        );

        progression.gainExperience(
                player,
                cat,
                quality.getXpGain()
        );

        progression.grantMeowPower(
                player,
                cat,
                quality.getMeowPowerGain()
        );

        runtime.playSound(


                player.getLocation(),


                "eat",


                1.0f,


                1.0f


        );

        player.sendMessage(
                lang.forPlayer(player).messageComponents(
                        "feed.meowdan-eat",
                        Component.text(
                                cat.getName()
                        ),
                        legacySerializer.deserialize(
                                lang.forPlayer(player).text(
                                        "meowdan-name."
                                                + quality.name()
                                                .toLowerCase(
                                                        java.util.Locale.ROOT
                                                )
                                )
                        )
                )
        );

        if (actualAffectionGain > 0) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "feed.meowdan-affection",
                            String.valueOf(
                                    actualAffectionGain
                            ),
                            String.valueOf(
                                    cat.getAffection()
                            )
                    )
            );
        }

        /*
         * 底蕴升阶判定（0.6.2）。
         */
        tryTierUpgrade(
                player,
                cat,
                quality,
                playerUUID
        );

        return true;
    }

    /*
     * ============================================================
     * 经验丸（0.7.4）
     * ============================================================
     *
     * 初阶 / 高阶两档，喂给猫咪直接增加经验。
     * 不影响饱食与好感（补充剂，不是食物）。
     * 物品身份：PDC nekonyume_xp_pill = tier.id。
     * 高阶额外带附魔光泽（setEnchantmentGlintOverride）。
     */

    public NamespacedKey getXpPillKey() {
        return xpPillKey;
    }

    public ItemStack createXpPill(
            XpPillTier tier,
            int amount,
            Player player
    ) {

        if (tier == null) {
            tier = XpPillTier.NORMAL;
        }

        int safeAmount =
                Math.max(
                        1,
                        Math.min(
                                amount,
                                MEOW_DAN_MAX_STACK
                        )
                );

        ItemStack item =
                new ItemStack(
                        Material.EXPERIENCE_BOTTLE,
                        safeAmount
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            int xp =
                    xpFor(
                            tier
                    );

            meta.setDisplayName(
                    lang.forPlayer(player).text(
                            "item.xp-pill."
                                    + tier.getId()
                                    + "-name"
                    )
            );

            meta.setLore(
                    Arrays.asList(
                            lang.forPlayer(player).text(
                                    "item.xp-pill.lore",
                                    String.valueOf(
                                            xp
                                    )
                            )
                    )
            );

            if (tier == XpPillTier.ELITE) {

                meta.setEnchantmentGlintOverride(
                        true
                );
            }

            meta.getPersistentDataContainer()
                    .set(
                            xpPillKey,
                            PersistentDataType.STRING,
                            tier.getId()
                    );

            item.setItemMeta(
                    meta
            );
        }

        return item;
    }

    public boolean isXpPill(
            ItemStack item
    ) {

        return getXpPillTier(
                item
        ) != null;
    }

    public XpPillTier getXpPillTier(
            ItemStack item
    ) {

        if (item == null ||
                item.getType().isAir()) {

            return null;
        }

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return null;
        }

        String id =
                meta.getPersistentDataContainer()
                        .get(
                                xpPillKey,
                                PersistentDataType.STRING
                        );

        return XpPillTier.fromId(
                id
        );
    }

    public int xpFor(
            XpPillTier tier
    ) {

        if (tier == null) {
            return 0;
        }

        ConfigSnapshot.XpPill config =
                configManager.snapshot()
                        .getXpPill();

        return tier == XpPillTier.ELITE
                ? config.getEliteXp()
                : config.getNormalXp();
    }

    /*
     * ============================================================
     * 装备（0.8.0）
     * ============================================================
     */

    public ItemStack createEquipment(
            CatEquipItem equip,
            int amount,
            Player player
    ) {

        if (equip == null ||
                amount <= 0) {

            throw new IllegalArgumentException(
                    "equip == null || amount <= 0"
            );
        }

        int safeAmount =
                Math.min(
                        amount,
                        64
                );

        ItemStack item =
                new ItemStack(
                        equip.getType().getMaterial(),
                        safeAmount
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return item;
        }

        String name =
                lang.forPlayer(player).text(
                        equip.getLangKey()
                );

        meta.setDisplayName(
                equip.getQuality().getColorCode()
                        + "✨ "
                        + name
        );

        java.util.List<String> lore =
                new java.util.ArrayList<>();

        lore.add(
                lang.forPlayer(player).text(
                        "equip-lore."
                                + equip.getType().getId()
                )
        );

        if (equip.getDamageBonus() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.damage",
                            String.valueOf(
                                    equip.getDamageBonus()
                            )
                    )
            );
        }

        if (equip.getAuraBonus() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.aura",
                            String.valueOf(
                                    equip.getAuraBonus()
                            )
                    )
            );
        }

        if (equip.getMeowBonus() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.meow",
                            String.valueOf(
                                    equip.getMeowBonus()
                            )
                    )
            );
        }

        if (equip.getCatHealthBonus() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.health",
                            String.valueOf(
                                    equip.getCatHealthBonus()
                            )
                    )
            );
        }

        if (equip.getDamageReductionPercent() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.reduction",
                            String.valueOf(
                                    equip.getDamageReductionPercent()
                            )
                    )
            );
        }

        if (equip.getLifestealPercent() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.lifesteal",
                            String.valueOf(
                                    equip.getLifestealPercent()
                            )
                    )
            );
        }

        if (equip.isAuraSpeed()) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.aura-speed"
                    )
            );
        }

        if (equip.getFeedAffectionBonus() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.feed-affection",
                            String.valueOf(
                                    equip.getFeedAffectionBonus()
                            )
                    )
            );
        }

        if (equip.getHungerSlowPercent() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.hunger-slow",
                            String.valueOf(
                                    equip.getHungerSlowPercent()
                            )
                    )
            );
        }

        if (equip.getXpBonusPercent() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.xp",
                            String.valueOf(
                                    equip.getXpBonusPercent()
                            )
                    )
            );
        }

        if (equip.getCooldownReductionPercent() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.cooldown",
                            String.valueOf(
                                    equip.getCooldownReductionPercent()
                            )
                    )
            );
        }

        if (equip.getAttackIntervalReductionTicks() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.attack-speed",
                            String.valueOf(
                                    equip.getAttackIntervalReductionTicks()
                            )
                    )
            );
        }

        if (equip.getAffectionDecayReduce() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.decay-reduce",
                            String.valueOf(
                                    equip.getAffectionDecayReduce()
                            )
                    )
            );
        }

        if (equip.getRegenBoostPercent() > 0) {

            lore.add(
                    lang.forPlayer(player).text(
                            "equip-lore.regen",
                            String.valueOf(
                                    equip.getRegenBoostPercent()
                            )
                    )
            );
        }

        meta.setLore(
                lore
        );

        meta.setCustomModelData(
                equip.getCustomModelData()
        );

        meta.getPersistentDataContainer()
                .set(
                        equipKey,
                        PersistentDataType.STRING,
                        equip.getCode()
                );

        item.setItemMeta(
                meta
        );

        return item;
    }

    public boolean isEquipment(
            ItemStack item
    ) {

        return getEquipment(
                item
        ) != null;
    }

    public CatEquipItem getEquipment(
            ItemStack item
    ) {

        if (item == null ||
                item.getType().isAir()) {

            return null;
        }

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return null;
        }

        String code =
                meta.getPersistentDataContainer()
                        .get(
                                equipKey,
                                PersistentDataType.STRING
                        );

        return CatEquipItem.fromCode(
                code
        );
    }

    /*
     * 读取物品的附加属性；未知/空返回 null。
     */
    public EquipBonusAttribute getEquipmentBonus(
            ItemStack item
    ) {

        if (item == null ||
                item.getType().isAir()) {

            return null;
        }

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return null;
        }

        String code =
                meta.getPersistentDataContainer()
                        .get(
                                equipBonusKey,
                                PersistentDataType.STRING
                        );

        return EquipBonusAttribute.fromCode(
                code
        );
    }

    /*
     * 给物品附上附加属性：炫彩色 lore 行 + PDC 身份。
     *
     * 只追加、不重roll——与觉醒（roll）严格分离：
     * 展示/归还路径可以安全复用，不会误触随机。
     */
    public void applyBonusAttribute(
            ItemStack item,
            EquipBonusAttribute bonus,
            Player player
    ) {

        if (item == null ||
                bonus == null ||
                player == null) {

            return;
        }

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return;
        }

        java.util.List<String> lore =
                meta.getLore() == null
                        ? new java.util.ArrayList<>()
                        : new java.util.ArrayList<>(
                        meta.getLore()
                );

        lore.add("");

        lore.add(
                EquipBonusAttribute.rainbow(
                        "✦ "
                                + lang.forPlayer(player).text(
                                bonus.getLangKey()
                        )
                                + "："
                                + lang.forPlayer(player).text(
                                bonus.getDescKey(),
                                String.valueOf(
                                        bonus.getDisplayValue()
                                )
                        )
                )
        );

        meta.setLore(
                lore
        );

        meta.getPersistentDataContainer()
                .set(
                        equipBonusKey,
                        PersistentDataType.STRING,
                        bonus.getCode()
                );

        item.setItemMeta(
                meta
        );
    }

    /*
     * 获取途径专用：生成一件装备并觉醒 roll。
     *
     * 至极品质有 ROLL_PERCENT 概率携带附加属性；
     * 其余品质永不携带。
     * 所有未来获取途径（掉落、任务、礼包等）都应走本方法，
     * 保证“获得的一瞬间”语义统一（拾取物品不会重roll）。
     */
    public ItemStack createAcquisition(
            CatEquipItem equip,
            Player player
    ) {

        ItemStack item =
                createEquipment(
                        equip,
                        1,
                        player
                );

        if (equip.getQuality()
                == MeowDanQuality.LEGENDARY) {

            EquipBonusAttribute bonus =
                    EquipBonusAttribute.roll(
                            random
                    );

            if (bonus != null) {

                applyBonusAttribute(
                        item,
                        bonus,
                        player
                );
            }
        }

        return item;
    }

    /*
     * 归还路径专用：按已存档的附加属性重建装备（不重roll）。
     */
    public ItemStack createEquippedReturn(
            CatEquipItem equip,
            EquipBonusAttribute bonus,
            Player player
    ) {

        ItemStack item =
                createEquipment(
                        equip,
                        1,
                        player
                );

        if (bonus != null) {

            applyBonusAttribute(
                    item,
                    bonus,
                    player
            );
        }

        return item;
    }

    /*
     * 发放装备（背包优先，满则掉落在脚边）。
     * 觉醒 roll 在本方法内发生；返回实际发放的物品。
     */
    public ItemStack grantEquipment(
            Player player,
            CatEquipItem equip
    ) {

        if (player == null ||
                equip == null) {

            throw new IllegalArgumentException(
                    "player == null || equip == null"
            );
        }

        ItemStack item =
                createAcquisition(
                        equip,
                        player
                );

        java.util.Map<Integer, ItemStack> left =
                player.getInventory()
                        .addItem(
                                item
                        );

        if (!left.isEmpty()) {

            for (ItemStack rest :
                    left.values()) {

                player.getWorld()
                        .dropItemNaturally(
                                player.getLocation(),
                                rest
                        );
            }
        }

        return item;
    }

    /*
     * ============================================================
     * 猫猫装备袋（0.8.0 梦魔之夜）
     * ============================================================
     *
     * 掉落概率见 config.yml 的 drops 节
     * （梦魔夜 drops.muma-night.equip-bag-chance 默认 0.02，
     * 平时 drops.general.equip-bag-chance 默认 0）；
     * 右键打开自动抽取一件装备（品质 40/30/20/7.5/2.5）。
     * 物品身份：PDC nekonyume_equip_bag = BYTE 1。
     */

    public static final int EQUIP_BAG_MODEL_DATA = 92050;

    public ItemStack createEquipBag(
            int amount,
            Player player
    ) {

        int safeAmount =
                Math.max(
                        1,
                        Math.min(
                                amount,
                                64
                        )
                );

        ItemStack item =
                new ItemStack(
                        Material.BUNDLE,
                        safeAmount
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            meta.setDisplayName(
                    "§d✨ "
                            + lang.forPlayer(player).text(
                            "equip-bag.name"
                    )
            );

            meta.setLore(
                    Arrays.asList(
                            lang.forPlayer(player).text(
                                    "equip-bag.lore-1"
                            ),
                            lang.forPlayer(player).text(
                                    "equip-bag.lore-2"
                            )
                    )
            );

            meta.setCustomModelData(
                    EQUIP_BAG_MODEL_DATA
            );

            meta.getPersistentDataContainer()
                    .set(
                            equipBagKey,
                            PersistentDataType.BYTE,
                            (byte) 1
                    );

            item.setItemMeta(
                    meta
            );
        }

        return item;
    }

    public boolean isEquipBag(
            ItemStack item
    ) {

        if (item == null ||
                !item.hasItemMeta()) {

            return false;
        }

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(
                        equipBagKey,
                        PersistentDataType.BYTE
                );
    }

    /*
     * 觉醒播报（彩虹“至宝降世”）：发放路径共用。
     */
    public void announceEquipBonus(
            Player player,
            EquipBonusAttribute bonus
    ) {

        if (player == null ||
                bonus == null) {

            return;
        }

        player.sendMessage(
                legacySerializer.deserialize(
                        EquipBonusAttribute.rainbow(
                                lang.forPlayer(player).text(
                                        "equip-bonus.obtained",
                                        lang.forPlayer(player).text(
                                                bonus.getLangKey()
                                        )
                                )
                        )
                )
        );
    }

    /*
     * 穿戴装备（0.8.0）：唯一装备位，替换时旧装备归还玩家。
     * 返回是否成功（物品已消耗 / 装备已生效）。
     */
    public boolean equipCat(
            Player player,
            org.bukkit.entity.Cat entity,
            CatEquipItem equip
    ) {

        /*
         * 0.8.4 R18（社区上报 M-NEW-03）：
         * 快照语义：判定与消费围绕同一个 ItemStack 引用。
         */
        return equipCat(
                player,
                entity,
                equip,
                player.getInventory()
                        .getItemInMainHand()
        );
    }

    public boolean equipCat(
            Player player,
            org.bukkit.entity.Cat entity,
            CatEquipItem equip,
            ItemStack itemSnapshot
    ) {

        if (player == null ||
                entity == null ||
                equip == null ||
                itemSnapshot == null) {

            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        if (!store.hasCat(playerUUID)) {
            return false;
        }

        Cat cat =
                cache.loadCat(
                        player
                );

        if (cat == null) {
            return false;
        }

        /*
         * 附加属性：从判定时的同一快照读取（PDC），与装备位绑定。
         */
        EquipBonusAttribute bonus =
                getEquipmentBonus(
                        itemSnapshot
                );

        if (!applyEquipCore(
                player,
                playerUUID,
                cat,
                equip,
                bonus
        )) {

            return false;
        }

        /*
         * 消耗手持物品（创造模式不消耗）。
         */
        if (player.getGameMode()
                != GameMode.CREATIVE) {

            if (!itemSnapshot.getType().isAir()) {

                itemSnapshot.setAmount(
                        itemSnapshot.getAmount() <= 1
                                ? 0
                                : itemSnapshot.getAmount() - 1
                );
            }
        }

        /*
         * 刷新战斗相关状态（最大生命）。
         */
        entityService.refreshEquipStats(
                player,
                cat,
                entity
        );

        return true;
    }

    /*
     * 装备界面快捷穿戴（0.8.0）：
     * 点击背包任意槽位中的装备直接穿上。
     *
     * <p>
     * source 为被点击的背包槽位物品（活引用），
     * 穿戴成功后扣减 1（创造模式不扣）。
     * 实体刷新由调用方（装备界面）负责。
     * </p>
     */
    public boolean equipFromStack(
            Player player,
            Cat cat,
            CatEquipItem equip,
            ItemStack source
    ) {

        if (player == null ||
                cat == null ||
                equip == null ||
                source == null ||
                source.getType().isAir()) {

            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        if (!store.hasCat(playerUUID)) {
            return false;
        }

        EquipBonusAttribute bonus =
                getEquipmentBonus(
                        source
                );

        if (!applyEquipCore(
                player,
                playerUUID,
                cat,
                equip,
                bonus
        )) {

            return false;
        }

        if (player.getGameMode()
                != GameMode.CREATIVE) {

            source.setAmount(
                    source.getAmount() <= 1
                            ? 0
                            : source.getAmount() - 1
            );
        }

        return true;
    }

    /*
     * 共用穿戴核心（0.8.0）：
     * 旧装备捕获、同名同属性拦截、状态写入、旧装备归还与提示。
     *
     * <p>
     * 不负责：物品消耗、实体刷新（调用方各司其职）。
     * </p>
     */
    private boolean applyEquipCore(
            Player player,
            UUID playerUUID,
            Cat cat,
            CatEquipItem equip,
            EquipBonusAttribute bonus
    ) {

        CatEquipItem old =
                cat.getEquippedItem();

        EquipBonusAttribute oldBonus =
                cat.getEquippedBonus();

        if (old == equip &&
                java.util.Objects.equals(
                        oldBonus,
                        bonus
                )) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "equip.same",
                            lang.forPlayer(player).text(
                                    equip.getLangKey()
                            )
                    )
            );

            return false;
        }

        cat.setEquippedItem(
                equip
        );

        cat.setEquippedBonus(
                bonus
        );

        store.setCatEquipment(
                playerUUID,
                equip.getCode()
        );

        store.setCatEquipmentBonus(
                playerUUID,
                bonus == null
                        ? ""
                        : bonus.getCode()
        );

        if (old != null) {

            giveOrDrop(
                    player,
                    createEquippedReturn(
                            old,
                            oldBonus,
                            player
                    )
            );

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "equip.replaced",
                            lang.forPlayer(player).text(
                                    old.getLangKey()
                            )
                    )
            );
        }

        player.sendMessage(
                lang.forPlayer(player).message(
                        "equip.done",
                        lang.forPlayer(player).text(
                                equip.getLangKey()
                        )
                )
        );

        return true;
    }

    /*
     * 背包优先，满了掉在脚边。
     */
    private void giveOrDrop(
            Player player,
            ItemStack item
    ) {

        java.util.Map<Integer, ItemStack> left =
                player.getInventory()
                        .addItem(
                                item
                        );

        if (left.isEmpty()) {
            return;
        }

        for (ItemStack rest :
                left.values()) {

            player.getWorld()
                    .dropItemNaturally(
                            player.getLocation(),
                            rest
                    );
        }
    }

    /**
     * 喂食经验丸：只加经验，不动饱食 / 好感。
     * 返回是否成功（物品已消耗 / 经验已发放）。
     */
    public boolean feedXpPill(
            Player player,
            ItemStack item
    ) {

        if (player == null ||
                item == null) {

            return false;
        }

        XpPillTier tier =
                getXpPillTier(
                        item
                );

        if (tier == null) {
            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        if (!store.hasCat(playerUUID)) {
            return false;
        }

        Cat cat =
                cache.loadCat(
                        player
                );

        if (cat == null) {
            return false;
        }

        int xp =
                xpFor(
                        tier
                );

        if (xp <= 0) {
            return false;
        }

        /*
         * 0.8.4 R18（社区上报 M-03）：
         * 消耗先行——gainExperience 内部会触发可重入事件，
         * 若物品在经验发放之后才消耗，事件监听器抛异常时
         * 会出现"经验已加、物品未扣"的重复利用窗口。
         */
        if (player.getGameMode() != GameMode.CREATIVE) {

            if (item.getAmount() <= 1) {

                item.setAmount(
                        0
                );

            } else {

                item.setAmount(
                        item.getAmount() - 1
                );
            }
        }

        progression.gainExperience(
                player,
                cat,
                xp
        );

        runtime.playSound(


                player.getLocation(),


                "eat",


                1.0f,


                1.0f


        );

        /*
         * 猫名 / 经验数值为纯文本参数，经 Lang 占位符包装；
         * 经验丸名称含 § 色码，进聊天组件前必须经
         * LegacyComponentSerializer 转换（与喵丹路径一致），
         * 避免 LegacyFormattingDetected 与字面 § 泄漏。
         */
        player.sendMessage(
                lang.forPlayer(player).messageComponents(
                        "feed.xp-pill-eat",
                        Component.text(
                                cat.getName()
                        ),
                        legacySerializer.deserialize(
                                lang.forPlayer(player).text(
                                        "item.xp-pill."
                                                + tier.getId()
                                                + "-name"
                                )
                        ),
                        Component.text(
                                String.valueOf(
                                        xp
                                )
                        )
                )
        );

        return true;
    }

    /*
     * ============================================================
     * 底蕴升阶（吃喵丹触发，0.6.2）
     * ============================================================
     *
     * 普通：卓越 50% → 稀有；至极 100% → 稀有
     * 稀有：卓越无效；至极 50% → 独特
     * 独特：至极 20% → 梦幻
     * 梦幻：封顶
     */
    private void tryTierUpgrade(
            Player player,
            Cat cat,
            MeowDanQuality quality,
            UUID playerUUID
    ) {

        java.util.List<MeowDanQuality> ordered =
                orderedQualities();

        int qualityIndex =
                ordered.indexOf(
                        quality
                );

        boolean isEpic =
                qualityIndex == 3;

        boolean isLegendary =
                qualityIndex >= 4;

        CatTier newTier = null;
        int chance = 0;

        switch (cat.getTier()) {

            case COMMON -> {

                if (isLegendary) {

                    newTier = CatTier.RARE;
                    chance = 100;

                } else if (isEpic) {

                    newTier = CatTier.RARE;
                    chance = 50;
                }
            }

            case RARE -> {

                if (isLegendary) {

                    newTier = CatTier.UNIQUE;
                    chance = 50;

                } else if (isEpic) {

                    /*
                     * 0.8.1 修复（R2）：
                     * “稀有：卓越无效”此前静默无反馈，
                     * 玩家会误以为吞了喵丹。现在明确提示。
                     */
                    notifyTierUpgradeInvalid(
                            player,
                            quality
                    );

                    return;
                }
            }

            case UNIQUE -> {

                if (isLegendary) {

                    newTier = CatTier.DREAM;
                    chance = 20;

                } else if (isEpic) {

                    notifyTierUpgradeInvalid(
                            player,
                            quality
                    );

                    return;
                }
            }

            case DREAM -> {
                return;
            }
        }

        if (newTier == null) {
            return;
        }

        boolean success =
                chance >= 100 ||
                        random.nextInt(100) < chance;

        if (!success) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "feed.tier-upgrade-fail"
                    )
            );

            return;
        }

        CatTier fromTier =
                cat.getTier();

        cat.setTier(
                newTier
        );

        store.setCatTier(
                playerUUID,
                newTier.name()
        );

        progression.syncSkillSlots(
                player,
                cat
        );

        player.sendMessage(
                lang.forPlayer(player).message(
                        "feed.tier-upgrade",
                        cat.getName(),
                        lang.forPlayer(player).text(
                                "tier-name."
                                        + newTier.name()
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                        )
                )
        );

        runtime.playSound(


                player.getLocation(),


                "toast",


                1.0f,


                1.0f
        );

        UUID entityUuid =
                cat.getEntityUuid();

        if (entityUuid != null) {

            Entity entity =
                    runtime.getEntity(
                            entityUuid


        );

            if (entity != null &&
                    entity.isValid()) {

                entity.getWorld()
                        .spawnParticle(
                                Particle.END_ROD,
                                entity.getLocation()
                                        .add(0, 1, 0),
                                50,
                                0.5,
                                0.5,
                                0.5,
                                0.05
                        );
            }
        }

        /*
         * 事后通知事件：成就系统等第三方监听。
         */
        runtime.callEvent(

                new CatTierUpgradeEvent(


                player,

                cat,

                fromTier,

                newTier

                )

        );
    }

    /*
     * 0.8.1 修复（R2）：卓越喵丹对当前底蕴无效的明确提示。
     */
    private void notifyTierUpgradeInvalid(
            Player player,
            MeowDanQuality quality
    ) {

        player.sendMessage(
                lang.forPlayer(player).message(
                        "feed.tier-upgrade-invalid",
                        lang.forPlayer(player).text(
                                "meowdan-name."
                                        + quality.name()
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                        )
                )
        );
    }

    /*
     * ============================================================
     * 喂猫
     * ============================================================
     */

    public boolean feedCat(
            Player player,
            ItemStack item
    ) {

        if (player == null ||
                item == null) {

            return false;
        }

        if (!isFood(item)) {
            return false;
        }

        UUID playerUUID =
                player.getUniqueId();

        if (!store.hasCat(playerUUID)) {
            return false;
        }

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return false;
        }

        int currentHunger =
                cat.getHunger();

        if (currentHunger >= MAX_HUNGER) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "feed.full"
                    )
            );

            return false;
        }

        int foodValue =
                getFoodValue(
                        item
                );

        if (foodValue <= 0) {
            return false;
        }

        CatPersonality personality =
                cat.getPersonality();

        int effectiveFoodValue =
                (int) Math.round(
                        foodValue
                                * personality
                                .getFoodValueMultiplier()
                );

        if (effectiveFoodValue <= 0) {
            return false;
        }

        int newHunger =
                Math.min(
                        MAX_HUNGER,
                        currentHunger
                                + effectiveFoodValue
                );

        int actualHungerGain =
                newHunger
                        - currentHunger;

        if (actualHungerGain <= 0) {
            return false;
        }

        cat.setHunger(
                newHunger
        );

        int oldAffection =
                cat.getAffection();

        ConfigSnapshot config =
                configManager.snapshot();

        ConfigSnapshot.Care care =
                config.getCare();

        /*
         * 羁绊纪元（0.8.0）：
         * 喂食好感改为饥饿相关：
         * 饥饿（低于 care.hungry-feed-threshold）时喂食获得“雪中送炭”高好感，
         * 非饥饿喂食只获得少量陪伴好感；性格加成保留。
         */
        boolean hungryFeed =
                currentHunger
                        < care.getHungryFeedThreshold();

        int baseAffection =
                hungryFeed
                        ? care.getFeedHungryAffection()
                        : care.getFeedNormalAffection();

        /*
         * 装备（0.8.0）：至极铃铛每次喂食额外好感。
         */
        CatEquipItem equip =
                cat.getEquippedItem();

        if (equip != null &&
                equip.getFeedAffectionBonus() > 0) {

            baseAffection +=
                    equip.getFeedAffectionBonus();
        }

        cat.addAffection(
                baseAffection
                        + personality
                        .getFeedAffectionBonus()
        );

        int actualAffectionGain =
                cat.getAffection()
                        - oldAffection;

        /*
         * 羁绊纪元（0.8.0）：喂食恢复逻辑健康。
         */
        int healthRestore =
                care.getFeedHealthRestore();

        if (healthRestore > 0) {

            cat.addHealth(
                    healthRestore
            );

            store.setCatHealth(
                    playerUUID,
                    cat.getHealth()
            );
        }

        cat.markFed();

        store.setCatHungerLastUpdate(
                playerUUID,
                System.currentTimeMillis()
        );

        store.setCatHunger(
                playerUUID,
                cat.getHunger()
        );

        store.setCatAffection(
                playerUUID,
                cat.getAffection()
        );

        store.setCatLastFedAt(
                playerUUID,
                cat.getLastFedAt()
        );

        /*
         * 0.8.0 修复：扣减前捕获食物快照。
         *
         * 物品扣减后数量可能归零（ItemStack 变为 AIR），
         * 若在扣减后再读取 getType()，"只剩1个食物"时
         * 喂食消息会显示为 AIR。事件参数同样改用快照，
         * 避免事后监听器拿到空气物品。
         */
        ItemStack fedSnapshot =
                item.clone();

        if (player.getGameMode() != GameMode.CREATIVE) {

            if (item.getAmount() <= 1) {

                item.setAmount(0);

            } else {

                item.setAmount(
                        item.getAmount() - 1
                );
            }
        }

        int xpGain =
                effectiveFoodValue;

        progression.gainExperience(
                player,
                cat,
                xpGain
        );

        int meowGain = 0;

        int feedCount =
                store.getCatFeedCount(
                        playerUUID
                );

        ConfigSnapshot.Meow meowConfig =
                config.getMeow();

        if (feedCount <
                meowConfig.getFeedChanceLimit()) {

            int chance =
                    meowConfig.getFeedChance()
                            + personality
                            .getFeedMeowChanceBonus();

            /*
             * 装备（0.8.0）：铃铛的喵力概率加成。
             * equip 变量已在好感段声明，此处直接复用。
             */
            if (equip != null) {

                chance +=
                        equip.getMeowBonus();
            }

            /*
             * 附加属性（0.8.0）：共鸣的喵力概率加成。
             */
            EquipBonusAttribute equipBonus =
                    cat.getEquippedBonus();

            if (equipBonus != null) {

                chance +=
                        equipBonus.getMeowBonus();
            }

            if (chance > 0 &&
                    random.nextInt(100) < chance) {

                /*
                 * 0.8.0 数值修正：
                 * 触发量由配置决定（meow.feed-gain，默认2），
                 * 与刷新费用形成合理比例。
                 */
                meowGain =
                        meowConfig.getFeedGain();

                progression.grantMeowPower(
                        player,
                        cat,
                        meowGain
                );
            }
        }

        store.addCatFeedCount(
                playerUUID
        );

        runtime.callEvent(


                new CatFedEvent(



                player,


                cat,


                fedSnapshot,


                actualHungerGain,


                actualAffectionGain,


                xpGain,


                meowGain


                )


        );

        player.sendMessage(
                lang.forPlayer(player).message(
                        "feed.ate-food",
                        cat.getName(),
                        lang.forPlayer(player).text(
                                "food-name."
                                        + fedSnapshot.getType()
                                        .name()
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                        )
                )
        );

        player.sendMessage(
                lang.forPlayer(player).message(
                        "feed.hunger-up",
                        String.valueOf(
                                actualHungerGain
                        ),
                        String.valueOf(
                                cat.getHunger()
                        )
                )
        );

        player.sendMessage(
                lang.forPlayer(player).message(
                        "feed.affection-up",
                        String.valueOf(
                                actualAffectionGain
                        ),
                        String.valueOf(
                                cat.getAffection()
                        )
                )
        );

        /*
         * 羁绊纪元（0.8.0）：雪中送炭提示。
         */
        if (hungryFeed) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "feed.hungry-bonus",
                            cat.getName()
                    )
            );

            player.getWorld()
                    .spawnParticle(
                            Particle.HEART,
                            player.getLocation()
                                    .add(0, 2, 0),
                            10,
                            0.4,
                            0.4,
                            0.4,
                            0.02
                    );
        }

        return true;
    }

}
