package com.solao.claimdecay;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ExemptClaimCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public ExemptClaimCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("claimdecay.admin")) {
            player.sendMessage("You do not have permission to run this command.");
            return true;
        }

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);
        if (claim == null) {
            player.sendMessage("You are not standing in a claim.");
            return true;
        }

        String claimId = String.valueOf(claim.getID());
        plugin.getConfig().set("exempt-claims." + claimId, true);
        plugin.saveConfig();

        player.sendMessage("Claim " + claimId + " has been marked as exempt.");
        return true;
    }
}
