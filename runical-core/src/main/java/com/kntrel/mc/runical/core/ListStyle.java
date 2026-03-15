package com.kntrel.mc.runical.core;

public enum ListStyle {

    //CONSTANTS
    AND("and"),
    OR("or");


    //FIELDS
    private final String configKey;


    //CONSTRUCTOR
    ListStyle(String configKey) {
        this.configKey = configKey;
    }


    //GETTERS
    public String configKey() {
        return this.configKey;
    }
}
