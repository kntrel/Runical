package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.BaseRunical;
import com.kntrel.mc.runical.core.ListStyle;
import com.kntrel.mc.runical.core.Placeholder;
import com.kntrel.mc.runical.core.ResolvedTranslation;
import com.kntrel.mc.runical.core.RunicalOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class Runical extends BaseRunical implements Listener {

    private final Plugin plugin;
    private final ConcurrentHashMap<UUID, String> playerLocales;

    public Runical(Plugin plugin, String languagesFolderRelativePath) {
        this(plugin, languagesFolderRelativePath, RunicalOptions.builder().build());
    }

    public Runical(Plugin plugin, String languagesFolderRelativePath, RunicalOptions options) {
        super(resolveLanguagePath(plugin, languagesFolderRelativePath), options);
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerLocales = new ConcurrentHashMap<>();

        plugin.getServer().getOnlinePlayers().forEach(this::trackPlayer);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public String translate(Player player, String key, Placeholder... args) {
        return translate(localeOf(player), key, args);
    }

    public String translate(Player player, String key) {
        return translate(player, key, new Placeholder[0]);
    }

    public CompletableFuture<String> translateAsync(Player player, String key, Placeholder... args) {
        return this.translateAsync(localeOf(player), key, args);
    }

    public CompletableFuture<String> translateAsync(Player player, String key) {
        return this.translateAsync(player, key, new Placeholder[0]);
    }

    public String translateOrNull(Player player, String key, Placeholder... args) {
        return this.translateOrNull(localeOf(player), key, args);
    }

    public String translateOrNull(Player player, String key) {
        return this.translateOrNull(player, key, new Placeholder[0]);
    }

    public String translateOrDefault(Player player, String key, String defaultValue, Placeholder... args) {
        return this.translateOrDefault(localeOf(player), key, defaultValue, args);
    }

    public String translateOrDefault(Player player, String key, String defaultValue) {
        return this.translateOrDefault(player, key, defaultValue, new Placeholder[0]);
    }

    public CompletableFuture<String> translateOrNullAsync(Player player, String key, Placeholder... args) {
        return this.translateOrNullAsync(localeOf(player), key, args);
    }

    public CompletableFuture<String> translateOrNullAsync(Player player, String key) {
        return this.translateOrNullAsync(player, key, new Placeholder[0]);
    }

    public CompletableFuture<String> translateOrDefaultAsync(Player player, String key, String defaultValue, Placeholder... args) {
        return this.translateOrDefaultAsync(localeOf(player), key, defaultValue, args);
    }

    public CompletableFuture<String> translateOrDefaultAsync(Player player, String key, String defaultValue) {
        return this.translateOrDefaultAsync(player, key, defaultValue, new Placeholder[0]);
    }

    public ResolvedTranslation resolve(Player player, String key, Placeholder... args) {
        return this.resolve(localeOf(player), key, args);
    }

    public ResolvedTranslation resolve(Player player, String key) {
        return this.resolve(player, key, new Placeholder[0]);
    }

    public CompletableFuture<ResolvedTranslation> resolveAsync(Player player, String key, Placeholder... args) {
        return this.resolveAsync(localeOf(player), key, args);
    }

    public CompletableFuture<ResolvedTranslation> resolveAsync(Player player, String key) {
        return this.resolveAsync(player, key, new Placeholder[0]);
    }

    public String formatList(Player player, Collection<?> items) {
        return this.formatList(player, items, ListStyle.AND);
    }

    public String formatList(Player player, Collection<?> items, ListStyle style) {
        return this.formatList(localeOf(player), items, style);
    }

    public CompletableFuture<Boolean> sendTranslation(Player player, String key, Placeholder... args) {
        String locale = localeOf(player);
        return this.sendMessage(player, resolveAsync(locale, key, args).thenApply(ResolvedTranslation::orKey));
    }

    public CompletableFuture<Boolean> sendTranslation(Player player, String key) {
        return sendTranslation(player, key, new Placeholder[0]);
    }

    public CompletableFuture<Boolean> sendTranslationOrDefault(Player player, String key, String defaultValue, Placeholder... args) {
        String locale = localeOf(player);
        return sendMessage(player, translateOrDefaultAsync(locale, key, defaultValue, args));
    }

    public CompletableFuture<Boolean> sendTranslationOrDefault(Player player, String key, String defaultValue) {
        return sendTranslationOrDefault(player, key, defaultValue, new Placeholder[0]);
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
        this.playerLocales.forEach((uuid, locale) -> releaseLocale(locale));
        this.playerLocales.clear();
        super.close();
    }

    private String localeOf(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        if (Bukkit.isPrimaryThread()) {
            return updatePlayerLocale(playerId, player.getLocale());
        }

        String cachedLocale = this.playerLocales.get(playerId);
        return cachedLocale != null ? cachedLocale : getDefaultLocale();
    }

    private void trackPlayer(Player player) {
        updatePlayerLocale(player.getUniqueId(), player.getLocale());
    }

    private void untrackPlayer(Player player) {
        String previousLocale = this.playerLocales.remove(player.getUniqueId());
        if (previousLocale != null) {
            releaseLocale(previousLocale);
        }
    }

    private String updatePlayerLocale(UUID playerId, String locale) {
        String normalizedLocale = normalizeLocale(locale);
        this.playerLocales.compute(playerId, (ignored, previousLocale) -> {
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

    private CompletableFuture<Boolean> sendMessage(Player player, CompletableFuture<String> messageFuture) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();

        return messageFuture.thenCompose(message -> {
            CompletableFuture<Boolean> sentFuture = new CompletableFuture<>();
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                Player target = this.plugin.getServer().getPlayer(playerId);
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

    private static Path resolveLanguagePath(Plugin plugin, String relativePath) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isBlank()) {
            throw new IllegalArgumentException("languagesFolderRelativePath must not be blank.");
        }
        return plugin.getDataFolder().toPath().resolve(relativePath).normalize();
    }
}
