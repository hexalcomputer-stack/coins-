package com.smp.coins;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One single repeating task (runs every second) that pays coins for online time.
 * Uses real time, so server lag does not make players earn slower, and a relog cannot be used to skip ahead.
 */
public final class EarnTask extends BukkitRunnable {

    /** Ignore gaps longer than this (ms), so a frozen server doesn't pay out a huge chunk at once. */
    private static final long MAX_STEP_MS = 5000L;

    private final CoinsPlugin plugin;
    private final Map<UUID, Long> progressMs = new HashMap<>();

    private long lastRun = 0L;
    private long amount;
    private long intervalMs;
    private boolean afkEnabled;
    private long afkMs;
    private boolean actionBar;

    public EarnTask(CoinsPlugin plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    public void reloadSettings() {
        FileConfiguration cfg = plugin.getConfig();
        amount = Math.max(0L, cfg.getLong("earning.amount", 5L));
        intervalMs = Math.max(1L, cfg.getLong("earning.interval-seconds", 60L)) * 1000L;
        afkEnabled = cfg.getBoolean("earning.afk.enabled", false);
        afkMs = Math.max(1L, cfg.getLong("earning.afk.timeout-seconds", 300L)) * 1000L;
        actionBar = cfg.getBoolean("earning.action-bar", true);
    }

    public void forget(UUID id) {
        progressMs.remove(id);
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        long delta = lastRun == 0L ? 0L : Math.min(now - lastRun, MAX_STEP_MS);
        lastRun = now;
        if (delta <= 0L || amount <= 0L) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();

            if (!player.hasPermission("coins.earn")) {
                continue;
            }
            if (afkEnabled && plugin.activity().idleMillis(id) >= afkMs) {
                continue; // AFK players don't earn
            }

            long total = progressMs.merge(id, delta, Long::sum);
            if (total < intervalMs) {
                continue;
            }

            long payouts = total / intervalMs;
            progressMs.put(id, total % intervalMs);

            long earned = payouts * amount;
            plugin.coins().add(id, earned);

            if (actionBar) {
                player.sendActionBar(plugin.messages().get("earn-actionbar", "amount", Text.number(earned)));
            }
            plugin.playSound(player, "sounds.earn");
        }
    }
}
