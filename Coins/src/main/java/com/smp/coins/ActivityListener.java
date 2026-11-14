package com.smp.coins;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Handles join/quit bookkeeping and remembers when each player was last active (for the AFK option). */
public final class ActivityListener implements Listener {

    private final CoinsPlugin plugin;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();

    public ActivityListener(CoinsPlugin plugin) {
        this.plugin = plugin;
    }

    private void touch(UUID id) {
        lastActive.put(id, System.currentTimeMillis());
    }

    /** How long (ms) the player has been idle. Unknown players count as active. */
    public long idleMillis(UUID id) {
        Long last = lastActive.get(id);
        return last == null ? 0L : System.currentTimeMillis() - last;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.coins().ensure(id);
        touch(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastActive.remove(id);
        plugin.earnTask().forget(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition() || event.hasChangedOrientation()) {
            touch(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        touch(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        touch(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        touch(event.getPlayer().getUniqueId());
    }
}
