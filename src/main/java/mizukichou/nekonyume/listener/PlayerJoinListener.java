package mizukichou.nekonyume.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();


    public PlayerJoinListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        if (!plugin.getConfig()
                .getBoolean("join-message.enabled")) {
            return;
        }


        for (String msg : plugin.getConfig()
                .getStringList("join-message.messages")) {

            event.getPlayer()
                    .sendMessage(mm.deserialize(msg));
        }
    }
}