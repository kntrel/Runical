package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.BaseRunical;
import com.kntrel.mc.runical.core.BaseTranslator;
import com.kntrel.mc.runical.core.ListStyle;
import com.kntrel.mc.runical.core.Placeholder;
import com.kntrel.mc.runical.core.ResolvedTranslation;
import com.kntrel.mc.runical.core.RunicalOptions;
import com.kntrel.mc.runical.core.internal.LocaleSupport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class Runical extends BaseRunical implements Translator, Listener {

    //FIELDS
    private final Plugin plugin_;
    private final String langsPath_;
    private final ConcurrentHashMap<UUID, String> playerLocales_;
    private volatile BundledLocaleIndex bundledLocaleIndex_;
    private final Object lock_;


    //CONSTRUCTOR
    public Runical(Plugin plugin, String languagesFolderRelativePath) {
        this(plugin, languagesFolderRelativePath, RunicalOptions.builder().build());
    }

    public Runical(Plugin plugin, String languagesFolderRelativePath, RunicalOptions options) {
        super(resolveLanguagePath(plugin, normalizeRelativePath(languagesFolderRelativePath)), options);
        this.plugin_ = Objects.requireNonNull(plugin, "plugin");
        this.langsPath_ = normalizeRelativePath(languagesFolderRelativePath);
        this.playerLocales_ = new ConcurrentHashMap<>();
        this.lock_ = new Object();

        plugin.getServer().getOnlinePlayers().forEach(this::trackPlayer);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** {@inheritDoc} */
    @Override
    public Translator getChild(String segment) {
        return (Translator) super.getChild(segment);
    }

    /** {@inheritDoc} */
    @Override
    public String translateOrDefault(Player player, String key, String defaultValue, Placeholder... args) {
        return this.translateOrDefault(localeOf(player), key, defaultValue, args);
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<String> translateOrDefaultAsync(Player player, String key, String defaultValue, Placeholder... args) {
        return this.translateOrDefaultAsync(localeOf(player), key, defaultValue, args);
    }

    /** {@inheritDoc} */
    @Override
    public ResolvedTranslation resolve(Player player, String key, Placeholder... args) {
        return this.resolve(localeOf(player), key, args);
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<ResolvedTranslation> resolveAsync(Player player, String key, Placeholder... args) {
        return this.resolveAsync(localeOf(player), key, args);
    }

    /** {@inheritDoc} */
    @Override
    public String formatList(Player player, Collection<?> items, ListStyle style) {
        return this.formatList(localeOf(player), items, style);
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Boolean> sendTranslation(Player player, String key, Placeholder... args) {
        String locale = localeOf(player);
        return this.sendMessage(player, resolveAsync(locale, key, args).thenApply(ResolvedTranslation::orKey));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Boolean> sendTranslationOrDefault(Player player, String key, String defaultValue, Placeholder... args) {
        String locale = localeOf(player);
        return sendMessage(player, translateOrDefaultAsync(locale, key, defaultValue, args));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        trackPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        untrackPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerLocaleChange(PlayerLocaleChangeEvent event) {
        updatePlayerLocale(event.getPlayer().getUniqueId(), event.getLocale());
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        this.playerLocales_.forEach((uuid, locale) -> releaseLocale(locale));
        this.playerLocales_.clear();
        super.close();
    }

    @Override
    protected Optional<Path> resolveMissingLocaleFile(String locale) {
        String resourcePath = this.bundledLocaleIndex().resourcePath(locale).orElse(null);
        if (resourcePath == null) {
            return Optional.empty();
        }

        Path targetPath = this.plugin_.getDataFolder().toPath().resolve(resourcePath).toAbsolutePath().normalize();
        return this.copyBundledResource(resourcePath, targetPath, "locale");
    }

    @Override
    protected Optional<Path> resolveMissingTaggedFile(Path localeFile, Path referencedPath) {
        Path dataFolderPath = this.plugin_.getDataFolder().toPath().toAbsolutePath().normalize();
        Path normalizedReference = referencedPath.toAbsolutePath().normalize();

        if (!normalizedReference.startsWith(dataFolderPath)) {
            return Optional.empty();
        }

        String resourcePath = dataFolderPath.relativize(normalizedReference).toString().replace('\\', '/');
        if (resourcePath.isBlank()) {
            return Optional.empty();
        }

        return this.copyBundledResource(resourcePath, normalizedReference, "tagged file");
    }

    private Optional<Path> copyBundledResource(String resourcePath, Path targetPath, String resourceType) {
        if (Files.isRegularFile(targetPath)) {
            return Optional.of(targetPath);
        }

        try (InputStream inputStream = this.plugin_.getResource(resourcePath)) {
            if (inputStream == null) {
                return Optional.empty();
            }

            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(inputStream, targetPath);
            return Optional.of(targetPath);
        } catch (FileAlreadyExistsException ignored) {
            return Optional.of(targetPath);
        } catch (IOException exception) {
            this.plugin_.getLogger().warning(
                    "Unable to copy bundled " + resourceType + " '" + resourcePath + "' to '" + targetPath + "': " + exception.getMessage()
            );
            return Optional.empty();
        }
    }

    @Override
    protected List<String> additionalLocalesForLanguage(String language) {
        return this.bundledLocaleIndex().localesForLanguage(language);
    }

    @Override
    protected BaseTranslator createChildTranslator(String path) {
        return new TranslatorNode(this, path);
    }

    @Override
    protected Translator childTranslator(String path) {
        return (Translator) super.childTranslator(path);
    }

    String localeOf(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        if (Bukkit.isPrimaryThread()) {
            return updatePlayerLocale(playerId, player.getLocale());
        }

        String cachedLocale = this.playerLocales_.get(playerId);
        return cachedLocale != null ? cachedLocale : getDefaultLocale();
    }

    private void trackPlayer(Player player) {
        updatePlayerLocale(player.getUniqueId(), player.getLocale());
    }

    private void untrackPlayer(Player player) {
        String previousLocale = this.playerLocales_.remove(player.getUniqueId());
        if (previousLocale != null) {
            releaseLocale(previousLocale);
        }
    }

    private String updatePlayerLocale(UUID playerId, String locale) {
        String normalizedLocale = normalizeLocale(locale);
        this.playerLocales_.compute(playerId, (ignored, previousLocale) -> {
            if (previousLocale == null) {
                retainLocale(normalizedLocale);
                return normalizedLocale;
            }
            if (!previousLocale.equals(normalizedLocale)) {
                replaceRetainedLocale(previousLocale, normalizedLocale);
            }
            return normalizedLocale;
        });
        return normalizedLocale;
    }

    CompletableFuture<Boolean> sendMessage(Player player, CompletableFuture<String> messageFuture) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();

        return messageFuture.thenCompose(message -> {
            CompletableFuture<Boolean> sentFuture = new CompletableFuture<>();
            this.plugin_.getServer().getScheduler().runTask(this.plugin_, () -> {
                Player target = this.plugin_.getServer().getPlayer(playerId);
                if (target == null || !target.isOnline()) {
                    sentFuture.complete(false);
                    return;
                }

                target.sendMessage(message);
                sentFuture.complete(true);
            });
            return sentFuture;
        });
    }

    private BundledLocaleIndex bundledLocaleIndex() {
        BundledLocaleIndex cachedIndex = this.bundledLocaleIndex_;
        if (cachedIndex != null) {
            return cachedIndex;
        }

        synchronized (this.lock_) {
            if (this.bundledLocaleIndex_ == null) {
                this.bundledLocaleIndex_ = this.scanBundledLocaleIndex();
            }
            return this.bundledLocaleIndex_;
        }
    }

    private BundledLocaleIndex scanBundledLocaleIndex() {
        try {
            CodeSource codeSource = this.plugin_.getClass().getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return BundledLocaleIndex.empty();
            }

            Path sourcePath = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
            if (Files.isDirectory(sourcePath)) {
                return this.scanBundledLocalesInDirectory(sourcePath.resolve(this.langsPath_));
            }
            if (Files.isRegularFile(sourcePath)) {
                return this.scanBundledLocalesInJar(sourcePath);
            }
        } catch (Exception exception) {
            this.plugin_.getLogger().warning(
                    "Unable to inspect bundled Runical locales in '" + this.langsPath_ + "': " + exception.getMessage()
            );
        }

        return BundledLocaleIndex.empty();
    }

    private BundledLocaleIndex scanBundledLocalesInDirectory(Path resourceDirectory) {
        if (!Files.isDirectory(resourceDirectory)) {
            return BundledLocaleIndex.empty();
        }

        List<String> resourcePaths;
        try (Stream<Path> stream = Files.list(resourceDirectory)) {
            resourcePaths = stream
                    .filter(Files::isRegularFile)
                    .filter(Runical::isYamlFile)
                    .sorted((left, right) -> left.getFileName().toString().compareToIgnoreCase(right.getFileName().toString()))
                    .map(path -> this.langsPath_ + "/" + path.getFileName())
                    .toList();
        } catch (IOException exception) {
            this.plugin_.getLogger().warning(
                    "Unable to scan bundled Runical locale directory '" + resourceDirectory + "': " + exception.getMessage()
            );
            return BundledLocaleIndex.empty();
        }

        return this.buildBundledLocaleIndex(resourcePaths);
    }

    private BundledLocaleIndex scanBundledLocalesInJar(Path jarPath) {
        List<String> resourcePaths = new ArrayList<>();
        String prefix = this.langsPath_ + "/";

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.startsWith(prefix)) {
                    continue;
                }

                String nestedName = name.substring(prefix.length());
                if (nestedName.isBlank() || nestedName.contains("/")) {
                    continue;
                }
                if (!isYamlResourceName(nestedName)) {
                    continue;
                }
                resourcePaths.add(name);
            }
        } catch (IOException exception) {
            this.plugin_.getLogger().warning(
                    "Unable to scan bundled Runical locale jar '" + jarPath + "': " + exception.getMessage()
            );
            return BundledLocaleIndex.empty();
        }

        Collections.sort(resourcePaths, String.CASE_INSENSITIVE_ORDER);
        return this.buildBundledLocaleIndex(resourcePaths);
    }

    private BundledLocaleIndex buildBundledLocaleIndex(List<String> resourcePaths) {
        Map<String, String> localeResources = new HashMap<>();
        Map<String, List<String>> localesByLanguage = new HashMap<>();

        for (String resourcePath : resourcePaths) {
            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            int extensionSeparator = fileName.lastIndexOf('.');
            String rawLocale = extensionSeparator >= 0 ? fileName.substring(0, extensionSeparator) : fileName;

            String normalizedLocale;
            try {
                normalizedLocale = this.normalizeLocale(rawLocale);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            if (localeResources.putIfAbsent(normalizedLocale, resourcePath) != null) {
                continue;
            }

            localesByLanguage.computeIfAbsent(LocaleSupport.languageOf(normalizedLocale), ignored -> new ArrayList<>())
                    .add(normalizedLocale);
        }

        for (List<String> locales : localesByLanguage.values()) {
            Collections.sort(locales);
        }

        return new BundledLocaleIndex(localeResources, localesByLanguage);
    }

    private static String normalizeRelativePath(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        String normalized = relativePath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("languagesFolderRelativePath must not be blank.");
        }
        return normalized;
    }

    private static Path resolveLanguagePath(Plugin plugin, String relativePath) {
        Objects.requireNonNull(plugin, "plugin");
        return plugin.getDataFolder().toPath().resolve(relativePath).normalize();
    }

    private static boolean isYamlFile(Path path) {
        return isYamlResourceName(path.getFileName().toString());
    }

    private static boolean isYamlResourceName(String name) {
        String normalizedName = name.toLowerCase(java.util.Locale.ROOT);
        return normalizedName.endsWith(".yml") || normalizedName.endsWith(".yaml");
    }
}
