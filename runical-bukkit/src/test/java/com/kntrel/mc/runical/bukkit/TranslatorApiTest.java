package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.BaseTranslator;
import com.kntrel.mc.runical.core.Placeholder;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslatorApiTest {

    @Test
    void exposesCovariantChildTranslatorOnPublicApi() throws Exception {
        assertTrue(BaseTranslator.class.isAssignableFrom(Translator.class));
        assertEquals(Translator.class, Translator.class.getMethod("getChild", String.class).getReturnType());
        assertEquals(Translator.class, Runical.class.getMethod("getChild", String.class).getReturnType());
    }

    @Test
    void exposesBukkitRootMountApi() throws Exception {
        assertEquals(Runical.class, Runical.class.getMethod("mount", String.class, String.class).getReturnType());
        assertEquals(Runical.class, Runical.class.getMethod("mount", Translator.class, String.class).getReturnType());
        assertEquals(Runical.class, Runical.class.getMethod("mount", Translator.class, Translator.class, String.class).getReturnType());
    }

    @Test
    void exposesComponentTranslationOverloadsOnPublicApi() throws Exception {
        assertEquals(BaseComponent.class, Translator.class.getMethod(
                "translateAsComponent", String.class, String.class, Placeholder[].class
        ).getReturnType());
        assertEquals(BaseComponent.class, Translator.class.getMethod(
                "translateAsComponent", String.class, String.class
        ).getReturnType());
        assertEquals(CompletableFuture.class, Translator.class.getMethod(
                "translateAsComponentAsync", String.class, String.class, Placeholder[].class
        ).getReturnType());
        assertEquals(CompletableFuture.class, Translator.class.getMethod(
                "translateAsComponentAsync", String.class, String.class
        ).getReturnType());

        assertEquals(BaseComponent.class, Translator.class.getMethod(
                "translateAsComponent", Player.class, String.class, Placeholder[].class
        ).getReturnType());
        assertEquals(BaseComponent.class, Translator.class.getMethod(
                "translateAsComponent", Player.class, String.class
        ).getReturnType());
        assertEquals(CompletableFuture.class, Translator.class.getMethod(
                "translateAsComponentAsync", Player.class, String.class, Placeholder[].class
        ).getReturnType());
        assertEquals(CompletableFuture.class, Translator.class.getMethod(
                "translateAsComponentAsync", Player.class, String.class
        ).getReturnType());
    }
}
