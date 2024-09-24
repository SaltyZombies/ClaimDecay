package com.solao.claimdecay;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerActivityListener implements Listener {

    private final ClaimDecayPlugin plugin;

    public PlayerActivityListener(ClaimDecayPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.updateLastActiveTime(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        plugin.updateLastActiveTime(event.getPlayer());
    }
}
