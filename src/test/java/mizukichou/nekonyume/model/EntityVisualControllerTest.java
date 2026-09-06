package mizukichou.nekonyume.model;

import mizukichou.nekonyume.testutil.FakeBukkit;
import mizukichou.nekonyume.testutil.FakeCatEntityRuntime;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D1 方案（Player.hideEntity）的视觉控制器：
 * 隐藏/显示幂等、玩家会话重应用、失效实体清理。
 */
class EntityVisualControllerTest {

    private Plugin plugin;

    private FakeCatEntityRuntime runtime;

    private EntityVisualController controller;

    private List<String> playerCalls;

    private Player player;

    private Cat cat;

    @BeforeEach
    void setUp() {

        plugin =
                FakeBukkit.proxy(
                        Plugin.class,
                        Map.of(),
                        null
                );

        runtime =
                new FakeCatEntityRuntime();

        playerCalls =
                new ArrayList<>();

        World world =
                FakeBukkit.proxy(
                        World.class,
                        Map.of("getName", "test"),
                        null
                );

        player =
                FakeBukkit.proxy(
                        Player.class,
                        answers(
                                "getWorld", world,
                                "getName", "Tester"
                        ),
                        playerCalls
                );

        cat =
                runtime.newCat(
                        UUID.randomUUID(),
                        new Location(world, 0, 64, 0),
                        world,
                        new FakeBukkit.FakePDC()
                );

        /*
         * 4 参重载只建 proxy；手动登记到运行时实体表。
         */
        runtime.entities.put(
                cat.getUniqueId(),
                cat
        );

        runtime.players.put(
                player.getUniqueId(),
                player
        );

        controller =
                new EntityVisualController(
                        plugin,
                        runtime
                );
    }

    @Test
    void hideCallsHideEntityOncePerPlayer() {

        controller.hide(cat);
        controller.hide(cat);

        assertEquals(
                1L,
                playerCalls.stream()
                        .filter(c -> c.equals("hideEntity"))
                        .count()
        );
        assertEquals(
                1,
                controller.hiddenCount()
        );
    }

    @Test
    void showRemovesAndCallsShowEntity() {

        controller.hide(cat);
        controller.show(cat);
        controller.show(cat);

        assertEquals(
                1L,
                playerCalls.stream()
                        .filter(c -> c.equals("showEntity"))
                        .count()
        );
        assertEquals(
                0,
                controller.hiddenCount()
        );
    }

    @Test
    void showWithoutHideIsNoOp() {

        controller.show(cat);

        assertEquals(
                0L,
                playerCalls.stream()
                        .filter(c -> c.equals("showEntity"))
                        .count()
        );
    }

    @Test
    void nonCatEntityIsIgnored() {

        Entity stranger =
                FakeBukkit.proxy(
                        Entity.class,
                        Map.of(),
                        null
                );

        controller.hide(stranger);

        assertEquals(
                0,
                controller.hiddenCount()
        );
    }

    @Test
    void playerJoinReappliesHide() {

        controller.hide(cat);

        /*
         * 新会话玩家：登录后重新 hide。
         */
        controller.onPlayerJoin(player);

        assertEquals(
                2L,
                playerCalls.stream()
                        .filter(c -> c.equals("hideEntity"))
                        .count()
        );
    }

    @Test
    void invalidEntityIsForgottenOnJoin() {

        controller.hide(cat);

        /*
         * 实体失效后：join 应用时遗忘。
         */
        runtime.entities.remove(
                cat.getUniqueId()
        );

        controller.onPlayerJoin(player);

        assertEquals(
                0,
                controller.hiddenCount()
        );
    }

    @Test
    void forgetRemovesWithoutShow() {

        controller.hide(cat);

        controller.forget(
                cat.getUniqueId()
        );

        assertEquals(
                0,
                controller.hiddenCount()
        );
        assertEquals(
                0L,
                playerCalls.stream()
                        .filter(c -> c.equals("showEntity"))
                        .count()
        );
    }

    @Test
    void clearAllDropsEverything() {

        controller.hide(cat);
        controller.clearAll();

        assertEquals(
                0,
                controller.hiddenCount()
        );
    }

    @Test
    void nullArgumentsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new EntityVisualController(
                        null,
                        runtime
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new EntityVisualController(
                        plugin,
                        null
                )
        );
    }

    private static Map<String, Object> answers(
            Object... pairs
    ) {

        Map<String, Object> map =
                new HashMap<>();

        for (int i = 0; i < pairs.length; i += 2) {

            map.put(
                    (String) pairs[i],
                    pairs[i + 1]
            );
        }

        return map;
    }
}
