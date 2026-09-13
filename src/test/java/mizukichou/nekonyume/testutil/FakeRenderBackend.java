package mizukichou.nekonyume.testutil;

import mizukichou.nekonyume.model.ModelRenderBackend;
import mizukichou.nekonyume.model.ModelRenderObject;
import mizukichou.nekonyume.model.Quaternion;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.Vec3;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 渲染后端测试替身：记录全部渲染对象与写入。
 */
public final class FakeRenderBackend implements ModelRenderBackend {

    private final List<FakeRenderObject> created =
            new ArrayList<>();

    private final List<org.bukkit.entity.Interaction> spawnedInteractions =
            new ArrayList<>();

    private final List<List<String>> interactionCalls =
            new ArrayList<>();

    private final List<UUID> interactionCatUuids =
            new ArrayList<>();

    @Override
    public org.bukkit.entity.Interaction spawnInteraction(
            Location location,
            UUID catUuid
    ) {

        List<String> calls =
                new ArrayList<>();

        interactionCalls.add(
                calls
        );

        interactionCatUuids.add(
                catUuid
        );

        org.bukkit.entity.Interaction proxy =
                FakeBukkit.proxy(
                        org.bukkit.entity.Interaction.class,
                        java.util.Map.of(
                                "getUniqueId",
                                UUID.randomUUID(),
                                "isValid",
                                true,
                                "getLocation",
                                location.clone()
                        ),
                        calls
                );

        spawnedInteractions.add(
                proxy
        );

        return proxy;
    }

    public List<org.bukkit.entity.Interaction> getSpawnedInteractions() {

        return spawnedInteractions;
    }

    public List<String> getInteractionCalls(
            int index
    ) {

        return interactionCalls.get(
                index
        );
    }

    public List<UUID> getInteractionCatUuids() {

        return interactionCatUuids;
    }

    @Override
    public ModelRenderObject createBoneObject(
            Location location,
            ResourceId itemModel,
            String boneName,
            UUID catUuid
    ) {

        FakeRenderObject object =
                new FakeRenderObject(
                        location,
                        itemModel,
                        boneName
                );

        created.add(
                object
        );

        return object;
    }

    public List<FakeRenderObject> getCreated() {

        return created;
    }

    public FakeRenderObject objectFor(
            String boneName
    ) {

        for (FakeRenderObject object :
                created) {

            if (object.getBoneName().equals(boneName)) {
                return object;
            }
        }

        return null;
    }

    /**
     * 记录型渲染对象。
     */
    public static final class FakeRenderObject
            implements ModelRenderObject {

        private final UUID id =
                UUID.randomUUID();

        private final Location spawnLocation;

        private final ResourceId itemModel;

        private final String boneName;

        private Vec3 lastTranslation;

        private Quaternion lastRotation;

        private Vec3 lastScale;

        private Location lastTeleportTarget;

        private int transformWrites;

        private int teleportCount;

        private boolean removed;

        private boolean visible = true;

        private boolean interactive;

        FakeRenderObject(
                Location spawnLocation,
                ResourceId itemModel,
                String boneName
        ) {

            this.spawnLocation = spawnLocation;
            this.itemModel = itemModel;
            this.boneName = boneName;
        }

        @Override
        public UUID getId() {

            return id;
        }

        @Override
        public void setWorldTransform(
                Vec3 translationBlocks,
                Quaternion rotation,
                Vec3 scale
        ) {

            if (removed) {
                throw new IllegalStateException(
                        "Write after remove."
                );
            }

            this.lastTranslation = translationBlocks;
            this.lastRotation = rotation;
            this.lastScale = scale;
            this.transformWrites++;
        }

        @Override
        public void teleportTo(
                Location location
        ) {

            if (removed) {
                throw new IllegalStateException(
                        "Teleport after remove."
                );
            }

            this.lastTeleportTarget =
                    location.clone();
            this.teleportCount++;
        }

        @Override
        public void remove() {

            this.removed = true;
        }

        @Override
        public void setVisible(
                boolean visible
        ) {

            if (removed) {
                throw new IllegalStateException(
                        "Set visible after remove."
                );
            }

            this.visible = visible;
        }

        @Override
        public void setInteractive(
                boolean interactive
        ) {

            if (removed) {
                throw new IllegalStateException(
                        "Set interactive after remove."
                );
            }

            this.interactive =
                    interactive;
        }

        public Location getSpawnLocation() {

            return spawnLocation;
        }

        public ResourceId getItemModel() {

            return itemModel;
        }

        public String getBoneName() {

            return boneName;
        }

        public Vec3 getLastTranslation() {

            return lastTranslation;
        }

        public Quaternion getLastRotation() {

            return lastRotation;
        }

        public Vec3 getLastScale() {

            return lastScale;
        }

        public Location getLastTeleportTarget() {

            return lastTeleportTarget;
        }

        public int getTransformWrites() {

            return transformWrites;
        }

        public int getTeleportCount() {

            return teleportCount;
        }

        public boolean isRemoved() {

            return removed;
        }

        public boolean isVisible() {

            return visible;
        }

        public boolean isInteractive() {

            return interactive;
        }
    }
}
