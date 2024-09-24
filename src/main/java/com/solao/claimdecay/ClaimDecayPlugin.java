package com.solao.claimdecay;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.Selection;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.registry.WorldData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ClaimDecayPlugin extends JavaPlugin {

    private LuckPerms luckPerms;
    private WorldEditPlugin worldEdit;
    private File lastActiveFile;
    private YamlConfiguration lastActiveConfig;

    @Override
    public void onEnable() {
        this.luckPerms = LuckPermsProvider.get();
        this.worldEdit = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");

        // Load last active times
        lastActiveFile = new File(getDataFolder(), "last_active.yml");
        if (!lastActiveFile.exists()) {
            try {
                lastActiveFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        lastActiveConfig = YamlConfiguration.loadConfiguration(lastActiveFile);

        getServer().getScheduler().runTaskTimer(this, this::checkAndResetClaims, 0L, 20L * 60 * 60 * 24); // Run daily

        // Update last active time on player activity
        getServer().getPluginManager().registerEvents(new PlayerActivityListener(this), this);
    }

    private void checkAndResetClaims() {
        List<Player> players = (List<Player>) Bukkit.getOnlinePlayers();
        for (Player player : players) {
            if (!hasPermission(player, "decay.exempt")) {
                int decayDays = getDecayDays(player);
                long lastActiveTime = getLastActiveTime(player);
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastActiveTime > decayDays * 24 * 60 * 60 * 1000L) {
                    Selection selection = worldEdit.getSelection(player);
                    if (selection != null) {
                        backupClaim(player, selection);
                        resetClaim(player, selection);
                    }
                }
            }
        }
    }

    private int getDecayDays(Player player) {
        for (int days = 1; days <= 365; days++) {
            if (hasPermission(player, "decay.set." + days)) {
                return days;
            }
        }
        return 30; // Default to 30 days
    }

    public boolean hasPermission(Player player, String permission) {
        User user = luckperms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return false;
        }
        return user.getCachedData().getPermissionData().checkPermission(Node.builder(permission).build()).asBoolean();
    }

    private void backupClaim(Player player, Selection selection) {
        try {
            File schematicFile = new File(getDataFolder(), player.getUniqueId() + ".schematic");
            ClipboardHolder clipboard = new ClipboardHolder(selection.getClipboard());
            WorldData worldData = worldEdit.getWorldEdit().getWorldData(selection.getWorld());
            clipboard.createPaste(worldEdit.getWorldEdit().getEditSessionFactory().getEditSession(selection.getWorld(), -1))
                    .to(selection.getMinimumPoint())
                    .build();
            clipboard.save(schematicFile);
            player.sendMessage("Claim backed up successfully.");
        } catch (Exception e) {
            player.sendMessage("Failed to backup claim.");
            e.printStackTrace();
        }
    }

    private void resetClaim(Player player, Selection selection) {
        try {
            worldEdit.getWorldEdit().getEditSessionFactory().getEditSession(selection.getWorld(), -1)
                    .regenerate(selection);
            player.sendMessage("Claim reset successfully.");
        } catch (Exception e) {
            player.sendMessage("Failed to reset claim.");
            e.printStackTrace();
        }
    }

    public void restoreClaim(Player player) {
        try {
            File schematicFile = new File(getDataFolder(), player.getUniqueId() + ".schematic");
            ClipboardHolder clipboard = ClipboardHolder.load(schematicFile);
            clipboard.createPaste(worldEdit.getWorldEdit().getEditSessionFactory().getEditSession(player.getWorld(), -1))
                    .to(player.getLocation())
                    .build();
            player.sendMessage("Claim restored successfully.");
        } catch (Exception e) {
            player.sendMessage("Failed to restore claim.");
            e.printStackTrace();
        }
    }

    private long getLastActiveTime(Player player) {
        return lastActiveConfig.getLong(player.getUniqueId().toString(), System.currentTimeMillis());
    }

    public void updateLastActiveTime(Player player) {
        lastActiveConfig.set(player.getUniqueId().toString(), System.currentTimeMillis());
        try {
            lastActiveConfig.save(lastActiveFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("restoreclaim") && sender instanceof Player) {
            Player player = (Player) sender;
            restoreClaim(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("resetclaim") && sender instanceof Player) {
            Player player = (Player) sender;
            Selection selection = worldEdit.getSelection(player);
            if (selection != null) {
                backupClaim(player, selection);
                resetClaim(player, selection);
                return true;
            } else {
                player.sendMessage("No selection found.");
                return false;
            }
        }
        return false;
    }
}
