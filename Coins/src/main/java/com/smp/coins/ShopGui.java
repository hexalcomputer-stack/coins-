package com.smp.coins;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** The Coin Shop: loads items/prices from config.yml, builds the menus and handles purchases. */
public final class ShopGui implements Listener {

    public enum Type { MAIN, SPAWNERS, TRIMS }

    public record Rarity(String id, String name, String color) {
    }

    /** One thing for sale. {@code give} is exactly what the player receives. */
    public record Entry(String displayName, Rarity rarity, long price, Material iconMaterial, ItemStack give) {
    }

    private static final int ITEMS_PER_PAGE = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_BALANCE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;
    private static final int MAIN_SPAWNERS = 11;
    private static final int MAIN_BALANCE = 13;
    private static final int MAIN_TRIMS = 15;

    private static final String TRIM_SUFFIX = "_ARMOR_TRIM_SMITHING_TEMPLATE";
    private static final String TEMPLATE_SUFFIX = "_SMITHING_TEMPLATE";

    /** Marks our inventories so we can recognise them in click events. */
    private static final class Holder implements InventoryHolder {
        final Type type;
        int page;
        final Map<Integer, Entry> entries = new HashMap<>();
        Inventory inventory;

        Holder(Type type, int page) {
            this.type = type;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final CoinsPlugin plugin;
    private final Map<String, Rarity> rarities = new LinkedHashMap<>();
    private final List<Entry> spawners = new ArrayList<>();
    private final List<Entry> trims = new ArrayList<>();

    public ShopGui(CoinsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    // ================================================================== loading

    public void reload() {
        rarities.clear();
        spawners.clear();
        trims.clear();

        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection rarityRoot = cfg.getConfigurationSection("rarities");
        if (rarityRoot != null) {
            for (String id : rarityRoot.getKeys(false)) {
                String key = id.toLowerCase(Locale.ROOT);
                rarities.put(key, new Rarity(
                        key,
                        rarityRoot.getString(id + ".name", Text.pretty(id)),
                        rarityRoot.getString(id + ".color", "&f")));
            }
        }

        loadSpawners(cfg.getConfigurationSection("spawners"));
        loadTrims(cfg.getConfigurationSection("trims"));

        plugin.getLogger().info("Coin Shop loaded: " + spawners.size() + " spawners, " + trims.size() + " armor trims.");
    }

    private void warnUnknownTiers(String section, ConfigurationSection root) {
        for (String tier : root.getKeys(false)) {
            if (!rarities.containsKey(tier.toLowerCase(Locale.ROOT))) {
                plugin.getLogger().warning("config.yml: '" + section + "." + tier + "' does not match any rarity and was ignored.");
            }
        }
    }

    private void loadSpawners(ConfigurationSection root) {
        if (root == null) {
            return;
        }
        warnUnknownTiers("spawners", root);

        for (Rarity rarity : rarities.values()) {
            ConfigurationSection tier = root.getConfigurationSection(rarity.id());
            if (tier == null) {
                continue;
            }
            long price = Math.max(0L, tier.getLong("price", 0L));

            for (String mob : tier.getStringList("items")) {
                EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(mob.toLowerCase(Locale.ROOT)));
                if (type == null) {
                    plugin.getLogger().warning("config.yml: unknown mob '" + mob + "' in spawners." + rarity.id());
                    continue;
                }
                try {
                    spawners.add(buildSpawner(mob, type, rarity, price));
                } catch (Exception ex) {
                    plugin.getLogger().warning("config.yml: could not create a spawner for '" + mob + "': " + ex.getMessage());
                }
            }
        }
    }

    private Entry buildSpawner(String mob, EntityType type, Rarity rarity, long price) {
        String pretty = Text.pretty(mob);

        ItemStack give = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) give.getItemMeta();
        CreatureSpawner state = (CreatureSpawner) meta.getBlockState();
        state.setSpawnedType(type);
        meta.setBlockState(state);
        meta.displayName(Text.item(rarity.color() + pretty + " Spawner"));
        give.setItemMeta(meta);

        // Show the spawn egg in the GUI (easier to recognise than a plain spawner block)
        Material egg = Material.matchMaterial(mob.toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        return new Entry(pretty + " Spawner", rarity, price, egg != null ? egg : Material.SPAWNER, give);
    }

    private void loadTrims(ConfigurationSection root) {
        if (root == null) {
            return;
        }
        warnUnknownTiers("trims", root);

        for (Rarity rarity : rarities.values()) {
            ConfigurationSection tier = root.getConfigurationSection(rarity.id());
            if (tier == null) {
                continue;
            }
            long price = Math.max(0L, tier.getLong("price", 0L));

            for (String name : tier.getStringList("items")) {
                String id = name.toUpperCase(Locale.ROOT);
                if (!id.endsWith(TEMPLATE_SUFFIX)) {
                    id = id + TRIM_SUFFIX;
                }
                Material material = Material.matchMaterial(id);
                if (material == null) {
                    plugin.getLogger().warning("config.yml: unknown trim '" + name + "' in trims." + rarity.id());
                    continue;
                }

                String pretty;
                if (id.endsWith(TRIM_SUFFIX)) {
                    pretty = Text.pretty(id.substring(0, id.length() - TRIM_SUFFIX.length())) + " Armor Trim";
                } else {
                    pretty = Text.pretty(id.substring(0, id.length() - TEMPLATE_SUFFIX.length())) + " Template";
                }
                trims.add(new Entry(pretty, rarity, price, material, new ItemStack(material)));
            }
        }
    }

    // ================================================================== opening / rendering

    public void open(Player player, Type type) {
        Holder holder = new Holder(type, 0);
        int size = type == Type.MAIN ? 27 : 54;

        String titleKey = switch (type) {
            case MAIN -> "gui.main-title";
            case SPAWNERS -> "gui.spawners-title";
            case TRIMS -> "gui.trims-title";
        };
        Component title = Text.color(plugin.messages().fill(plugin.getConfig().getString(titleKey, "&6Coin Shop")));

        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    private void render(Player player, Holder holder) {
        Inventory inv = holder.inventory;
        inv.clear();
        holder.entries.clear();

        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        long balance = plugin.coins().get(player.getUniqueId());

        if (holder.type == Type.MAIN) {
            inv.setItem(MAIN_SPAWNERS, categoryButton("gui.spawners-icon", Material.SPAWNER,
                    "gui.spawners-name", "gui.spawners-lore", spawners.size()));
            inv.setItem(MAIN_BALANCE, balanceButton(balance));
            inv.setItem(MAIN_TRIMS, categoryButton("gui.trims-icon", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                    "gui.trims-name", "gui.trims-lore", trims.size()));
            return;
        }

        List<Entry> list = holder.type == Type.SPAWNERS ? spawners : trims;
        int pages = Math.max(1, (list.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        holder.page = Math.max(0, Math.min(holder.page, pages - 1));

        int from = holder.page * ITEMS_PER_PAGE;
        for (int slot = 0; slot < ITEMS_PER_PAGE && from + slot < list.size(); slot++) {
            Entry entry = list.get(from + slot);
            inv.setItem(slot, entryIcon(entry, balance));
            holder.entries.put(slot, entry);
        }

        FileConfiguration cfg = plugin.getConfig();
        inv.setItem(SLOT_BACK, simpleButton(Material.ARROW, cfg.getString("gui.back-name", "&c« Back")));
        if (holder.page > 0) {
            inv.setItem(SLOT_PREVIOUS, simpleButton(Material.SPECTRAL_ARROW, cfg.getString("gui.previous-name", "&ePrevious page")));
        }
        inv.setItem(SLOT_BALANCE, balanceButton(balance));
        if (holder.page < pages - 1) {
            inv.setItem(SLOT_NEXT, simpleButton(Material.SPECTRAL_ARROW, cfg.getString("gui.next-name", "&eNext page")));
        }
        inv.setItem(SLOT_CLOSE, simpleButton(Material.BARRIER, cfg.getString("gui.close-name", "&cClose")));
    }

    private ItemStack entryIcon(Entry entry, long balance) {
        FileConfiguration cfg = plugin.getConfig();
        Messages messages = plugin.messages();

        long missing = Math.max(0L, entry.price() - balance);
        String statusTemplate = missing == 0L
                ? cfg.getString("gui.status-affordable", "&aClick to buy!")
                : cfg.getString("gui.status-too-expensive", "&cNot enough coins.");
        String status = messages.fill(statusTemplate, "missing", Text.number(missing));
        String rarity = entry.rarity().color() + entry.rarity().name();

        List<Component> lore = new ArrayList<>();
        for (String line : cfg.getStringList("gui.entry-lore")) {
            lore.add(Text.item(messages.fill(line,
                    "rarity", rarity,
                    "price", Text.number(entry.price()),
                    "status", status)));
        }

        ItemStack icon = new ItemStack(entry.iconMaterial());
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Text.item(entry.rarity().color() + entry.displayName()));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack categoryButton(String iconKey, Material fallback, String nameKey, String loreKey, int count) {
        FileConfiguration cfg = plugin.getConfig();
        Messages messages = plugin.messages();

        ItemStack item = new ItemStack(material(cfg.getString(iconKey), fallback));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(messages.fill(cfg.getString(nameKey, "&6Category"))));
        List<Component> lore = new ArrayList<>();
        for (String line : cfg.getStringList(loreKey)) {
            lore.add(Text.item(messages.fill(line, "count", String.valueOf(count))));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack balanceButton(long balance) {
        FileConfiguration cfg = plugin.getConfig();
        Messages messages = plugin.messages();

        ItemStack item = new ItemStack(material(cfg.getString("gui.balance-icon"), Material.GOLD_NUGGET));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(messages.fill(cfg.getString("gui.balance-name", "&6Your Coins"))));
        List<Component> lore = new ArrayList<>();
        for (String line : cfg.getStringList("gui.balance-lore")) {
            lore.add(Text.item(messages.fill(line, "balance", Text.number(balance))));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack simpleButton(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        Material material = material(plugin.getConfig().getString("gui.filler-material"), Material.GRAY_STAINED_GLASS_PANE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(" "));
        item.setItemMeta(meta);
        return item;
    }

    private static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material != null ? material : fallback;
    }

    // ================================================================== clicks

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true); // nothing can ever be taken out of (or put into) the shop

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        ClickType click = event.getClick();
        if (!click.isLeftClick() && !click.isRightClick()) {
            return;
        }

        int slot = event.getSlot();

        if (holder.type == Type.MAIN) {
            if (slot == MAIN_SPAWNERS) {
                plugin.playSound(player, "sounds.click");
                openNextTick(player, Type.SPAWNERS);
            } else if (slot == MAIN_TRIMS) {
                plugin.playSound(player, "sounds.click");
                openNextTick(player, Type.TRIMS);
            }
            return;
        }

        Entry entry = holder.entries.get(slot);
        if (entry != null) {
            buy(player, holder, entry);
            return;
        }

        if (slot == SLOT_BACK) {
            plugin.playSound(player, "sounds.click");
            openNextTick(player, Type.MAIN);
        } else if (slot == SLOT_PREVIOUS && holder.page > 0) {
            holder.page--;
            plugin.playSound(player, "sounds.click");
            render(player, holder);
        } else if (slot == SLOT_NEXT) {
            holder.page++;
            plugin.playSound(player, "sounds.click");
            render(player, holder);
        } else if (slot == SLOT_CLOSE) {
            Bukkit.getScheduler().runTask(plugin, player::closeInventory);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void openNextTick(Player player, Type type) {
        Bukkit.getScheduler().runTask(plugin, () -> open(player, type));
    }

    // ================================================================== buying

    private void buy(Player player, Holder holder, Entry entry) {
        UUID id = player.getUniqueId();
        CoinManager coins = plugin.coins();
        Messages messages = plugin.messages();

        long balance = coins.get(id);
        if (balance < entry.price()) {
            notEnough(player, entry, balance);
            return;
        }

        ItemStack give = entry.give().clone();
        if (!hasRoom(player.getInventory(), give)) {
            messages.send(player, "inventory-full");
            plugin.playSound(player, "sounds.fail");
            return;
        }

        if (!coins.take(id, entry.price())) {
            notEnough(player, entry, coins.get(id));
            return;
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(give);
        if (!leftover.isEmpty()) {
            coins.add(id, entry.price()); // safety net: refund if the item could not be added
            messages.send(player, "inventory-full");
            plugin.playSound(player, "sounds.fail");
            return;
        }

        messages.send(player, "purchase-success",
                "item", entry.displayName(),
                "price", Text.number(entry.price()),
                "balance", Text.number(coins.get(id)));
        plugin.playSound(player, "sounds.success");

        if (plugin.getConfig().getBoolean("log-purchases", true)) {
            plugin.getLogger().info(player.getName() + " bought " + entry.displayName() + " for " + entry.price() + " coins.");
        }

        render(player, holder); // refresh balance + affordability
    }

    private void notEnough(Player player, Entry entry, long balance) {
        plugin.messages().send(player, "not-enough-coins",
                "price", Text.number(entry.price()),
                "balance", Text.number(balance),
                "missing", Text.number(Math.max(0L, entry.price() - balance)));
        plugin.playSound(player, "sounds.fail");
    }

    private static boolean hasRoom(PlayerInventory inventory, ItemStack item) {
        if (inventory.firstEmpty() != -1) {
            return true;
        }
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(item) && stack.getAmount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }
}
