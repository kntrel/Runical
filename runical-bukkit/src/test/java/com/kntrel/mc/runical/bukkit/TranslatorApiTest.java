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
}
