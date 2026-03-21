package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.BaseTranslator;
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
    void exposesBukkitTranslatorCompositionApi() throws Exception {
        assertEquals(Translators.Builder.class, Translators.class.getMethod("compose", Translator.class).getReturnType());
        assertEquals(Translators.Builder.class, Translators.Builder.class.getMethod("mount", String.class, Translator.class).getReturnType());
        assertEquals(Translator.class, Translators.Builder.class.getMethod("build").getReturnType());
    }
}
