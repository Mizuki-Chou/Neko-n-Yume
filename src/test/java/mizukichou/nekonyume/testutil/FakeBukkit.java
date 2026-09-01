package mizukichou.nekonyume.testutil;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.8.4：Bukkit 接口的轻量测试替身。
 *
 * <p>
 * 通过 java.lang.reflect.Proxy 运行时实现任意 Bukkit 接口：
 * 测试只关心恢复管线实际触达的方法（answers 显式给出），
 * 其余方法按返回类型给安全默认值，调用记录进 calls 供断言。
 * </p>
 */
public final class FakeBukkit {

    private FakeBukkit() {
    }

    /**
     * 创建接口代理。
     *
     * @param type    目标接口类型（org.bukkit.entity.Cat 等）
     * @param answers 方法名 → 返回值；值可以是字面量或 {@link Answer} 函数
     * @param calls   调用记录（可为 null）
     */
    @SuppressWarnings("unchecked")
    public static <T> T proxy(
            Class<T> type,
            Map<String, Object> answers,
            List<String> calls
    ) {

        InvocationHandler handler = (proxyObj, method, args) -> {

            String name = method.getName();

            /*
             * Object 方法默认语义：身份相等 / 身份哈希 / 可读标签。
             * equals 必须是身份比较——清理逻辑（cat.equals(keepCat)）
             * 依赖同一代理实例相等。
             */
            if (name.equals("equals") && args != null && args.length == 1) {
                return proxyObj == args[0];
            }
            if (name.equals("hashCode") && (args == null || args.length == 0)) {
                return System.identityHashCode(proxyObj);
            }
            if (name.equals("toString") && (args == null || args.length == 0)) {
                return "Fake" + type.getSimpleName();
            }

            if (calls != null) {
                calls.add(name);
            }

            Object value = answers.get(name);

            if (value instanceof Answer answer) {
                return answer.apply(args);
            }

            if (answers.containsKey(name)) {
                return value;
            }

            return defaultValue(method.getReturnType());
        };

        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        );
    }

    private static Object defaultValue(Class<?> returnType) {

        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == double.class) return 0.0;
        if (returnType == float.class) return 0f;
        if (returnType == short.class) return (short) 0;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == char.class) return '\0';
        return null;
    }

    /**
     * 函数型答案：按调用参数动态计算返回值。
     */
    @FunctionalInterface
    public interface Answer {

        Object apply(Object[] args);
    }

    /**
     * 真正的 PDC 测试实现（内部 map 存储）。
     */
    public static final class FakePDC implements PersistentDataContainer {

        private final Map<NamespacedKey, Object> values =
                new HashMap<>();

        private final List<String> calls = new ArrayList<>();

        @Override
        public <T, Z> void set(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
            calls.add("set");
            values.put(key, value);
        }

        @Override
        public <T, Z> boolean has(NamespacedKey key, PersistentDataType<T, Z> type) {
            return values.containsKey(key);
        }

        @Override
        public boolean has(NamespacedKey key) {
            return values.containsKey(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> Z get(NamespacedKey key, PersistentDataType<T, Z> type) {
            return (Z) values.get(key);
        }

        @Override
        public void remove(NamespacedKey key) {
            values.remove(key);
        }

        @Override
        public Set<NamespacedKey> getKeys() {
            return new HashSet<>(values.keySet());
        }

        @Override
        public boolean isEmpty() {
            return values.isEmpty();
        }

        @Override
        public void copyTo(PersistentDataContainer other, boolean replace) {
            for (Map.Entry<NamespacedKey, Object> entry : values.entrySet()) {
                NamespacedKey key = entry.getKey();
                if (replace || !other.has(key)) {
                    setInto(other, key, entry.getValue());
                }
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void setInto(PersistentDataContainer target, NamespacedKey key, Object value) {
            if (value instanceof Byte b) {
                target.set(key, PersistentDataType.BYTE, b);
            } else if (value instanceof String s) {
                target.set(key, PersistentDataType.STRING, s);
            } else if (value instanceof Integer i) {
                target.set(key, PersistentDataType.INTEGER, i);
            } else if (value instanceof Long l) {
                target.set(key, PersistentDataType.LONG, l);
            }
        }

        @Override
        public PersistentDataAdapterContext getAdapterContext() {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> Z getOrDefault(NamespacedKey key, PersistentDataType<T, Z> type, Z defaultValue) {
            Z value = get(key, type);
            return value == null ? defaultValue : value;
        }

        @Override
        public void readFromBytes(byte[] bytes, boolean clear) {
            if (clear) {
                values.clear();
            }
            // 测试不需要二进制往返，空实现即可。
        }

        @Override
        public int getSize() {
            return values.size();
        }

        @Override
        public byte[] serializeToBytes() {
            return new byte[0];
        }

        public List<String> calls() {
            return calls;
        }
    }
}
