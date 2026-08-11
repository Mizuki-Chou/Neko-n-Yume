package mizukichou.nekonyume.cat;

import org.bukkit.Location;
import org.bukkit.entity.Cat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;


public class CatManager {


    private final JavaPlugin plugin;


    public CatManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }



    public void spawnCat(Player player, String name) {


        Location loc =
                player.getLocation();


        Cat cat =
                (Cat) player.getWorld()
                        .spawnEntity(
                                loc,
                                EntityType.CAT
                        );


        cat.setCustomName(
                "§d🐱 " + name
        );


        cat.setCustomNameVisible(true);


        cat.setOwner(player);


        cat.setTamed(true);


    }

}