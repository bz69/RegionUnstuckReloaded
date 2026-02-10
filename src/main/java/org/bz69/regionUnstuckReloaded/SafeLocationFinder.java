package org.bz69.regionUnstuckReloaded;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class SafeLocationFinder {

    private final RegionUnstuckReloaded plugin;

    /** Creates finder instance. */
    public SafeLocationFinder(RegionUnstuckReloaded plugin) {
        this.plugin = plugin;
    }

    /** Finds a safe location outside a foreign region. */
    public Location findSafeLocation(Player player) throws Exception {
        Location playerLoc = player.getLocation();

        ProtectedRegion foreignRegion = getForeignRegion(player);

        if (foreignRegion == null) {
            throw new Exception("Player is not in a foreign region");
        }

        Location baseLocation = getClosestLocationOutsideRegion(foreignRegion, playerLoc);
        Location safe = findSafeLocationAround(baseLocation, foreignRegion, player);

        if (safe == null) {
            throw new Exception("No safe location found for teleport");
        }

        return safe;
    }

    /** Validates candidate location for safety and ownership. */
    private boolean isValidLocation(Location loc, Player player) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }

        if (!world.getWorldBorder().isInside(loc)) {
            return false;
        }

        if (loc.getBlockY() <= world.getMinHeight() || loc.getBlockY() >= world.getMaxHeight() - 1) {
            return false;
        }

        Location below = loc.clone().subtract(0, 1, 0);
        if (!below.getBlock().getType().isSolid()) {
            return false;
        }

        Block feetBlock = loc.getBlock();
        Material feet = feetBlock.getType();
        if (!isAirOrSafePassThrough(feet) || feetBlock.isLiquid()) {
            return false;
        }

        Location above1 = loc.clone().add(0, 1, 0);
        Location above2 = loc.clone().add(0, 2, 0);

        if (!isAirOrSafePassThrough(above1.getBlock().getType()) ||
            !isAirOrSafePassThrough(above2.getBlock().getType())) {
            return false;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        for (ProtectedRegion region : regions) {
            if (!region.getOwners().contains(player.getUniqueId()) &&
                !region.getMembers().contains(player.getUniqueId())) {
                return false;
            }
        }

        return true;
    }

    /** Returns a foreign region at player location or null. */
    private ProtectedRegion getForeignRegion(Player player) {
        Location loc = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        for (ProtectedRegion region : regions) {
            if (!region.getOwners().contains(player.getUniqueId()) &&
                !region.getMembers().contains(player.getUniqueId())) {
                return region;
            }
        }

        return null;
    }

    /** Picks base location just outside the region boundary. */
    private Location getClosestLocationOutsideRegion(ProtectedRegion region, Location playerLoc) {
        Location newLoc = playerLoc.clone();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int[] dir = getExitDirection(region, playerLoc);
        int dx = dir[0];
        int dz = dir[1];

        if (dx != 0) {
            newLoc.setX(dx < 0 ? min.getBlockX() - 1.5 : max.getBlockX() + 1.5);
        } else {
            newLoc.setZ(dz < 0 ? min.getBlockZ() - 1.5 : max.getBlockZ() + 1.5);
        }

        return newLoc;
    }

    /** Calculates exit direction based on nearest boundary. */
    private int[] getExitDirection(ProtectedRegion region, Location playerLoc) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int playerX = playerLoc.getBlockX();
        int playerZ = playerLoc.getBlockZ();

        int distToMinX = Math.abs(min.getBlockX() - playerX);
        int distToMaxX = Math.abs(max.getBlockX() - playerX);
        int distToMinZ = Math.abs(min.getBlockZ() - playerZ);
        int distToMaxZ = Math.abs(max.getBlockZ() - playerZ);

        int minDistX = Math.min(distToMinX, distToMaxX);
        int minDistZ = Math.min(distToMinZ, distToMaxZ);

        if (minDistX < minDistZ) {
            return new int[] { distToMinX < distToMaxX ? -1 : 1, 0 };
        } else {
            return new int[] { 0, distToMinZ < distToMaxZ ? -1 : 1 };
        }
    }

    /** Searches for a safe location around base point. */
    private Location findSafeLocationAround(Location base, ProtectedRegion region, Player player) throws Exception {
        int maxOut = plugin.getConfig().getInt("search.max-out", 32);
        int maxSide = plugin.getConfig().getInt("search.max-side", 6);

        int[] dir = getExitDirection(region, player.getLocation());
        int dx = dir[0];
        int dz = dir[1];

        for (int out = 0; out <= maxOut; out++) {
            for (int side = -maxSide; side <= maxSide; side++) {
                Location candidate = base.clone();

                if (dx != 0) {
                    candidate.add(dx * out, 0, side);
                } else {
                    candidate.add(side, 0, dz * out);
                }

                int safeY = getSafeY(candidate);
                if (safeY == -1) {
                    continue;
                }

                candidate.setY(safeY);

                if (isValidLocation(candidate, player)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    /** Finds safe Y coordinate at the given X/Z. */
    private int getSafeY(Location loc) throws Exception {
        World world = loc.getWorld();
        if (world == null) {
            throw new Exception("World is null");
        }

        Location checkLoc = loc.clone();
        int maxY = world.getMaxHeight() - 1;
        int minY = world.getMinHeight() + 2;

        for (int y = Math.min(loc.getBlockY(), maxY); y >= minY; y--) {
            checkLoc.setY(y);
            Material blockType = checkLoc.getBlock().getType();

            if (blockType.isSolid() && !isDangerousBlock(blockType)) {
                Location feetLoc = checkLoc.clone().add(0, 1, 0);
                Location above1 = checkLoc.clone().add(0, 2, 0);
                Location above2 = checkLoc.clone().add(0, 3, 0);

                Block feetBlock = feetLoc.getBlock();
                Material feetType = feetBlock.getType();
                if (!isAirOrSafePassThrough(feetType) || feetBlock.isLiquid()) {
                    continue;
                }

                if (isAirOrSafePassThrough(above1.getBlock().getType()) &&
                    isAirOrSafePassThrough(above2.getBlock().getType())) {
                    return y + 1;
                }
            }
        }

        return -1;
    }

    /** Checks if material is safe to pass through. */
    private boolean isAirOrSafePassThrough(Material material) {
        return material.isAir() ||
               material == Material.GRASS ||
               material == Material.TALL_GRASS ||
               material == Material.FERN ||
               material == Material.LARGE_FERN ||
               material == Material.SNOW;
    }

    /** Checks if block is dangerous to stand on. */
    private boolean isDangerousBlock(Material material) {
        return material == Material.LAVA ||
               material == Material.FIRE ||
               material == Material.SOUL_FIRE ||
               material == Material.CACTUS ||
               material == Material.MAGMA_BLOCK ||
               material == Material.SWEET_BERRY_BUSH ||
               material == Material.WITHER_ROSE ||
               material == Material.POWDER_SNOW ||
               material == Material.CAMPFIRE ||
               material == Material.SOUL_CAMPFIRE;
    }
}
