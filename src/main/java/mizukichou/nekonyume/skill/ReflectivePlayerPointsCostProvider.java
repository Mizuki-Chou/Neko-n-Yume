package mizukichou.nekonyume.skill;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * PlayerPoints 消耗提供者（反射式软依赖）。
 *
 * <p>
 * 不依赖 PlayerPoints 的 jar：
 * 运行时动态探测 getAPI() / look() / take() 的签名，
 * 兼容 UUID 与 PlayerId 两种参数形态。
 * </p>
 *
 * <p>
 * 探测失败时 isAvailable() 返回 false，
 * 管理器自动回退到喵力并记录告警。
 * </p>
 */
public class ReflectivePlayerPointsCostProvider
        implements SkillRefreshCostProvider {

    private final Object pointsApi;
    private final Constructor<?> playerIdConstructor;
    private final Method lookMethod;
    private final Method takeMethod;
    private final boolean available;

    public ReflectivePlayerPointsCostProvider(
            Plugin pointsPlugin
    ) {

        boolean ok = false;
        Object api = null;
        Constructor<?> pidCtor = null;
        Method look = null;
        Method take = null;

        try {

            if (pointsPlugin == null) {
                throw new IllegalStateException(
                        "PlayerPoints plugin not present"
                );
            }

            /*
             * getAPI()
             */
            Method getApi = null;

            for (Method m :
                    pointsPlugin.getClass()
                            .getMethods()) {

                if (m.getName().equals("getAPI") &&
                        m.getParameterCount() == 0) {

                    getApi = m;
                    break;
                }
            }

            if (getApi == null) {
                throw new IllegalStateException(
                        "getAPI() not found"
                );
            }

            api = getApi.invoke(pointsPlugin);

            if (api == null) {
                throw new IllegalStateException(
                        "API is null"
                );
            }

            /*
             * look(...)
             */
            for (Method m :
                    api.getClass()
                            .getMethods()) {

                if (m.getName().equals("look") &&
                        m.getParameterCount() == 1) {

                    look = m;
                    break;
                }
            }

            if (look == null) {
                throw new IllegalStateException(
                        "look(...) not found"
                );
            }

            /*
             * 参数形态：UUID 或 PlayerId。
             */
            Class<?> paramType =
                    look.getParameterTypes()[0];

            if (paramType != UUID.class) {

                for (Constructor<?> c :
                        paramType.getConstructors()) {

                    if (c.getParameterCount() == 1 &&
                            c.getParameterTypes()[0]
                                    == UUID.class) {

                        pidCtor = c;
                        break;
                    }
                }

                if (pidCtor == null) {
                    throw new IllegalStateException(
                            "PlayerId(UUID) constructor not found"
                    );
                }
            }

            /*
             * take(..., int)
             */
            for (Method m :
                    api.getClass()
                            .getMethods()) {

                if (m.getName().equals("take") &&
                        m.getParameterCount() == 2 &&
                        m.getParameterTypes()[0]
                                == paramType &&
                        m.getParameterTypes()[1]
                                == int.class) {

                    take = m;
                    break;
                }
            }

            if (take == null) {
                throw new IllegalStateException(
                        "take(..., int) not found"
                );
            }

            ok = true;

        } catch (Exception e) {

            ok = false;
        }

        this.pointsApi = api;
        this.playerIdConstructor = pidCtor;
        this.lookMethod = look;
        this.takeMethod = take;
        this.available = ok;
    }

    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getDisplayName() {
        return "points";
    }

    @Override
    public boolean canAfford(
            Player player,
            int cost
    ) {

        if (!available) {
            return false;
        }

        try {

            Object id =
                    toId(
                            player.getUniqueId()
                    );

            Object result =
                    lookMethod.invoke(
                            pointsApi,
                            id
                    );

            int balance;

            if (result instanceof Integer value) {

                balance = value;

            } else if (result instanceof Long value) {

                balance =
                        (int) Math.min(
                                value,
                                Integer.MAX_VALUE
                        );

            } else {

                return false;
            }

            return balance >= cost;

        } catch (Exception e) {

            return false;
        }
    }

    @Override
    public boolean charge(
            Player player,
            int cost
    ) {

        if (!available) {
            return false;
        }

        try {

            Object id =
                    toId(
                            player.getUniqueId()
                    );

            takeMethod.invoke(
                    pointsApi,
                    id,
                    cost
            );

            return true;

        } catch (Exception e) {

            return false;
        }
    }

    private Object toId(
            UUID uuid
    ) throws Exception {

        if (playerIdConstructor == null) {
            return uuid;
        }

        return playerIdConstructor.newInstance(
                uuid
        );
    }
}