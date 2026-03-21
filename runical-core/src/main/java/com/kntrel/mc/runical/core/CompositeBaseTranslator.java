package com.kntrel.mc.runical.core;

import com.kntrel.mc.runical.core.internal.PlaceholderRenderer;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class CompositeBaseTranslator implements BaseTranslator {

    //FIELDS
    private final BaseTranslator primary_;
    private final BaseRunical root_;
    private final String path_;
    private final MountNode mounts_;
    private final ConcurrentHashMap<String, BaseTranslator> children_;


    //CONSTRUCTOR
    private CompositeBaseTranslator(BaseTranslator primary, String path, MountNode mounts) {
        this.primary_ = Objects.requireNonNull(primary, "primary");
        this.root_ = primary.getRoot();
        this.path_ = path;
        this.mounts_ = Objects.requireNonNull(mounts, "mounts");
        this.children_ = new ConcurrentHashMap<>();
    }


    static BaseTranslator create(BaseTranslator primary, Map<String, BaseTranslator> mounts) {
        if (mounts.isEmpty()) {
            return primary;
        }
        return new CompositeBaseTranslator(primary, primary.getPath(), buildMounts(mounts));
    }

    @Override
    public String getPath() {
        return this.path_;
    }

    @Override
    public BaseRunical getRoot() {
        return this.root_;
    }

    @Override
    public BaseTranslator getChild(String segment) {
        String normalizedSegment = Translators.requireSegment(segment);
        return this.children_.computeIfAbsent(normalizedSegment, this::createChild);
    }

    @Override
    public String translateOrDefault(String locale, String key, String defaultValue, Placeholder... args) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        String value = this.resolve(locale, key, args).value();
        return value != null ? value : renderMessage(defaultValue, args);
    }

    @Override
    public CompletableFuture<String> translateOrDefaultAsync(String locale, String key, String defaultValue, Placeholder... args) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        return this.resolveAsync(locale, key, args).thenApply(resolved -> {
            String value = resolved.value();
            return value != null ? value : renderMessage(defaultValue, args);
        });
    }

    @Override
    public ResolvedTranslation resolve(String locale, String key, Placeholder... args) {
        MountedRoute route = this.mounts_.route(key);
        if (route == null) {
            return this.primary_.resolve(locale, key, args);
        }
        if (route.relativeKey_ == null) {
            return route.translator_.getRoot().resolve(locale, route.translator_.getPath(), args);
        }
        return route.translator_.resolve(locale, route.relativeKey_, args);
    }

    @Override
    public CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key, Placeholder... args) {
        MountedRoute route = this.mounts_.route(key);
        if (route == null) {
            return this.primary_.resolveAsync(locale, key, args);
        }
        if (route.relativeKey_ == null) {
            return route.translator_.getRoot().resolveAsync(locale, route.translator_.getPath(), args);
        }
        return route.translator_.resolveAsync(locale, route.relativeKey_, args);
    }

    private BaseTranslator createChild(String segment) {
        MountNode childMounts = this.mounts_.child(segment);
        if (childMounts == null) {
            return this.primary_.getChild(segment);
        }

        BaseTranslator mountedTranslator = childMounts.mountedTranslator_;
        if (mountedTranslator != null) {
            return mountedTranslator;
        }

        return new CompositeBaseTranslator(this.primary_.getChild(segment), null, childMounts);
    }

    private static String renderMessage(String template, Placeholder... args) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (Placeholder argument : args) {
            if (argument == null) {
                continue;
            }
            placeholders.put(argument.name(), String.valueOf(argument.value()));
        }
        return PlaceholderRenderer.render(template, placeholders::get);
    }

    private static MountNode buildMounts(Map<String, BaseTranslator> mounts) {
        MountNodeBuilder root = new MountNodeBuilder();
        mounts.forEach(root::put);
        return root.build();
    }


    static final class MountNode {
        private final BaseTranslator mountedTranslator_;
        private final Map<String, MountNode> children_;

        private MountNode(BaseTranslator mountedTranslator, Map<String, MountNode> children) {
            this.mountedTranslator_ = mountedTranslator;
            this.children_ = children;
        }

        private MountNode child(String segment) {
            return this.children_.get(segment);
        }

        private MountedRoute route(String key) {
            String normalizedKey = Translators.requireKey(key);
            String[] segments = normalizedKey.split("\\.", -1);

            MountNode current = this;
            for (int index = 0; index < segments.length; index++) {
                current = current.children_.get(segments[index]);
                if (current == null) {
                    return null;
                }
                if (current.mountedTranslator_ != null) {
                    String relativeKey = index == segments.length - 1
                            ? null
                            : String.join(".", Arrays.copyOfRange(segments, index + 1, segments.length));
                    return new MountedRoute(current.mountedTranslator_, relativeKey);
                }
            }
            return null;
        }
    }

    private static final class MountedRoute {
        private final BaseTranslator translator_;
        private final String relativeKey_;

        private MountedRoute(BaseTranslator translator, String relativeKey) {
            this.translator_ = translator;
            this.relativeKey_ = relativeKey;
        }
    }

    private static final class MountNodeBuilder {
        private BaseTranslator mountedTranslator_;
        private final LinkedHashMap<String, MountNodeBuilder> children_ = new LinkedHashMap<>();

        private void put(String path, BaseTranslator translator) {
            MountNodeBuilder current = this;
            for (String segment : path.split("\\.")) {
                current = current.children_.computeIfAbsent(segment, ignored -> new MountNodeBuilder());
            }
            current.mountedTranslator_ = translator;
        }

        private MountNode build() {
            LinkedHashMap<String, MountNode> builtChildren = new LinkedHashMap<>();
            this.children_.forEach((segment, child) -> builtChildren.put(segment, child.build()));
            return new MountNode(this.mountedTranslator_, builtChildren.isEmpty() ? Map.of() : Map.copyOf(builtChildren));
        }
    }
}
