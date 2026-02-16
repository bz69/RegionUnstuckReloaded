package org.bz69.regionUnstuckReloaded;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements Listener {

    private final RegionUnstuckReloaded plugin;

    /** Creates listener instance. */
    public PlayerMoveListener(RegionUnstuckReloaded plugin) {
        this.plugin = plugin;
    }

    /** Cancels teleport if the player moved. */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        Location startLocation = UnstuckCommand.teleportingPlayers.get(player.getUniqueId());
        if (startLocation == null) {
            return;
        }

        Location to = event.getTo();
        if (to != null && hasPlayerMoved(startLocation, to)) {
            boolean cancelOnMove = plugin.getConfig().getBoolean("settings.teleport-cancel-on-move", true);
            if (!cancelOnMove) {
                return;
            }

            UnstuckCommand.cancelTeleport(player.getUniqueId());
            String message = plugin.getConfig().getString("messages.teleport-cancelled", "§cTeleport cancelled! You moved.");
            player.sendMessage(plugin.formatMessage(message));
        }
    }

    /** Checks if position changed (ignores head rotation). */
    private boolean hasPlayerMoved(Location from, Location to) {
        if (from.getWorld() != to.getWorld()) {
            return true;
        }
        double dx = from.getX() - to.getX();
        double dz = from.getZ() - to.getZ();
        return (dx * dx + dz * dz) > 1.0;
    }
}
