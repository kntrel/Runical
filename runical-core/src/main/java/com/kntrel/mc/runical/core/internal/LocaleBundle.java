package com.kntrel.mc.runical.core.internal;

import com.kntrel.mc.runical.core.ListStyle;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class LocaleBundle {

    //FIELDS
    private final String locale_;
    private final Map<String, String> messages_;
    private final Map<ListStyle, ListFormat> listFormats_;
    private final long version_;
    private final AtomicLong lastAccessSequence_;


    //CONSTRUCTOR
    public LocaleBundle(String locale, Map<String, String> messages, Map<ListStyle, ListFormat> listFormats, long version) {
        this.locale_ = locale;
        this.messages_ = Collections.unmodifiableMap(new java.util.HashMap<>(messages));
        EnumMap<ListStyle, ListFormat> formats = new EnumMap<>(ListStyle.class);
        formats.putAll(listFormats);
        this.listFormats_ = Collections.unmodifiableMap(formats);
        this.version_ = version;
        this.lastAccessSequence_ = new AtomicLong();
    }


    //GETTERS
    public String locale() {
        return this.locale_;
    }
    public String message(String key) {
        return this.messages_.get(key);
    }
    public Optional<ListFormat> listFormat(ListStyle style) {
        return Optional.ofNullable(this.listFormats_.get(style));
    }
    public long version() {
        return this.version_;
    }
    public long lastAccessSequence() {
        return this.lastAccessSequence_.get();
    }
    public void touch(long accessSequence) {
        this.lastAccessSequence_.set(accessSequence);
    }
}
