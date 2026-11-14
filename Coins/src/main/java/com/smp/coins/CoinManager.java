package com.smp.coins;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stores every player's coin balance (by UUID) and saves them to data.yml.
 * Reads are thread-safe because TAB/PlaceholderAPI may ask for balances off the main thread.
 */
public final class CoinManager {

    private final CoinsPlugin plugin;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final Path file;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final Object ioLock = new Object();

    public CoinManager(CoinsPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data.yml");
    }

    // ------------------------------------------------------------------ loading / saving

    public void load() {
        balances.clear();
        if (!Files.exists(file)) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection section = yml.getConfigurationSection("balances");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), Math.max(0L, section.getLong(key)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping invalid UUID in data.yml: " + key);
            }
        }
        plugin.getLogger().info("Loaded " + balances.size() + " coin balances.");
    }

    /** Saves in the background if anything changed. Call from the main thread while the plugin is enabled. */
    public void saveAsync() {
        if (!dirty.getAndSet(false)) {
            return;
        }
        String data = serialize();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    /** Saves right now on the calling thread (used on shutdown). */
    public void saveSync() {
        dirty.set(false);
        write(serialize());
    }

    private String serialize() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            yml.set("balances." + entry.getKey(), entry.getValue());
        }
        return yml.saveToString();
    }

    private void write(String data) {
        synchronized (ioLock) {
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling("data.yml.tmp");
                Files.writeString(tmp, data, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
                dirty.set(true); // try again on the next autosave
            }
        }
    }

    // ------------------------------------------------------------------ balances

    public long get(UUID id) {
        return balances.getOrDefault(id, 0L);
    }

    public boolean has(UUID id) {
        return balances.containsKey(id);
    }

    /** Gives a new player their starting balance (does nothing for known players). */
    public void ensure(UUID id) {
        long start = Math.max(0L, plugin.getConfig().getLong("currency.starting-balance", 0L));
        if (balances.putIfAbsent(id, start) == null) {
            dirty.set(true);
        }
    }

    public void set(UUID id, long amount) {
        balances.put(id, Math.max(0L, amount));
        dirty.set(true);
    }

    /** Adds coins and returns the new balance. */
    public long add(UUID id, long amount) {
        if (amount <= 0) {
            return get(id);
        }
        long result = balances.merge(id, amount, CoinManager::safeAdd);
        dirty.set(true);
        return result;
    }

    /** Removes coins. Returns false (and changes nothing) if the player doesn't have enough. */
    public boolean take(UUID id, long amount) {
        if (amount <= 0) {
            return true;
        }
        boolean[] success = {false};
        balances.compute(id, (key, current) -> {
            long have = current == null ? 0L : current;
            if (have >= amount) {
                success[0] = true;
                return have - amount;
            }
            return current;
        });
        if (success[0]) {
            dirty.set(true);
        }
        return success[0];
    }

    private static long safeAdd(long a, long b) {
        long r = a + b;
        return (((a ^ r) & (b ^ r)) < 0) ? Long.MAX_VALUE : r;
    }
}
