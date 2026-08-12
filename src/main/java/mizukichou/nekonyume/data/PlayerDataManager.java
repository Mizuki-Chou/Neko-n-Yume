package mizukichou.nekonyume.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerDataManager {

    private final File file;
    private final YamlConfiguration data;

    public PlayerDataManager(JavaPlugin plugin) {

        file = new File(
                plugin.getDataFolder(),
                "players.yml"
        );

        if (!file.exists()) {

            try {

                file.getParentFile().mkdirs();
                file.createNewFile();

            } catch (IOException e) {

                e.printStackTrace();
            }
        }

        data = YamlConfiguration.loadConfiguration(file);
    }

    /*
     * =========================
     * 猫咪基础数据
     * =========================
     */

    public boolean hasCat(UUID uuid) {

        return data.contains(
                "players." + uuid + ".cat"
        );
    }

    public void createCat(UUID uuid) {

        String path =
                "players." + uuid + ".cat";

        data.set(
                path + ".name",
                "Mikan"
        );

        data.set(
                path + ".level",
                1
        );

        data.set(
                path + ".affection",
                50
        );

        /*
         * 猫咪初始饱食度
         */
        data.set(
                path + ".hunger",
                100
        );

        /*
         * 饥饿度上次更新时间
         */
        data.set(
                path + ".hunger-last-update",
                System.currentTimeMillis()
        );

        /*
         * 每日抚摸次数
         */
        data.set(
                path + ".pet-count",
                0
        );

        /*
         * 每日抚摸日期
         */
        data.set(
                path + ".pet-date",
                LocalDate.now().toString()
        );

        save();
    }

    public String getCatName(UUID uuid) {

        return data.getString(
                "players." + uuid + ".cat.name",
                "Mikan"
        );
    }

    public void setCatName(
            UUID uuid,
            String name
    ) {

        data.set(
                "players." + uuid + ".cat.name",
                name
        );

        save();
    }

    public int getCatLevel(UUID uuid) {

        return data.getInt(
                "players." + uuid + ".cat.level",
                1
        );
    }

    public int getCatAffection(UUID uuid) {

        return data.getInt(
                "players." + uuid + ".cat.affection",
                50
        );
    }

    public void setCatAffection(
            UUID playerUUID,
            int affection
    ) {

        affection = Math.max(
                0,
                Math.min(
                        100,
                        affection
                )
        );

        data.set(
                "players." + playerUUID + ".cat.affection",
                affection
        );

        save();
    }

    public void addCatAffection(
            UUID playerUUID,
            int amount
    ) {

        int currentAffection =
                getCatAffection(playerUUID);

        setCatAffection(
                playerUUID,
                currentAffection + amount
        );
    }

    /*
     * =========================
     * 猫咪每日抚摸次数
     * =========================
     *
     * 每天最多 20 次
     */

    public int getCatPetCount(
            UUID playerUUID
    ) {

        resetPetCountIfNewDay(
                playerUUID
        );

        return data.getInt(
                "players." + playerUUID
                        + ".cat.pet-count",
                0
        );
    }

    public void addCatPetCount(
            UUID playerUUID
    ) {

        resetPetCountIfNewDay(
                playerUUID
        );

        String path =
                "players." + playerUUID
                        + ".cat.pet-count";

        int current =
                data.getInt(
                        path,
                        0
                );

        /*
         * 最大 20 次
         */
        if (current >= 20) {
            return;
        }

        data.set(
                path,
                current + 1
        );

        save();
    }

    public boolean canPetCat(
            UUID playerUUID
    ) {

        return getCatPetCount(
                playerUUID
        ) < 20;
    }

    public int getRemainingPetCount(
            UUID playerUUID
    ) {

        return Math.max(
                0,
                20 - getCatPetCount(
                        playerUUID
                )
        );
    }

    /*
     * 如果日期发生变化，
     * 自动重置每日抚摸次数
     */
    private void resetPetCountIfNewDay(
            UUID playerUUID
    ) {

        String datePath =
                "players." + playerUUID
                        + ".cat.pet-date";

        String countPath =
                "players." + playerUUID
                        + ".cat.pet-count";

        String today =
                LocalDate.now().toString();

        String savedDate =
                data.getString(
                        datePath
                );

        /*
         * 旧玩家没有每日抚摸数据
         */
        if (savedDate == null) {

            data.set(
                    datePath,
                    today
            );

            data.set(
                    countPath,
                    0
            );

            save();

            return;
        }

        /*
         * 新的一天
         */
        if (!savedDate.equals(today)) {

            data.set(
                    datePath,
                    today
            );

            data.set(
                    countPath,
                    0
            );

            save();
        }
    }

    /*
     * =========================
     * 猫咪饱食度
     * =========================
     *
     * 范围：
     * 0 ~ 100
     *
     * 100 = 完全饱腹
     * 0   = 极度饥饿
     *
     * 0 不会死亡。
     */

    public int getCatHunger(UUID playerUUID) {

        return data.getInt(
                "players." + playerUUID + ".cat.hunger",
                100
        );
    }

    public void setCatHunger(
            UUID playerUUID,
            int hunger
    ) {

        hunger = Math.max(
                0,
                Math.min(
                        100,
                        hunger
                )
        );

        data.set(
                "players." + playerUUID + ".cat.hunger",
                hunger
        );

        save();
    }

    public void addCatHunger(
            UUID playerUUID,
            int amount
    ) {

        int currentHunger =
                getCatHunger(playerUUID);

        setCatHunger(
                playerUUID,
                currentHunger + amount
        );
    }

    public boolean isCatHungry(
            UUID playerUUID
    ) {

        return getCatHunger(
                playerUUID
        ) <= 0;
    }

    public double getCatHungerPercent(
            UUID playerUUID
    ) {

        return getCatHunger(
                playerUUID
        ) / 100.0;
    }

    /*
     * =========================
     * 饥饿计时
     * =========================
     */

    public long getCatHungerLastUpdate(
            UUID playerUUID
    ) {

        String path =
                "players." + playerUUID
                        + ".cat.hunger-last-update";

        /*
         * 旧玩家没有这个数据
         */
        if (!data.contains(path)) {

            long now =
                    System.currentTimeMillis();

            data.set(
                    path,
                    now
            );

            save();

            return now;
        }

        return data.getLong(
                path
        );
    }

    public void setCatHungerLastUpdate(
            UUID playerUUID,
            long timestamp
    ) {

        data.set(
                "players." + playerUUID
                        + ".cat.hunger-last-update",
                timestamp
        );

        save();
    }

    /*
     * =========================
     * 获取所有拥有猫咪的玩家
     * =========================
     */

    public Set<UUID> getCatPlayers() {

        Set<UUID> players =
                new HashSet<>();

        if (!data.contains("players")) {
            return players;
        }

        var section =
                data.getConfigurationSection(
                        "players"
                );

        if (section == null) {
            return players;
        }

        for (String key :
                section.getKeys(false)) {

            try {

                UUID uuid =
                        UUID.fromString(key);

                if (hasCat(uuid)) {

                    players.add(uuid);
                }

            } catch (IllegalArgumentException ignored) {

                /*
                 * 无效 UUID 直接跳过
                 */
            }
        }

        return players;
    }

    /*
     * =========================
     * 猫咪花色
     * =========================
     */

    public String getCatVariant(
            UUID playerUUID
    ) {

        return data.getString(
                "players." + playerUUID
                        + ".cat.variant"
        );
    }

    public void setCatVariant(
            UUID playerUUID,
            String variant
    ) {

        data.set(
                "players." + playerUUID
                        + ".cat.variant",
                variant
        );

        save();
    }

    /*
     * =========================
     * 猫咪实体 UUID
     * =========================
     */

    public UUID getCatEntityUUID(
            UUID playerUUID
    ) {

        String value =
                data.getString(
                        "players." + playerUUID
                                + ".cat.entity-uuid"
                );

        if (value == null) {
            return null;
        }

        try {

            return UUID.fromString(value);

        } catch (IllegalArgumentException e) {

            return null;
        }
    }

    public void setCatEntityUUID(
            UUID playerUUID,
            UUID entityUUID
    ) {

        data.set(
                "players." + playerUUID
                        + ".cat.entity-uuid",
                entityUUID.toString()
        );

        save();
    }

    /*
     * =========================
     * 猫咪所在世界
     * =========================
     */

    public UUID getCatWorldUUID(
            UUID playerUUID
    ) {

        String value =
                data.getString(
                        "players." + playerUUID
                                + ".cat.world-uuid"
                );

        if (value == null) {
            return null;
        }

        try {

            return UUID.fromString(value);

        } catch (IllegalArgumentException e) {

            return null;
        }
    }

    public void setCatWorldUUID(
            UUID playerUUID,
            UUID worldUUID
    ) {

        data.set(
                "players." + playerUUID
                        + ".cat.world-uuid",
                worldUUID.toString()
        );

        save();
    }

    /*
     * =========================
     * 猫咪坐标
     * =========================
     */

    public double getCatX(
            UUID playerUUID
    ) {

        return data.getDouble(
                "players." + playerUUID + ".cat.x"
        );
    }

    public double getCatY(
            UUID playerUUID
    ) {

        return data.getDouble(
                "players." + playerUUID + ".cat.y"
        );
    }

    public double getCatZ(
            UUID playerUUID
    ) {

        return data.getDouble(
                "players." + playerUUID + ".cat.z"
        );
    }

    public void setCatLocation(
            UUID playerUUID,
            UUID worldUUID,
            double x,
            double y,
            double z
    ) {

        String path =
                "players." + playerUUID + ".cat";

        data.set(
                path + ".world-uuid",
                worldUUID.toString()
        );

        data.set(
                path + ".x",
                x
        );

        data.set(
                path + ".y",
                y
        );

        data.set(
                path + ".z",
                z
        );

        save();
    }

    /*
     * =========================
     * 保存
     * =========================
     */

    private void save() {

        try {

            data.save(file);

        } catch (IOException e) {

            e.printStackTrace();
        }
    }
}