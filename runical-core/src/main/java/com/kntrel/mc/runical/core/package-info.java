/**
 * Core Runical API for locale resolution, placeholder rendering, and locale-aware list
 * formatting.
 *
 * <p>The main entry point is {@link com.kntrel.mc.runical.core.BaseRunical}, which implements
 * {@link com.kntrel.mc.runical.core.BaseTranslator}, resolves translations from a directory of
 * locale YAML files, and exposes synchronous and asynchronous lookup methods. Child translators
 * created through {@link com.kntrel.mc.runical.core.BaseTranslator#getChild(String)} qualify keys
 * relative to a fixed path while delegating resolution back to the root resolver.
 * {@link com.kntrel.mc.runical.core.RunicalOptions} configures caching and async behavior, while
 * {@link com.kntrel.mc.runical.core.ResolvedTranslation} and
 * {@link com.kntrel.mc.runical.core.Placeholder} model lookup results and placeholder inputs.
 */
package com.kntrel.mc.runical.core;
