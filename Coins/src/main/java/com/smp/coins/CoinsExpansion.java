package com.smp.coins;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Placeholders:
 *   %coins_balance%            -> 12500
 *   %coins_balance_formatted%  -> 12,500
 *   %coins_icon%               -> the coin icon from config.yml
 */
public final class CoinsExpansion extends PlaceholderExpansion {

    private final CoinsPlugin plugin;

    public CoinsExpansion(CoinsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "coins";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SMP";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        switch (params.toLowerCase(Locale.ROOT)) {
            case "icon":
                return plugin.messages().icon();
            case "balance":
                return player == null ? "0" : String.valueOf(plugin.coins().get(player.getUniqueId()));
            case "balance_formatted":
                return player == null ? "0" : Text.number(plugin.coins().get(player.getUniqueId()));
            default:
                return null;
        }
    }
}
