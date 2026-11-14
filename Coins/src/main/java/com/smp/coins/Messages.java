package com.smp.coins;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/** Reads messages from config.yml and fills in placeholders. Always reads live, so /coins reload works. */
public final class Messages {

    private final CoinsPlugin plugin;

    public Messages(CoinsPlugin plugin) {
        this.plugin = plugin;
    }

    public String icon() {
        return plugin.getConfig().getString("currency.icon", "⛁");
    }

    /**
     * Replaces {prefix}, {icon} and any extra key/value pairs, e.g. fill("{amount}", "amount", "5").
     */
    public String fill(String text, String... pairs) {
        FileConfiguration cfg = plugin.getConfig();
        String out = text == null ? "" : text;
        out = out.replace("{prefix}", cfg.getString("messages.prefix", ""));
        out = out.replace("{icon}", icon());
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return out;
    }

    public String raw(String key, String... pairs) {
        String text = plugin.getConfig().getString("messages." + key);
        if (text == null) {
            text = "&cMissing message '" + key + "' in config.yml";
        }
        return fill(text, pairs);
    }

    public Component get(String key, String... pairs) {
        return Text.color(raw(key, pairs));
    }

    public void send(CommandSender to, String key, String... pairs) {
        String text = raw(key, pairs);
        if (text.isBlank()) {
            return;
        }
        to.sendMessage(Text.color(text));
    }
}
