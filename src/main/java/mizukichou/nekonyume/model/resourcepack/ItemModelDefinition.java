package mizukichou.nekonyume.model.resourcepack;

import mizukichou.nekonyume.model.bbmodel.json.JsonObject;

/**
 * Minecraft 26.2 Item Model 定义的 AST 根
 * （知识包 P0-1：现代 Item Definition 体系）。
 *
 * <p>
 * 1.21.4+ 的 item definition JSON 顶层为：
 * {@code {"model": <item model type object>}}。
 * 本接口表达"model"字段内部的 item model type；
 * 未来按需扩展 {@link PlainItemModel} 之外的
 * composite / select / condition / range_dispatch
 * 类型（sealed 层级）。
 * </p>
 */
public sealed interface ItemModelDefinition
        permits PlainItemModel {

    /**
     * 序列化为 item model type 对象
     * （即 item definition 的 "model" 字段值）。
     */
    JsonObject toJson();
}
