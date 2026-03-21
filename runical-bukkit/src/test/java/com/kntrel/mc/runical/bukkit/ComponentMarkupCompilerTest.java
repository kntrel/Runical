package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.BaseRunical;
import com.kntrel.mc.runical.core.ListStyle;
import com.kntrel.mc.runical.core.Placeholder;
import com.kntrel.mc.runical.core.ResolutionSource;
import com.kntrel.mc.runical.core.ResolvedTranslation;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentMarkupCompilerTest {

    @Test
    void compilesNestedFormattingAndColorTags() {
        BaseComponent component = ComponentMarkupCompiler.compile("A <b><c:red>Title</c></b> B");

        assertEquals("A Title B", BaseComponent.toPlainText(component));

        List<BaseComponent> segments = componentSegments(component);
        assertEquals(3, segments.size());
        assertEquals("A ", textOf(segments.get(0)));

        TextComponent title = textComponent(segments.get(1));
        assertEquals("Title", title.getText());
        assertTrue(title.isBold());
        assertEquals(ChatColor.RED, title.getColor());

        TextComponent suffix = textComponent(segments.get(2));
        assertEquals(" B", suffix.getText());
        assertFalse(suffix.isBold());
        assertFalse(suffix.isItalic());
    }

    @Test
    void supportsClosingSugarAndNestedColorScopes() {
        BaseComponent component = ComponentMarkupCompiler.compile("<c:red>red <c:blue>blue</> red</c>");

        assertEquals("red blue red", BaseComponent.toPlainText(component));

        List<BaseComponent> segments = componentSegments(component);
        assertEquals(3, segments.size());
        assertEquals(ChatColor.RED, textComponent(segments.get(0)).getColor());
        assertEquals(ChatColor.BLUE, textComponent(segments.get(1)).getColor());
        assertEquals(ChatColor.RED, textComponent(segments.get(2)).getColor());
    }

    @Test
    void leavesInvalidMarkupLiteralWhenItCannotBeParsed() {
        BaseComponent component = ComponentMarkupCompiler.compile("bad <c:#FFFFF>hex</c> </>");

        assertEquals("bad <c:#FFFFF>hex</c> </>", BaseComponent.toPlainText(component));
        assertEquals(1, componentSegments(component).size());
    }

    @Test
    void translateAsComponentUsesLocaleResolutionAndKeyFallback() {
        RecordingTranslator translator = new RecordingTranslator(
                new ResolvedTranslation("en-us", "title.key", "en-us", "<b>Title</b>", ResolutionSource.EXACT),
                new ResolvedTranslation("en-us", "player.key", "en-us", "<i>Player</i>", ResolutionSource.EXACT)
        );

        BaseComponent resolved = translator.translateAsComponent("en-us", "title.key", Placeholder.of("name", "Alex"));
        assertEquals("Title", BaseComponent.toPlainText(resolved));
        assertTrue(textComponent(componentSegments(resolved).get(0)).isBold());
        assertEquals("en-us", translator.lastLocale_);
        assertEquals("title.key", translator.lastLocaleKey_);
        assertEquals(1, translator.lastLocaleArgs_.length);

        translator.localeResult_ = new ResolvedTranslation("en-us", "missing.key", null, null, ResolutionSource.UNRESOLVED);
        BaseComponent fallback = translator.translateAsComponent("en-us", "missing.key");
        assertEquals("missing.key", BaseComponent.toPlainText(fallback));
    }

    @Test
    void translateAsComponentAsyncUsesPlayerResolution() {
        RecordingTranslator translator = new RecordingTranslator(
                new ResolvedTranslation("en-us", "unused", "en-us", "unused", ResolutionSource.EXACT),
                new ResolvedTranslation("es-mx", "player.key", "es-mx", "<i>Jugador</i>", ResolutionSource.EXACT)
        );
        Player player = playerProxy();

        BaseComponent component = translator.translateAsComponentAsync(player, "player.key").join();

        assertEquals("Jugador", BaseComponent.toPlainText(component));
        assertTrue(textComponent(componentSegments(component).get(0)).isItalic());
        assertSame(player, translator.lastPlayer_);
        assertEquals("player.key", translator.lastPlayerKey_);
    }

    private static List<BaseComponent> componentSegments(BaseComponent component) {
        List<BaseComponent> segments = component.getExtra();
        assertNotNull(segments);
        return segments;
    }

    private static TextComponent textComponent(BaseComponent component) {
        return assertInstanceOf(TextComponent.class, component);
    }

    private static String textOf(BaseComponent component) {
        return textComponent(component).getText();
    }

    private static Player playerProxy() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "PlayerProxy";
                            default -> null;
                        };
                    }

                    Class<?> returnType = method.getReturnType();
                    if (!returnType.isPrimitive()) {
                        return null;
                    }
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == byte.class) {
                        return (byte) 0;
                    }
                    if (returnType == short.class) {
                        return (short) 0;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    if (returnType == float.class) {
                        return 0.0F;
                    }
                    if (returnType == double.class) {
                        return 0.0D;
                    }
                    if (returnType == char.class) {
                        return '\0';
                    }
                    return null;
                }
        );
    }

    private static final class RecordingTranslator implements Translator {

        private ResolvedTranslation localeResult_;
        private final ResolvedTranslation playerResult_;
        private String lastLocale_;
        private String lastLocaleKey_;
        private Placeholder[] lastLocaleArgs_ = new Placeholder[0];
        private Player lastPlayer_;
        private String lastPlayerKey_;
        private Placeholder[] lastPlayerArgs_ = new Placeholder[0];


        private RecordingTranslator(ResolvedTranslation localeResult, ResolvedTranslation playerResult) {
            this.localeResult_ = Objects.requireNonNull(localeResult, "localeResult");
            this.playerResult_ = Objects.requireNonNull(playerResult, "playerResult");
        }

        @Override
        public String getPath() {
            return "";
        }

        @Override
        public BaseRunical getRoot() {
            return null;
        }

        @Override
        public Translator getChild(String segment) {
            return this;
        }

        @Override
        public String translateOrDefault(String locale, String key, String defaultValue, Placeholder... args) {
            ResolvedTranslation resolved = this.resolve(locale, key, args);
            return resolved.found() ? resolved.value() : defaultValue;
        }

        @Override
        public CompletableFuture<String> translateOrDefaultAsync(String locale, String key, String defaultValue, Placeholder... args) {
            return CompletableFuture.completedFuture(this.translateOrDefault(locale, key, defaultValue, args));
        }

        @Override
        public ResolvedTranslation resolve(String locale, String key, Placeholder... args) {
            this.lastLocale_ = locale;
            this.lastLocaleKey_ = key;
            this.lastLocaleArgs_ = args;
            return this.localeResult_;
        }

        @Override
        public CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key, Placeholder... args) {
            return CompletableFuture.completedFuture(this.resolve(locale, key, args));
        }

        @Override
        public String translateOrDefault(Player player, String key, String defaultValue, Placeholder... args) {
            ResolvedTranslation resolved = this.resolve(player, key, args);
            return resolved.found() ? resolved.value() : defaultValue;
        }

        @Override
        public CompletableFuture<String> translateOrDefaultAsync(Player player, String key, String defaultValue, Placeholder... args) {
            return CompletableFuture.completedFuture(this.translateOrDefault(player, key, defaultValue, args));
        }

        @Override
        public ResolvedTranslation resolve(Player player, String key, Placeholder... args) {
            this.lastPlayer_ = player;
            this.lastPlayerKey_ = key;
            this.lastPlayerArgs_ = args;
            return this.playerResult_;
        }

        @Override
        public CompletableFuture<ResolvedTranslation> resolveAsync(Player player, String key, Placeholder... args) {
            return CompletableFuture.completedFuture(this.resolve(player, key, args));
        }

        @Override
        public String formatList(Player player, Collection<?> items, ListStyle style) {
            return items.stream().map(String::valueOf).reduce((left, right) -> left + ", " + right).orElse("");
        }

        @Override
        public CompletableFuture<Boolean> sendTranslation(Player player, String key, Placeholder... args) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> sendTranslationOrDefault(Player player, String key, String defaultValue, Placeholder... args) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
