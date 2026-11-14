package com.smp.coins;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /coinshop [spawners|trims] */
public final class CoinShopCommand implements TabExecutor {

    private final CoinsPlugin plugin;

    public CoinShopCommand(CoinsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("coins.shop")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }

        ShopGui.Type type = ShopGui.Type.MAIN;
        if (args.length > 0) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "spawners", "spawner" -> type = ShopGui.Type.SPAWNERS;
                case "trims", "trim" -> type = ShopGui.Type.TRIMS;
                default -> {
                }
            }
        }
        plugin.shop().open(player, type);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            for (String option : List.of("spawners", "trims")) {
                if (option.startsWith(typed)) {
                    out.add(option);
                }
            }
        }
        return out;
    }
}
