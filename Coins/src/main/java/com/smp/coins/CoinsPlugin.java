package com.smp.coins;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoinsPlugin extends JavaPlugin {

    private Messages messages;
    private CoinManager coinManager;
    private ActivityListener activity;
    private EarnTask earnTask;
    private ShopGui shop;
    private boolean papiHooked;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        coinManager = new CoinManager(this);
        coinManager.load();

        activity = new ActivityListener(this);
        earnTask = new EarnTask(this);
        shop = new ShopGui(this);

        Bukkit.getPluginManager().registerEvents(activity, this);
        Bukkit.getPluginManager().registerEvents(shop, this);

        register("coins", new CoinsCommand(this));
        register("coinshop", new CoinShopCommand(this));

        // Pay coins for online time (checks once per second, see EarnTask)
        earnTask.runTaskTimer(this, 20L, 20L);

        // Periodic autosave (only writes if something changed)
        long saveTicks = Math.max(1L, getConfig().getLong("autosave-minutes", 5L)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, coinManager::saveAsync, saveTicks, saveTicks);

        // Players already online (e.g. after /reload) need a balance entry too
        for (Player player : Bukkit.getOnlinePlayers()) {
            coinManager.ensure(player.getUniqueId());
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            PapiHook.register(this);
            papiHooked = true;
            getLogger().info("PlaceholderAPI found: %coins_balance%, %coins_balance_formatted% and %coins_icon% are ready.");
        } else {
            getLogger().warning("PlaceholderAPI not found - the scoreboard placeholders will not work until it is installed.");
        }
    }

    @Override
    public void onDisable() {
        if (papiHooked) {
            PapiHook.unregister();
        }
        if (earnTask != null) {
            try {
                earnTask.cancel();
            } catch (IllegalStateException ignored) {
                // task was never scheduled (enable failed early)
            }
        }
        if (coinManager != null) {
            coinManager.saveSync();
        }
    }

    private void register(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /** Reloads config.yml, shop prices and earning settings. */
    public void reloadAll() {
        reloadConfig();
        shop.reload();
        earnTask.reloadSettings();
    }

    /** Plays a sound whose key is stored at the given config path. Blank = no sound. */
    public void playSound(Player player, String configPath) {
        String key = getConfig().getString(configPath, "");
        if (key == null || key.isBlank()) {
            return;
        }
        player.playSound(player.getLocation(), key, 1.0f, 1.0f);
    }

    public Messages messages() {
        return messages;
    }

    public CoinManager coins() {
        return coinManager;
    }

    public ActivityListener activity() {
        return activity;
    }

    public EarnTask earnTask() {
        return earnTask;
    }

    public ShopGui shop() {
        return shop;
    }
}
