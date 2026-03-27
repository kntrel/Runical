/**
 * Core Runical API for locale resolution, placeholder rendering, and locale-aware list
 * formatting.
 *
 * <p>The main entry point is {@link com.kntrel.mc.runical.core.BaseRunical}, which implements
 * {@link com.kntrel.mc.runical.core.BaseTranslator}, resolves translations from a directory of
 * locale YAML files, and exposes synchronous and asynchronous lookup methods. Child translators
 * created through {@link com.kntrel.mc.runical.core.BaseTranslator#getChild(String)} qualify keys
 * relative to a fixed path while delegating resolution back to the root resolver. Additional
 * root-level query aliases can be registered through
 * {@link com.kntrel.mc.runical.core.BaseRunical#mount(String, String)}.
 * {@link com.kntrel.mc.runical.core.RunicalOptions} configures caching and async behavior, while
 * {@link com.kntrel.mc.runical.core.ResolvedTranslation},
 * {@link com.kntrel.mc.runical.core.placeholder.Placeholder},
 * {@link com.kntrel.mc.runical.core.placeholder.BundledPlaceholder},
 * {@link com.kntrel.mc.runical.core.placeholder.Translatable}, and
 * {@link com.kntrel.mc.runical.core.placeholder.TranslationProperty} model lookup results and
 * translation-time placeholder inputs.
 */
package com.kntrel.mc.runical.core;
