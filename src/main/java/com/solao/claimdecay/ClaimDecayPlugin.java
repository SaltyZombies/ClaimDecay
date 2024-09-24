package com.solao.claimdecay;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.regions.CuboidRegion;
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
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;


public class ClaimDecayPlugin extends JavaPlugin {

    private LuckPerms luckPerms;
    private WorldEditPlugin worldEdit;
    private File lastActiveFile;
    private YamlConfiguration lastActiveConfig;

    @Override
    public void onEnable() {
        this.luckPerms = LuckPermsProvider.get();
        this.worldEdit = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");

        Objects.requireNonNull(this.getCommand("exemptclaim")).setExecutor(new ExemptClaimCommand(this));
        Objects.requireNonNull(this.getCommand("resetclaim")).setExecutor(new ResetClaimCommand(this));

        getServer().getScheduler().runTaskLater(this, this::checkAndResetClaims, 20L * 60L * 60L);
    }

    private void checkAndResetClaims() {
        // Loop through all claims
        for (Claim claim : GriefPrevention.instance.dataStore.getClaims()) {
            Set<UUID> claimPlayerUUIDs = new HashSet<>();

            // Get the owner of the claim
            claimPlayerUUIDs.add(claim.getOwnerID());

            ArrayList<String> builders = new ArrayList<>();
            ArrayList<String> containers = new ArrayList<>();
            ArrayList<String> accessors = new ArrayList<>();
            ArrayList<String> managers = new ArrayList<>();

            // Get permissions
            claim.getPermissions(builders, containers, accessors, managers);

            // Add UUIDs from builders
            for (String builder : builders) {
                try {
                    UUID playerUUID = UUID.fromString(builder);
                    claimPlayerUUIDs.add(playerUUID);
                } catch (IllegalArgumentException e) {
                    // Handle the case where the builder is not a UUID
                }
            }

            // Add UUIDs from containers
            for (String container : containers) {
                try {
                    UUID playerUUID = UUID.fromString(container);
                    claimPlayerUUIDs.add(playerUUID);
                } catch (IllegalArgumentException e) {
                    // Handle the case where the container is not a UUID
                }
            }

            // Add UUIDs from accessors
            for (String accessor : accessors) {
                try {
                    UUID playerUUID = UUID.fromString(accessor);
                    claimPlayerUUIDs.add(playerUUID);
                } catch (IllegalArgumentException e) {
                    // Handle the case where the accessor is not a UUID
                }
            }

            // Add UUIDs from managers
            for (String manager : managers) {
                try {
                    UUID playerUUID = UUID.fromString(manager);
                    claimPlayerUUIDs.add(playerUUID);
                } catch (IllegalArgumentException e) {
                    // Handle the case where the manager is not a UUID
                }
            }

            boolean allInactive = true;
            long currentTime = System.currentTimeMillis();

            // Perform actions based on the gathered UUIDs
            for (UUID playerUUID : claimPlayerUUIDs) {
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null && !hasPermission(player, "decay.exempt")) {
                    int decayDays = getDecayDays(player);
                    long lastActiveTime = getLastActiveTime(playerUUID);
                    if (currentTime - lastActiveTime <= decayDays * 24 * 60 * 60 * 1000L) {
                        allInactive = false;
                        break;
                    }
                }
            }

            // If all players are inactive, backup and reset the claim
            if (allInactive) {
                Region region = getSelection(claim);
                if (region != null) {
                    backupClaim(claim.getOwnerID(), region, claim.getID());
                    resetClaim(claim.getOwnerID(), claim);
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

    private Region getSelection(Claim claim) {
        if (claim == null) {
            return null;
        }

        BlockVector3 minPoint = BlockVector3.at(
                claim.getLesserBoundaryCorner().getBlockX(),
                claim.getLesserBoundaryCorner().getBlockY(),
                claim.getLesserBoundaryCorner().getBlockZ()
        );

        BlockVector3 maxPoint = BlockVector3.at(
                claim.getGreaterBoundaryCorner().getBlockX(),
                claim.getGreaterBoundaryCorner().getBlockY(),
                claim.getGreaterBoundaryCorner().getBlockZ()
        );

        World world = (World) Bukkit.getWorld(claim.getLesserBoundaryCorner().getWorld().getName());
        return new CuboidRegion((World) BukkitAdapter.adapt(world), minPoint, maxPoint);
    }

    private void backupClaim(UUID playerUUID, Region region, Long claimId) {
        try {
            File schematicFile = new File(getDataFolder(), playerUUID + "-" + claimId + ".schematic");
            EditSession editSession = worldEdit.getWorldEdit().getEditSessionFactory().getEditSession(region.getWorld(), -1);
            Clipboard clipboard = new BlockArrayClipboard(region);
            ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, clipboard, region.getMinimumPoint());
            Operations.complete(copy);

            // Save the clipboard to the file
            try (ClipboardWriter writer = ClipboardFormats.findByFile(schematicFile).getWriter(new FileOutputStream(schematicFile))) {
                writer.write(clipboard);
            }

            // Optionally, log the success
            getLogger().info("Claim backed up successfully for player " + playerUUID);
        } catch (Exception e) {
            // Optionally, log the failure
            getLogger().severe("Failed to backup claim for player " + playerUUID);
            e.printStackTrace();
        }
    }


    private void resetClaim(UUID playerUUID, Claim claim) {
        try {
            Region region = getSelection(claim);
            if (region == null) {
                getLogger().severe("Failed to get region for claim " + claim.getID());
                return;
            }

            // Get the world name
            String worldName = claim.getLesserBoundaryCorner().getWorld().getName();

            // Get the coordinates of the region
            int minX = region.getMinimumPoint().x();
            int minY = region.getMinimumPoint().y();
            int minZ = region.getMinimumPoint().z();
            int maxX = region.getMaximumPoint().x();
            int maxY = region.getMaximumPoint().y();
            int maxZ = region.getMaximumPoint().z();

            // Construct the command to restore the nature
            String command = String.format("restorenature %s %d %d %d %d %d %d", worldName, minX, minY, minZ, maxX, maxY, maxZ);

            // Execute the command as the console
            ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
            Bukkit.dispatchCommand(console, command);

            getLogger().info("Claim reset successfully for player " + playerUUID);
        } catch (Exception e) {
            getLogger().severe("Failed to reset claim for player " + playerUUID);
            e.printStackTrace();
        }
    }


    private long getLastActiveTime(UUID playerUUID) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getUserManager().getUser(playerUUID);
        if (user != null) {
            return user.getCachedData().getMetaData(QueryOptions.defaultContextualOptions()).getMetaValue("last-login-time", Long::parseLong).orElse(Instant.now().toEpochMilli());
        }
        return Instant.now().toEpochMilli();
    }
}
