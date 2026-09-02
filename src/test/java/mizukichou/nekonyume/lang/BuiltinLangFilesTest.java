package mizukichou.nekonyume.lang;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 0.8.1 修复（R3，社区上报）：
 * 生产资源验证——四套内置语言文件必须真实可解析，
 * 且包含关键键。此前单元测试只解析人工构造的小 YAML，
 * 导致内置资源缩进损坏也无法被测试发现。
 */
class BuiltinLangFilesTest {

    private static final List<String> CODES =
            List.of(
                    "zh_cn",
                    "zh_tw",
                    "en_us",
                    "ja_jp"
            );

    /*
     * 关键键（任意一套缺失都视为 Release Blocker）：
     * 覆盖命令 / 喂食 / 喵丹升阶 / 战斗 / 实体 / 面板核心文案。
     */
    private static final List<String> REQUIRED_KEYS =
            List.of(
                    "command.unknown",
                    "command.help",
                    "feed.full",
                    "feed.not-your-cat",
                    "feed.tier-upgrade-invalid",
                    "feed.tier-upgrade-fail",
                    "battle.recovering",
                    "entity.name-normal",
                    "equip.done",
                    "gift.received",
                    "gui.close"
            );

    /*
     * 0.8.5 回归：gui 节是「§ 纯字符串」节，
     * 排行面板键若混入 MiniMessage 标签（<gray> 等），
     * text() 会原样返回导致界面显示标签文本。
     */
    private static final List<String> RANKING_GUI_KEYS =
            List.of(
                    "gui.ranking-title",
                    "gui.ranking-head",
                    "gui.ranking-lore-rank",
                    "gui.ranking-lore-owner",
                    "gui.ranking-lore-meow",
                    "gui.ranking-lore-level",
                    "gui.ranking-sort",
                    "gui.ranking-mode-meow",
                    "gui.ranking-mode-level",
                    "gui.ranking-page",
                    "gui.ranking-prev",
                    "gui.ranking-next",
                    "gui.ranking-close",
                    "gui.ranking-empty"
            );

    @Test
    void rankingGuiKeysUseLegacyCodesOnly() {

        for (String code : CODES) {

            YamlConfiguration config =
                    loadBuiltin(
                            code
                    );

            for (String key :
                    RANKING_GUI_KEYS) {

                String template =
                        config.getString(
                                key
                        );

                assertNotNull(
                        template,
                        code + " missing " + key
                );

                assertFalse(
                        template.contains("<"),
                        code + " " + key
                        + " must use legacy § codes, got: "
                        + template
                );
            }
        }
    }

    @Test
    void allBuiltinLanguageResourcesParse() {

        for (String code : CODES) {

            YamlConfiguration config =
                    loadBuiltin(
                            code
                    );

            assertNotNull(
                    config,
                    "missing builtin resource lang/"
                            + code
                            + ".yml"
            );

            assertFalse(
                    config.getKeys(false)
                            .isEmpty(),
                    code
                            + " parsed to empty configuration"
            );
        }
    }

    @Test
    void allBuiltinLanguagesContainRequiredKeys() {

        for (String code : CODES) {

            YamlConfiguration config =
                    loadBuiltin(
                            code
                    );

            assertNotNull(
                    config
            );

            for (String key :
                    REQUIRED_KEYS) {

                assertTrue(
                        config.contains(
                                key
                        ),
                        code
                                + " is missing key '"
                                + key
                                + "'"
                );
            }
        }
    }

    private YamlConfiguration loadBuiltin(
            String code
    ) {

        String path =
                "/lang/" + code + ".yml";

        try (InputStream stream =
                     BuiltinLangFilesTest.class
                             .getResourceAsStream(
                                     path
                             )) {

            if (stream == null) {
                return null;
            }

            String content =
                    new String(
                            stream.readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            YamlConfiguration config =
                    new YamlConfiguration();

            config.loadFromString(
                    content
            );

            return config;

        } catch (IOException |
                 InvalidConfigurationException e) {

            fail(
                    path
                            + " failed to parse: "
                            + e
            );

            return null;
        }
    }
}
