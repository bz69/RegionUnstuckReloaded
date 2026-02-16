package org.bz69.regionUnstuckReloaded;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UnstuckCommand implements CommandExecutor {

    private final RegionUnstuckReloaded plugin;
    private final SafeLocationFinder locationFinder;
    public static final Map<UUID, Location> teleportingPlayers = new HashMap<>();
    public static final Map<UUID, Integer> taskIds = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();

    /** Creates command handler. */
    public UnstuckCommand(RegionUnstuckReloaded plugin) {
        this.plugin = plugin;
        this.locationFinder = new SafeLocationFinder(plugin);
    }

    /** Starts unstuck flow via /regionunstuckreloaded unstuck. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.formatMessage(
                plugin.getConfig().getString("messages.only-player", "§cOnly players can use this command!")
            ));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("regionunstuck.command") && !player.hasPermission("regionunstuck.*")) {
            player.sendMessage(plugin.formatMessage(
                plugin.getConfig().getString("messages.no-permission", "§cNo permission.")
            ));
            return true;
        }

        int cooldownSeconds = plugin.getConfig().getInt("settings.cooldown-seconds", 0);
        if (cooldownSeconds > 0) {
            Long until = cooldowns.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (until != null && until > now) {
                long remaining = (long) Math.ceil((until - now) / 1000.0);
                String msg = plugin.getConfig().getString("messages.cooldown", "§cYou must wait %seconds%s before using this again.")
                    .replace("%seconds%", String.valueOf(remaining));
                player.sendMessage(plugin.formatMessage(msg));
                return true;
            }
        }

        if (teleportingPlayers.containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.formatMessage(
                plugin.getConfig().getString("messages.already-teleporting", "§cTeleport is already in progress!")
            ));
            return true;
        }

        if (!isInForeignRegion(player)) {
            player.sendMessage(plugin.formatMessage(
                plugin.getConfig().getString("messages.no-teleport-needed", "§aTeleport is not needed.")
            ));
            return true;
        }

        if (hasBypassTeleportDelay(player)) {
            executeTeleportNow(player);
            return true;
        }

        startTeleportCountdown(player);
        return true;
    }

    /** Checks if player is inside any foreign region. */
    private boolean isInForeignRegion(Player player) {
        Location loc = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        if (regions.size() == 0) {
            return false;
        }

        for (ProtectedRegion region : regions) {
            if (region.getOwners().contains(player.getUniqueId()) ||
                region.getMembers().contains(player.getUniqueId())) {
                return false;
            }
        }

        return true;
    }

    /** Starts delayed teleport with movement cancellation. */
    private void startTeleportCountdown(Player player) {
        int countdown = plugin.getConfig().getInt("settings.teleport-delay", 5);
        Location startLocation = player.getLocation().clone();

        teleportingPlayers.put(player.getUniqueId(), startLocation);

        String startMessage = plugin.getConfig().getString("messages.teleport-start", "§eTeleport will start in %seconds% seconds. Don't move!")
            .replace("%seconds%", String.valueOf(countdown));
        player.sendMessage(plugin.formatMessage(startMessage));

        BukkitRunnable task = new BukkitRunnable() {
            int timeLeft = countdown;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelTeleport(player.getUniqueId());
                    return;
                }

                if (timeLeft <= 0) {
                    try {
                        Location destination = locationFinder.findSafeLocation(player);
                        destination.setYaw(player.getLocation().getYaw());
                        destination.setPitch(player.getLocation().getPitch());
                        player.teleport(destination);
                        player.getWorld().playSound(destination, getTeleportSound(), 1.0f, 1.0f);

                        int cooldownSeconds = plugin.getConfig().getInt("settings.cooldown-seconds", 0);
                        if (cooldownSeconds > 0 && !hasBypassCooldownSet(player)) {
                            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));
                        }

                        String successMessage = plugin.getConfig().getString("messages.teleport-success", "§aYou were teleported outside the region!");
                        player.sendMessage(plugin.formatMessage(successMessage));
                    } catch (Exception e) {
                        String errorMessage = plugin.getConfig().getString("messages.teleport-impossible", "§cTeleport is impossible!");
                        player.sendMessage(plugin.formatMessage(errorMessage));
                        if (plugin.isDebugEnabled()) {
                            plugin.getLogger().warning("Failed to teleport player " + player.getName() + ": " + e.getMessage());
                        }
                    }

                    teleportingPlayers.remove(player.getUniqueId());
                    taskIds.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (timeLeft <= 3) {
                    String countdownMessage = plugin.getConfig().getString("messages.teleport-countdown", "§e%seconds%...")
                        .replace("%seconds%", String.valueOf(timeLeft));
                    player.sendMessage(plugin.formatMessage(countdownMessage));
                }

                timeLeft--;
            }
        };

        int taskId = task.runTaskTimer(plugin, 0L, 20L).getTaskId();
        taskIds.put(player.getUniqueId(), taskId);
    }

    /** Cancels active teleport task for a player. */
    public static void cancelTeleport(UUID playerId) {
        teleportingPlayers.remove(playerId);
        Integer taskId = taskIds.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private Sound getTeleportSound() {
        String raw = plugin.getConfig().getString("teleport.sound.name");
        if (raw == null || raw.isBlank()) {
            raw = plugin.getConfig().getString("teleport-sound", "ENTITY_ENDERMAN_TELEPORT");
        }
        if (raw == null || raw.isBlank()) {
            return Sound.ENTITY_ENDERMAN_TELEPORT;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            if (plugin.isDebugEnabled()) {
                plugin.getLogger().warning("Invalid teleport sound in config: " + raw);
            }
            return Sound.ENTITY_ENDERMAN_TELEPORT;
        }
    }

    private boolean hasBypassCooldownSet(Player player) {
        return player.hasPermission("regionunstuck.nocooldown") ||
               player.hasPermission("regionunstuck.*");
    }

    private boolean hasBypassTeleportDelay(Player player) {
        return player.hasPermission("regionunstuck.nodelay") ||
               player.hasPermission("regionunstuck.*");
    }

    private void executeTeleportNow(Player player) {
        try {
            Location destination = locationFinder.findSafeLocation(player);
            destination.setYaw(player.getLocation().getYaw());
            destination.setPitch(player.getLocation().getPitch());
            player.teleport(destination);
            player.getWorld().playSound(destination, getTeleportSound(), 1.0f, 1.0f);

            int cooldownSeconds = plugin.getConfig().getInt("settings.cooldown-seconds", 0);
            if (cooldownSeconds > 0 && !hasBypassCooldownSet(player)) {
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));
            }

            String successMessage = plugin.getConfig().getString("messages.teleport-success", "§aYou were teleported outside the region!");
            player.sendMessage(plugin.formatMessage(successMessage));
        } catch (Exception e) {
            String errorMessage = plugin.getConfig().getString("messages.teleport-impossible", "§cTeleport is impossible!");
            player.sendMessage(plugin.formatMessage(errorMessage));
            if (plugin.isDebugEnabled()) {
                plugin.getLogger().warning("Failed to teleport player " + player.getName() + ": " + e.getMessage());
            }
        }
    }
}
