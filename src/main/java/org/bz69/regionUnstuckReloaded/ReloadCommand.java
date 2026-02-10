package org.bz69.regionUnstuckReloaded;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final RegionUnstuckReloaded plugin;
    private final UnstuckCommand unstuckCommand;

    /** Creates reload command handler. */
    public ReloadCommand(RegionUnstuckReloaded plugin, UnstuckCommand unstuckCommand) {
        this.plugin = plugin;
        this.unstuckCommand = unstuckCommand;
    }

    /** Handles /regionunstuckreloaded <unstuck|reload>. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.formatMessage(
                plugin.getConfig().getString("messages.usage-reload", "§cUsage: /regionunstuckreloaded <unstuck|reload>")
            ));
            return true;
        }

        if ("unstuck".equalsIgnoreCase(args[0])) {
            return unstuckCommand.onCommand(sender, command, label, new String[0]);
        }

        if (!"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(plugin.formatMessage(
                plugin.getConfig().getString("messages.usage-reload", "§cUsage: /regionunstuckreloaded <unstuck|reload>")
            ));
            return true;
        }

        if (!sender.hasPermission("regionunstuck.reload") && !sender.hasPermission("regionunstuck.*")) {
            sender.sendMessage(plugin.formatMessage(
                plugin.getConfig().getString("messages.no-permission", "§cNo permission.")
            ));
            return true;
        }

        plugin.reloadConfig();
        plugin.registerAliases();
        sender.sendMessage(plugin.formatMessage(
            plugin.getConfig().getString("messages.config-reloaded", "§aConfiguration reloaded.")
        ));
        return true;
    }
}
