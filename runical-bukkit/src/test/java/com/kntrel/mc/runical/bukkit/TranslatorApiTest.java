package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.BaseTranslator;
import com.kntrel.mc.runical.core.argument.Argument;
import com.kntrel.mc.runical.core.dsl.AsyncTranslationJob;
import com.kntrel.mc.runical.core.dsl.AsyncTerminalTranslationJob;
import com.kntrel.mc.runical.core.dsl.TerminalTranslationJob;
import com.kntrel.mc.runical.core.dsl.TranslationJob;
import com.kntrel.mc.runical.bukkit.dsl.PlayerTerminalTranslationJob;
import com.kntrel.mc.runical.bukkit.dsl.PlayerTranslationJob;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

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
    void exposesJobBasedTranslationApiOnPublicApi() throws Exception {
        assertEquals(TranslationJob.class, BaseTranslator.class.getMethod(
                "translate", String.class, String.class
        ).getReturnType());
        assertEquals(com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class, Translator.class.getMethod(
                "translate", String.class, String.class
        ).getReturnType());
        assertEquals(PlayerTranslationJob.class, Translator.class.getMethod(
                "translate", Player.class, String.class
        ).getReturnType());
    }

    @Test
    void exposesFluentArgumentDslOnPublicApi() throws Exception {
        assertEquals(TranslationJob.class, TranslationJob.class.getMethod(
                "arguments", Argument[].class
        ).getReturnType());
        assertEquals(TranslationJob.class, TranslationJob.class.getMethod(
                "arguments", java.util.Collection.class
        ).getReturnType());
        assertEquals(TranslationJob.class, TranslationJob.class.getMethod(
                "argument", String.class, Object.class
        ).getReturnType());
        assertEquals(TranslationJob.class, TranslationJob.class.getMethod(
                "argument", Argument.class
        ).getReturnType());

        assertEquals(com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class, com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class.getMethod(
                "arguments", Argument[].class
        ).getReturnType());
        assertEquals(com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class, com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class.getMethod(
                "arguments", java.util.Collection.class
        ).getReturnType());
        assertEquals(com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class, com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class.getMethod(
                "argument", String.class, Object.class
        ).getReturnType());
        assertEquals(com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class, com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class.getMethod(
                "argument", Argument.class
        ).getReturnType());

        assertEquals(PlayerTranslationJob.class, PlayerTranslationJob.class.getMethod(
                "arguments", Argument[].class
        ).getReturnType());
        assertEquals(PlayerTranslationJob.class, PlayerTranslationJob.class.getMethod(
                "arguments", java.util.Collection.class
        ).getReturnType());
        assertEquals(PlayerTranslationJob.class, PlayerTranslationJob.class.getMethod(
                "argument", String.class, Object.class
        ).getReturnType());
        assertEquals(PlayerTranslationJob.class, PlayerTranslationJob.class.getMethod(
                "argument", Argument.class
        ).getReturnType());
    }

    @Test
    void narrowsMissHandlingToTerminalSurfaces() throws Exception {
        assertEquals(TerminalTranslationJob.class, TranslationJob.class.getMethod("orNull").getReturnType());
        assertEquals(AsyncTerminalTranslationJob.class, TerminalTranslationJob.class.getMethod("async").getReturnType());
        assertEquals(AsyncTranslationJob.class, TranslationJob.class.getMethod("async").getReturnType());

        assertEquals(
                com.kntrel.mc.runical.bukkit.dsl.TerminalTranslationJob.class,
                com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class.getMethod("orNull").getReturnType()
        );
        assertEquals(
                com.kntrel.mc.runical.bukkit.dsl.AsyncTerminalTranslationJob.class,
                com.kntrel.mc.runical.bukkit.dsl.TerminalTranslationJob.class.getMethod("async").getReturnType()
        );
        assertEquals(
                com.kntrel.mc.runical.bukkit.dsl.AsyncTranslationJob.class,
                com.kntrel.mc.runical.bukkit.dsl.TranslationJob.class.getMethod("async").getReturnType()
        );
        assertEquals(PlayerTerminalTranslationJob.class, PlayerTranslationJob.class.getMethod("orNull").getReturnType());
        assertEquals(
                java.util.concurrent.CompletableFuture.class,
                com.kntrel.mc.runical.bukkit.dsl.TerminalTranslationJob.class.getMethod(
                        "send", Player.class, ChatMessageType.class
                ).getReturnType()
        );
        assertEquals(
                java.util.concurrent.CompletableFuture.class,
                PlayerTerminalTranslationJob.class.getMethod("send", ChatMessageType.class).getReturnType()
        );
    }
}
