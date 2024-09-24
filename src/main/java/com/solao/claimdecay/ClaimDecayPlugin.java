package com.solao.claimdecay;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
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
                    Region region = getSelection(player);
                    if (region != null) {
                        backupClaim(player, region);
                        resetClaim(player, region);
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
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return false;
        }
        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    private Region getSelection(Player player) {
        try {
            return worldEdit.getSession(player).getSelection(BukkitAdapter.adapt(player.getWorld()));
        } catch (Exception e) {
            player.sendMessage("Failed to get selection.");
            e.printStackTrace();
            return null;
        }
    }

    private void backupClaim(Player player, Region region) {
        try {
            File schematicFile = new File(getDataFolder(), player.getUniqueId() + ".schematic");
            EditSession editSession = worldEdit.getWorldEdit().getEditSessionFactory().getEditSession(region.getWorld(), -1);
            Clipboard clipboard = new BlockArrayClipboard(region);
            ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, clipboard, region.getMinimumPoint());
            Operations.complete(copy);

            // Save the clipboard to the file
            try (ClipboardWriter writer = ClipboardFormats.findByFile(schematicFile).getWriter(new FileOutputStream(schematicFile))) {
                writer.write(clipboard);
            }

            player.sendMessage("Claim backed up successfully.");
        } catch (Exception e) {
            player.sendMessage("Failed to backup claim.");
            e.printStackTrace();
        }
    }

    private void resetClaim(Player player, Region region) {
        try {
            // Create an edit session for the world
            World world = region.getWorld();
            EditSession editSession = worldEdit.getWorldEdit().getEditSessionFactory().getEditSession(world, -1);

            // Clear the region
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        editSession.setBlock(BlockVector3.at(x, y, z), BlockTypes.AIR.getDefaultState());
                    }
                }
            }

            // Commit the changes
            editSession.flushSession();

            player.sendMessage("Claim reset successfully.");
        } catch (Exception e) {
            player.sendMessage("Failed to reset claim.");
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
}
