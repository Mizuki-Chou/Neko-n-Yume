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
 *
 * <p>
 * 扣款契约：
 * take() 必须返回 boolean 且以返回值为准——
 * 返回 false（余额不足 / 内部失败）时，
 * 本次扣款视为失败，绝不允许玩家免费刷新。
 * </p>
 */
public class ReflectivePlayerPointsCostProvider
        implements SkillRefreshCostProvider {

    private final Object pointsApi;
    private final Constructor<?> playerIdConstructor;
    private final Method lookMethod;
    private final Method takeMethod;
    private final Method giveMethod;
    private final boolean available;

    public ReflectivePlayerPointsCostProvider(
            Plugin pointsPlugin
    ) {

        boolean ok = false;
        Object api = null;
        Constructor<?> pidCtor = null;
        Method look = null;
        Method take = null;
        Method give = null;

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

            /*
             * 扣款结果必须可判定：
             * 只有返回 boolean 的 take 才可信。
             * 否则扣款失败无法感知，直接判定不可用，
             * 回退到喵力消耗。
             */
            if (take.getReturnType()
                    != boolean.class) {

                throw new IllegalStateException(
                        "take(..., int) must return boolean"
                );
            }

            /*
             * give(...) 可选：仅用于防御路径退款。
             * 探测失败不影响可用性判定。
             */
            for (Method m :
                    api.getClass()
                            .getMethods()) {

                if (m.getName().equals("give") &&
                        m.getParameterCount() == 2 &&
                        m.getParameterTypes()[0]
                                == paramType &&
                        m.getParameterTypes()[1]
                                == int.class) {

                    give = m;
                    break;
                }
            }

            ok = true;

        } catch (Exception e) {

            ok = false;
        }

        this.pointsApi = api;
        this.playerIdConstructor = pidCtor;
        this.lookMethod = look;
        this.takeMethod = take;
        this.giveMethod = give;
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

        /*
         * 负数花费视为非法，直接拒绝。
         */
        if (cost < 0) {
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

        /*
         * 负数花费视为非法，直接拒绝。
         */
        if (cost < 0) {
            return false;
        }

        try {

            Object id =
                    toId(
                            player.getUniqueId()
                    );

            /*
             * 修复：必须检查 take() 的返回结果。
             *
             * 之前无条件返回 true，
             * 扣款失败（余额不足竞态 / 内部异常返回 false）
             * 也会被当成成功，玩家可以无限免费刷新技能。
             */
            Object result =
                    takeMethod.invoke(
                            pointsApi,
                            id,
                            cost
                    );

            return result instanceof Boolean bool &&
                    bool;

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

    @Override
    public void refund(
            Player player,
            int cost
    ) {

        if (!available ||
                giveMethod == null ||
                cost <= 0) {

            return;
        }

        try {

            giveMethod.invoke(
                    pointsApi,
                    toId(
                            player.getUniqueId()
                    ),
                    cost
            );

        } catch (Exception e) {

            /*
             * 退款失败只记录，不影响主流程：
             * 该路径仅在"写入被拒绝"的极端防御分支触发。
             */
            java.util.logging.Logger.getLogger(
                            "NekoNYume"
                    )
                    .warning(
                            "Failed to refund "
                                    + cost
                                    + " points to "
                                    + player.getName()
                    );
        }
    }
}
