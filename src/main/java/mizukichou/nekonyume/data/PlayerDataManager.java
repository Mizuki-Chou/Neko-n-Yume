package mizukichou.nekonyume.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlayerDataManager {

    private final File file;
    private final YamlConfiguration data;

    public String getCatName(UUID uuid) {

        return data.getString(
                "players." + uuid + ".cat.name"
        );
    }

    public void setCatName(UUID uuid, String name) {

        data.set(
                "players." + uuid + ".cat.name",
                name
        );

        save();
    }

    public int getCatLevel(UUID uuid) {

        return data.getInt(
                "players." + uuid + ".cat.level"
        );
    }

    public int getCatAffection(UUID uuid) {

        return data.getInt(
                "players." + uuid + ".cat.affection"
        );
    }

    public PlayerDataManager(JavaPlugin plugin) {

        file = new File(
                plugin.getDataFolder(),
                "players.yml"
        );

        if (!file.exists()) {

            try {

                file.createNewFile();

            } catch (IOException e) {

                e.printStackTrace();
            }
        }

        data = YamlConfiguration.loadConfiguration(file);
    }

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

        save();
    }

    /*
     * 获取猫咪实体 UUID
     */
    public UUID getCatEntityUUID(UUID playerUUID) {

        String value = data.getString(
                "players." + playerUUID + ".cat.entity-uuid"
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

    /*
     * 保存猫咪实体 UUID
     */
    public void setCatEntityUUID(
            UUID playerUUID,
            UUID entityUUID
    ) {

        data.set(
                "players." + playerUUID + ".cat.entity-uuid",
                entityUUID.toString()
        );

        save();
    }

    /*
     * 获取猫咪所在世界 UUID
     */
    public UUID getCatWorldUUID(UUID playerUUID) {

        String value = data.getString(
                "players." + playerUUID + ".cat.world-uuid"
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

    /*
     * 保存猫咪所在世界 UUID
     */
    public void setCatWorldUUID(
            UUID playerUUID,
            UUID worldUUID
    ) {

        data.set(
                "players." + playerUUID + ".cat.world-uuid",
                worldUUID.toString()
        );

        save();
    }

    /*
     * 获取猫咪 X 坐标
     */
    public double getCatX(UUID playerUUID) {

        return data.getDouble(
                "players." + playerUUID + ".cat.x"
        );
    }

    /*
     * 获取猫咪 Y 坐标
     */
    public double getCatY(UUID playerUUID) {

        return data.getDouble(
                "players." + playerUUID + ".cat.y"
        );
    }

    /*
     * 获取猫咪 Z 坐标
     */
    public double getCatZ(UUID playerUUID) {

        return data.getDouble(
                "players." + playerUUID + ".cat.z"
        );
    }

    /*
     * 保存猫咪位置
     */
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
     * 保存数据
     */
    private void save() {

        try {

            data.save(file);

        } catch (IOException e) {

            e.printStackTrace();
        }
    }
}