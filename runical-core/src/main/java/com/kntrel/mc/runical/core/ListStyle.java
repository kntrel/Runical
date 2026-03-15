package com.kntrel.mc.runical.core;

/**
 * Conjunction styles supported by {@link BaseRunical#formatList(String, java.util.Collection, ListStyle)}.
 */
public enum ListStyle {

    //CONSTANTS
    /**
     * Formats lists with an "and"-style conjunction.
     */
    AND("and"),

    /**
     * Formats lists with an "or"-style conjunction.
     */
    OR("or");


    //FIELDS
    private final String configKey;


    //CONSTRUCTOR
    ListStyle(String configKey) {
        this.configKey = configKey;
    }


    //GETTERS
    /**
     * Returns the metadata key used under {@code _runical.list_formats}.
     *
     * @return the configuration key for this style
     */
    public String configKey() {
        return this.configKey;
    }
}
