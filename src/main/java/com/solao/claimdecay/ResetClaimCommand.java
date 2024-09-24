package com.solao.claimdecay;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockTypes;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldedit.regions.Region;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

import static org.bukkit.Bukkit.getLogger;

public class ResetClaimCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private WorldEditPlugin worldEdit;

    public ResetClaimCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("claimdecay.reset")) {
            player.sendMessage("You do not have permission to run this command.");
            return true;
        }

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);
        if (claim == null || !claim.getOwnerID().equals(player.getUniqueId()) || !player.hasPermission("claimdecay.admin")) {
            player.sendMessage("You are not standing in a claim you own.");
            return true;
        }

        Region region = getSelection(player);
        if (region != null) {
            backupClaim(player.getUniqueId(), region, claim.getID());
            resetClaim(player.getUniqueId(), claim);
            player.sendMessage("Your claim has been reset successfully.");
        } else {
            player.sendMessage("Failed to reset your claim.");
        }

        return true;
    }

    private Region getSelection(Player player) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);
        if (claim == null) {
            return null;
        }

        BlockVector3 minPoint = BlockVector3.at(claim.getLesserBoundaryCorner().getBlockX(), claim.getLesserBoundaryCorner().getBlockY(), claim.getLesserBoundaryCorner().getBlockZ());
        BlockVector3 maxPoint = BlockVector3.at(claim.getGreaterBoundaryCorner().getBlockX(), claim.getGreaterBoundaryCorner().getBlockY(), claim.getGreaterBoundaryCorner().getBlockZ());

        World world = (World) Bukkit.getWorld(claim.getLesserBoundaryCorner().getWorld().getName());
        return new CuboidRegion((World) BukkitAdapter.adapt(world), minPoint, maxPoint);
    }

    private void backupClaim(UUID playerUUID, Region region, Long claimId) {
        try {
            File schematicFile = new File(plugin.getDataFolder(), playerUUID + "-" + claimId + ".schematic");
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
            Region region = getSelection(Bukkit.getPlayer(playerUUID));
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
}
