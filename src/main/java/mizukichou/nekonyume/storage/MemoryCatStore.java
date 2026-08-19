package mizukichou.nekonyume.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CatStore 的纯内存实现。
 *
 * <p>
 * 供单元测试使用（Step 4 生命周期测试）。
 * 与 YamlCatStore 共享全部字段语义（AbstractCatStore），
 * 因此是可信的测试替身。
 * </p>
 *
 * <p>
 * 非线程安全：只应在测试环境使用。
 * </p>
 */
public class MemoryCatStore extends AbstractCatStore {

    private final Map<UUID, Map<String, Object>> cats =
            new HashMap<>();

    private boolean dirty;

    @Override
    protected boolean containsRaw(UUID playerUUID) {

        return playerUUID != null &&
                cats.containsKey(playerUUID);
    }

    @Override
    protected Object getRaw(
            UUID playerUUID,
            String field
    ) {

        Map<String, Object> data =
                cats.get(playerUUID);

        return data == null
                ? null
                : data.get(field);
    }

    @Override
    protected void setRaw(
            UUID playerUUID,
            String field,
            Object value
    ) {

        Map<String, Object> data =
                cats.get(playerUUID);

        if (data == null) {
            return;
        }

        if (value == null) {

            data.remove(field);

        } else {

            data.put(field, value);
        }

        dirty = true;
    }

    @Override
    protected void createRaw(
            UUID playerUUID,
            Map<String, Object> fields
    ) {

        cats.put(
                playerUUID,
                new HashMap<>(fields)
        );

        dirty = true;
    }

    @Override
    protected void deleteRaw(UUID playerUUID) {

        cats.remove(playerUUID);

        dirty = true;
    }

    @Override
    protected Set<UUID> ownerKeysRaw() {

        return new HashSet<>(cats.keySet());
    }

    @Override
    public void save() {

        dirty = true;
    }

    @Override
    public boolean isDirty() {

        return dirty;
    }

    @Override
    public void flush() {

        dirty = false;
    }

    @Override
    public void saveNow() {

        dirty = false;
    }
}
