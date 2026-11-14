package com.smp.coins;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /coins [balance <player>] | give|take|set <player> <amount> | reload */
public final class CoinsCommand implements TabExecutor {

    private final CoinsPlugin plugin;

    public CoinsCommand(CoinsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();

        if (args.length == 0) {
            showOwnBalance(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "balance", "bal" -> {
                if (args.length == 1) {
                    showOwnBalance(sender);
                } else {
                    showOtherBalance(sender, args[1]);
                }
            }
            case "give", "take", "set" -> {
                if (!sender.hasPermission("coins.admin")) {
                    messages.send(sender, "no-permission");
                } else {
                    adminChange(sender, args[0].toLowerCase(Locale.ROOT), args);
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("coins.admin")) {
                    messages.send(sender, "no-permission");
                } else {
                    plugin.reloadAll();
                    messages.send(sender, "reloaded");
                }
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        plugin.messages().send(sender, "usage");
        if (sender.hasPermission("coins.admin")) {
            plugin.messages().send(sender, "usage-admin");
        }
    }

    private void showOwnBalance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        plugin.messages().send(sender, "balance-self",
                "balance", Text.number(plugin.coins().get(player.getUniqueId())));
    }

    private void showOtherBalance(CommandSender sender, String name) {
        Messages messages = plugin.messages();
        if (!sender.hasPermission("coins.balance.others")) {
            messages.send(sender, "no-permission");
            return;
        }
        OfflinePlayer target = resolve(name);
        if (target == null) {
            messages.send(sender, "player-not-found", "player", name);
            return;
        }
        messages.send(sender, "balance-other",
                "player", displayName(target, name),
                "balance", Text.number(plugin.coins().get(target.getUniqueId())));
    }

    private void adminChange(CommandSender sender, String action, String[] args) {
        Messages messages = plugin.messages();
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }

        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", "player", args[1]);
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2].replace(",", ""));
        } catch (NumberFormatException ex) {
            messages.send(sender, "invalid-amount", "amount", args[2]);
            return;
        }
        boolean isSet = action.equals("set");
        if ((isSet && amount < 0) || (!isSet && amount <= 0)) {
            messages.send(sender, "invalid-amount", "amount", args[2]);
            return;
        }

        UUID id = target.getUniqueId();
        String name = displayName(target, args[1]);
        CoinManager coins = plugin.coins();

        switch (action) {
            case "give" -> {
                long balance = coins.add(id, amount);
                messages.send(sender, "given", "player", name, "amount", Text.number(amount), "balance", Text.number(balance));
                Player online = target.getPlayer();
                if (online != null && !online.equals(sender)) {
                    messages.send(online, "received", "amount", Text.number(amount));
                }
            }
            case "take" -> {
                if (coins.take(id, amount)) {
                    messages.send(sender, "taken", "player", name, "amount", Text.number(amount),
                            "balance", Text.number(coins.get(id)));
                } else {
                    messages.send(sender, "not-enough-admin", "player", name, "balance", Text.number(coins.get(id)));
                }
            }
            default -> {
                coins.set(id, amount);
                messages.send(sender, "set", "player", name, "balance", Text.number(coins.get(id)));
            }
        }
    }

    /** Finds a player who is online, or one the server already knows (never blocks on a web lookup). */
    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && (plugin.coins().has(cached.getUniqueId()) || cached.hasPlayedBefore())) {
            return cached;
        }
        return null;
    }

    private static String displayName(OfflinePlayer player, String fallback) {
        String name = player.getName();
        return name != null ? name : fallback;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("coins.admin");

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("balance");
            if (admin) {
                options.addAll(List.of("give", "take", "set", "reload"));
            }
            addMatches(out, options, args[0]);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            boolean needsPlayer = (admin && (sub.equals("give") || sub.equals("take") || sub.equals("set")))
                    || ((sub.equals("balance") || sub.equals("bal")) && sender.hasPermission("coins.balance.others"));
            if (needsPlayer) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                addMatches(out, names, args[1]);
            }
        } else if (args.length == 3 && admin) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
                addMatches(out, List.of("100", "1000", "10000"), args[2]);
            }
        }
        return out;
    }

    private static void addMatches(List<String> out, List<String> options, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
    }
}
