package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;

import java.util.logging.Logger;

/**
 * Blockbench 版本适配器（知识包 P0-3）。
 *
 * <p>
 * 把特定 format_version 的 RawDocument 结构转换为
 * 统一的 canonical {@link CanonicalModel}。4.x（Legacy）与
 * 5.x（Modern）的结构差异（outliner 内联树 vs
 * groups/outliner UUID 树、cube vs mesh、动画 keyframe
 * 结构）全部收口在适配器内，下游 Validator/装配
 * 只认识 canonical 结构。
 * </p>
 */
interface BBModelVersionAdapter {

    /**
     * 解析为 canonical 中间表示。
     */
    CanonicalModel adapt(
            JsonObject root,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException;
}
