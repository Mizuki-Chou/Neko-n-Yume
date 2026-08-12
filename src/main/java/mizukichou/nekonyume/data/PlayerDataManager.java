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

    private void save() {

        try {

            data.save(file);

        } catch (IOException e) {

            e.printStackTrace();
        }
    }
}