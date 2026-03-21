package com.kntrel.mc.runical.bukkit;

/**
 * Bukkit-facing translator composition utilities.
 *
 * <p>This specialization delegates the actual mount routing to the core
 * {@link com.kntrel.mc.runical.core.Translators} implementation and then adapts the resulting
 * composed translator back into the Bukkit {@link Translator} contract.
 */
public final class Translators {

    private Translators() {
    }

    /**
     * Starts composing a Bukkit translator view around the given primary translator.
     *
     * @param primary primary translator that owns unprefixed local keys
     * @return a Bukkit composition builder
     */
    public static Builder compose(Translator primary) {
        return new Builder(primary);
    }


    /**
     * Builder for a mounted Bukkit translator composition.
     */
    public static final class Builder {

        //FIELDS
        private final com.kntrel.mc.runical.core.Translators.Builder delegate_;


        //CONSTRUCTOR
        private Builder(Translator primary) {
            this.delegate_ = com.kntrel.mc.runical.core.Translators.compose(primary);
        }


        /**
         * Mounts a canonical Bukkit translator at the given canonical path.
         *
         * @param path canonical mount path
         * @param translator mounted Bukkit translator
         * @return this builder
         */
        public Builder mount(String path, Translator translator) {
            this.delegate_.mount(path, translator);
            return this;
        }

        /**
         * Builds the composed Bukkit translator.
         *
         * @return a Bukkit translator preserving player-aware APIs
         */
        public Translator build() {
            return CompositeTranslator.wrap(this.delegate_.build());
        }
    }
}
