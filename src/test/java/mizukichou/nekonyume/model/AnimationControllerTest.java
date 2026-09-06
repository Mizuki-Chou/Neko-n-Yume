package mizukichou.nekonyume.model;

import org.junit.jupiter.api.BeforeEach;
import mizukichou.nekonyume.model.ModelBonePose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationControllerTest {

    private ModelInstance instance;

    private AnimationController controller;

    @BeforeEach
    void setUp() {

        ModelBone body =
                new ModelBone(
                        "Body",
                        ModelTransform.IDENTITY,
                        List.of(),
                        List.of()
                );

        ModelBone head =
                new ModelBone(
                        "Head",
                        ModelTransform.IDENTITY,
                        List.of(),
                        List.of()
                );

        ModelBone root =
                new ModelBone(
                        "Root",
                        ModelTransform.IDENTITY,
                        List.of(
                                body,
                                head
                        ),
                        List.of()
                );

        ModelGeometry geometry =
                new ModelGeometry.Builder(
                        "main",
                        root
                ).build();

        /*
         * walk：Body 与 Head 均绕 Y 0° → 90°（loop，1 秒）；
         * sit：仅 Body 平移 (0, -2, 0)（非循环，1 秒）。
         */
        ModelAnimation walk =
                new ModelAnimation(
                        "walk",
                        1.0,
                        LoopMode.LOOP,
                        Map.of(
                                "Body",
                                track(
                                        "Body",
                                        List.of(
                                                frame(
                                                        0.0,
                                                        new Vec3(0.0, 0.0, 0.0)
                                                ),
                                                frame(
                                                        1.0,
                                                        new Vec3(0.0, 90.0, 0.0)
                                                )
                                        ),
                                        List.of()
                                ),
                                "Head",
                                track(
                                        "Head",
                                        List.of(
                                                frame(
                                                        0.0,
                                                        new Vec3(0.0, 0.0, 0.0)
                                                ),
                                                frame(
                                                        1.0,
                                                        new Vec3(0.0, 90.0, 0.0)
                                                )
                                        ),
                                        List.of()
                                )
                        )
                );

        ModelAnimation sit =
                new ModelAnimation(
                        "sit",
                        1.0,
                        LoopMode.HOLD,
                        Map.of(
                                "Body",
                                track(
                                        "Body",
                                        List.of(),
                                        List.of(
                                                frame(
                                                        0.0,
                                                        new Vec3(0.0, 0.0, 0.0)
                                                ),
                                                frame(
                                                        1.0,
                                                        new Vec3(0.0, -2.0, 0.0)
                                                )
                                        )
                                )
                        )
                );

        ModelAnimation attack =
                new ModelAnimation(
                        "attack",
                        1.0,
                        LoopMode.LOOP,
                        Map.of(
                                "Body",
                                track(
                                        "Body",
                                        List.of(
                                                frame(
                                                        0.0,
                                                        new Vec3(
                                                                0.0,
                                                                0.0,
                                                                0.0
                                                        )
                                                ),
                                                frame(
                                                        1.0,
                                                        new Vec3(
                                                                0.0,
                                                                0.0,
                                                                45.0
                                                        )
                                                )
                                        ),
                                        List.of()
                                )
                        )
                );

        ModelDefinition definition =
                new ModelDefinition.Builder()
                        .id("cats:test_cat")
                        .geometry(geometry)
                        .animation(walk)
                        .animation(sit)
                        .build();

        instance =
                new ModelInstance(
                        definition,
                        UUID.randomUUID()
                );

        controller =
                new AnimationController(
                        instance
                );
    }

    @Test
    void playNonexistentAnimationIsIgnored() {

        controller.playAt(
                "no_such_animation",
                0.0
        );

        assertFalse(
                controller.hasActiveAnimation()
        );

        controller.tickAt(
                0.5
        );

        assertEquals(
                ModelTransform.IDENTITY,
                instance.getBone("Body")
                        .getAnimationTransform()
        );
    }

    @Test
    void samplesAndWritesPoses() {

        controller.playAt(
                "walk",
                0.0
        );

        controller.tickAt(
                0.0
        );

        controller.tickAt(
                0.5
        );

        ModelTransform animated =
                instance.getBone("Body")
                        .getAnimationTransform();

        assertNotNull(animated);

        assertFalse(
                animated.isIdentity()
        );

        /*
         * 半程：绕 Y 约 45°。
         */
        Quaternion rotation =
                animated.getRotation();

        assertTrue(
                Math.abs(
                        rotation.getY()
                ) > 0.3
        );
    }

    @Test
    void loopWrapsAround() {

        controller.playAt(
                "walk",
                0.0
        );

        controller.tickAt(
                0.0
        );

        controller.tickAt(
                0.6
        );

        controller.tickAt(
                1.2
        );

        /*
         * loop 动画：1.2s → 取模 0.2s；
         * 与 0.2s 处直接采样一致（插值方式无关）。
         */
        Quaternion rotation =
                instance.getBone("Body")
                        .getAnimationTransform()
                        .getRotation();

        /*
         * 通道级 Euler 线性：0.2s → 绕 Y 18°。
         */
        Quaternion expected =
                Quaternion.fromEulerDegXYZ(
                        0.0,
                        18.0,
                        0.0
                );

        /*
         * 浮点时间取模的 1 ulp 差异：分量级近似比较
         * （Quaternion.equals 是精确契约）。
         */
        assertTrue(
                Math.abs(
                        rotation.getY()
                                - expected.getY()
                ) < 1e-6
        );
    }

    @Test
    void nonLoopStopsAtLastFrame() {

        controller.playAt(
                "sit",
                0.0
        );

        controller.tickAt(
                0.0
        );

        controller.tickAt(
                5.0
        );

        Vec3 translation =
                instance.getBone("Body")
                        .getAnimationTransform()
                        .getTranslation();

        assertEquals(
                -2.0,
                translation.getY(),
                1e-9
        );
    }

    @Test
    void crossfadeBlendsBetweenAnimations() {

        controller.playAt(
                "walk",
                0.0
        );

        controller.tickAt(
                0.0
        );

        controller.tickAt(
                0.2
        );

        controller.playAt(
                "sit",
                0.2
        );

        /*
         * 淡化进行中（0.2 + 0.07 < 0.15 完成线）。
         */
        controller.tickAt(
                0.27
        );

        ModelTransform blended =
                instance.getBone("Body")
                        .getAnimationTransform();

        assertNotNull(blended);

        /*
         * 混合结果应同时带 walk 的旋转与 sit 的平移分量。
         */
        assertFalse(
                blended.getRotation()
                        .isIdentity()
        );

        assertTrue(
                blended.getTranslation()
                        .getY() < 0.0
        );

        /*
         * 淡化完成后只剩 sit；进度 0.3s → -0.6。
         */
        controller.tickAt(
                0.5
        );

        ModelTransform finalPose =
                instance.getBone("Body")
                        .getAnimationTransform();

        assertTrue(
                finalPose.getRotation()
                        .isIdentity()
        );

        assertEquals(
                -0.6,
                finalPose.getTranslation()
                        .getY(),
                1e-9
        );
    }

    @Test
    void playSameAnimationIsIdempotent() {

        controller.playAt(
                "walk",
                0.0
        );

        controller.tickAt(
                0.0
        );

        controller.tickAt(
                0.5
        );

        Quaternion before =
                instance.getBone("Body")
                        .getAnimationTransform()
                        .getRotation();

        controller.playAt(
                "walk",
                100.0
        );

        controller.tickAt(
                100.6
        );

        Quaternion after =
                instance.getBone("Body")
                        .getAnimationTransform()
                        .getRotation();

        /*
         * 相同动画重播被忽略：0.5 → 1.5（loop 取模 0.5）连续推进，
         * 而不是从 0 重新开始——两处采样同为 45°。
         */
        assertTrue(
                after.equals(
                        before
                )
        );
    }

    @Test
    void switchingTargetMidBlendStartsFromRenderedPose() {

        /*
         * P1-22：A→B 途中切 C——新 blend 的 from 必须是
         * 此刻屏幕上的混合结果（含 B 的位移分量），
         * 而不是 A 身份的继续推进（否则 pop）。
         */
        controller.playAt(
                "walk",
                0.0
        );

        controller.tickAt(
                0.0
        );

        controller.tickAt(
                0.5
        );

        controller.playAt(
                "sit",
                0.5
        );

        controller.tickAt(
                0.55
        );

        controller.playAt(
                "attack",
                0.55
        );

        controller.tickAt(
                0.62
        );

        double translationY =
                instance.getBone(
                                "Body"
                        )
                        .getAnimationTransform()
                        .getTranslation()
                        .getY();

        /*
         * 冻结版：from = 混合结果（sit 位移 ≈ -0.033）→
         *   blend 权重后 ≈ -0.018（非零）。
         * 旧版：from = walk 继续推进（位移 0）→ 0.0。
         */
        assertTrue(
                Math.abs(
                        translationY
                ) > 0.005,
                "blend 中切动画必须从屏幕混合结果开始，"
                        + "实际 translationY = "
                        + translationY
        );
    }

    @Test
    void stopResetsAllPoses() {

        controller.playAt(
                "walk",
                0.0
        );

        controller.tickAt(
                0.5
        );

        controller.stop();

        assertFalse(
                controller.hasActiveAnimation()
        );

        assertEquals(
                ModelTransform.IDENTITY,
                instance.getBone("Body")
                        .getAnimationTransform()
        );
    }

    @Test
    void rejectsNullInstance() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new AnimationController(
                        null
                )
        );
    }

    @Test
    void crossfadeDropsBonesAbsentFromTarget() {

        controller.playAt(
                "walk",
                0.0
        );

        controller.tickAt(0.0);
        controller.tickAt(0.5);

        /*
         * walk 覆盖 Head（45°），作为残留候选。
         */
        assertFalse(
                instance.getBone("Head")
                        .getAnimationTransform()
                        .isIdentity()
        );

        /*
         * 切到不含 Head 的 sit：blend 完成后 Head 必须归零。
         */
        controller.playAt(
                "sit",
                0.5
        );

        controller.tickAt(0.6);
        controller.tickAt(1.0);

        assertTrue(
                instance.getBone("Head")
                        .getAnimationTransform()
                        .isIdentity()
        );

        assertFalse(
                instance.getBone("Body")
                        .getAnimationTransform()
                        .isIdentity()
        );
    }

    @Test
    void nonLoopSettlesAfterLastFrame() {

        controller.playAt(
                "sit",
                0.0
        );

        controller.tickAt(0.0);

        assertTrue(
                controller.tickAt(5.0)
        );

        assertEquals(
                -2.0,
                instance.getBone("Body")
                        .getAnimationTransform()
                        .getTranslation()
                        .getY(),
                0.0001
        );

        /*
         * settled：后续 tick 零操作。
         */
        assertFalse(
                controller.tickAt(6.0)
        );
    }

    private static ChannelKeyframe frame(
            double time,
            Vec3 value
    ) {

        return new ChannelKeyframe(
                time,
                value,
                KeyframeInterpolation.LINEAR
        );
    }

    private static ModelBoneAnimation track(
            String boneName,
            List<ChannelKeyframe> rotationFrames,
            List<ChannelKeyframe> positionFrames
    ) {

        AnimationChannel rotation =
                rotationFrames.isEmpty()
                        ? new AnimationChannel(
                                List.of(
                                        frame(
                                                0.0,
                                                Vec3.ZERO
                                        )
                                )
                        )
                        : new AnimationChannel(
                                rotationFrames
                        );

        AnimationChannel position =
                positionFrames.isEmpty()
                        ? new AnimationChannel(
                                List.of(
                                        frame(
                                                0.0,
                                                Vec3.ZERO
                                        )
                                )
                        )
                        : new AnimationChannel(
                                positionFrames
                        );

        AnimationChannel scale =
                new AnimationChannel(
                        List.of(
                                frame(
                                        0.0,
                                        Vec3.ONE
                                )
                        )
                );

        return new ModelBoneAnimation(
                boneName,
                rotation,
                position,
                scale,
                ModelTransform.IDENTITY
        );
    }

    @Test
    void nonLoopReplaysAfterSettled() {

        controller.playAt(
                "sit",
                0.0
        );

        controller.tickAt(0.0);
        controller.tickAt(5.0);

        /*
         * 播完后请求同名动画：允许重播（从 0 开始）。
         * 重播后首个 tick 重建时间基准，第二个 tick 才采样。
         */
        controller.playAt(
                "sit",
                10.0
        );

        controller.tickAt(10.5);
        controller.tickAt(11.0);

        assertEquals(
                -1.0,
                instance.getBone("Body")
                        .getAnimationTransform()
                        .getTranslation()
                        .getY(),
                0.0001
        );
    }

    /*
     * 0.9.0更新：A→B 淡化途中切回 A 不得产生
     * self-blend（叠加导致幅度加倍），应取消淡化保持 A。
     */
    @Test
    void playSourceDuringBlendCancelsBlend() {

        controller.playAt(
                "walk",
                0.0
        );

        controller.tickAt(
                0.0
        );

        controller.tickAt(
                0.5
        );

        controller.playAt(
                "sit",
                0.5
        );

        controller.tickAt(
                0.55
        );

        assertTrue(
                controller.isBlending()
        );

        controller.playAt(
                "walk",
                0.55
        );

        assertFalse(
                controller.isBlending()
        );

        controller.tickAt(
                0.85
        );

        /*
         * 取消后只剩 walk：Body 平移恒 0（sit 的
         * -2 位移绝不叠加），旋转为 walk 轨迹。
         */
        ModelBonePose pose =
                instance.getBone(
                        "Body"
                );

        assertEquals(
                0.0,
                pose.getAnimationTransform()
                        .getTranslation()
                        .getY(),
                1e-9
        );

        assertFalse(
                pose.getAnimationTransform()
                        .getRotation()
                        .isIdentity()
        );
    }

}
