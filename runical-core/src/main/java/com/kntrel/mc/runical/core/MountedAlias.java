package com.kntrel.mc.runical.core;

final class MountedAlias {
    private final String aliasPath_;
    private final String canonicalPath_;

    MountedAlias(String aliasPath, String canonicalPath) {
        this.aliasPath_ = aliasPath;
        this.canonicalPath_ = canonicalPath;
    }

    String aliasPath() {
        return this.aliasPath_;
    }

    boolean matches(String path) {
        return path.equals(this.aliasPath_) || path.startsWith(this.aliasPath_ + ".");
    }

    String rewrite(String path) {
        if (path.equals(this.aliasPath_)) {
            return this.canonicalPath_;
        }
        return this.canonicalPath_ + path.substring(this.aliasPath_.length());
    }
}
