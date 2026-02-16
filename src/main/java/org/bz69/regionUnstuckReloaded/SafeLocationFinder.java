package org.bz69.regionUnstuckReloaded;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.Material;

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
        Location first = trySafeAtBase(baseLocation);
        if (first != null) {
            return first;
        }

        Location around = findSafeAroundRegion(foreignRegion, playerLoc);
        if (around != null) {
            return around;
        }

        throw new Exception("No safe location found for teleport");
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
            newLoc.setX(dx < 0 ? min.getBlockX() - 1 : max.getBlockX() + 1);
            newLoc.setZ(playerLoc.getBlockZ());
        } else {
            newLoc.setZ(dz < 0 ? min.getBlockZ() - 1 : max.getBlockZ() + 1);
            newLoc.setX(playerLoc.getBlockX());
        }

        return newLoc;
    }

    private Location getOppositeLocationOutsideRegion(ProtectedRegion region, Location playerLoc) {
        Location newLoc = playerLoc.clone();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int[] dir = getExitDirection(region, playerLoc);
        int dx = dir[0];
        int dz = dir[1];

        if (dx != 0) {
            newLoc.setX(dx < 0 ? max.getBlockX() + 1 : min.getBlockX() - 1);
            newLoc.setZ(playerLoc.getBlockZ());
        } else {
            newLoc.setZ(dz < 0 ? max.getBlockZ() + 1 : min.getBlockZ() - 1);
            newLoc.setX(playerLoc.getBlockX());
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

    /** Finds the topmost block at the given X/Z and returns a safe standing spot. */
    private Location getTopBlockLocation(Location base) throws Exception {
        World world = base.getWorld();
        if (world == null) {
            throw new Exception("World is null");
        }

        int x = base.getBlockX();
        int z = base.getBlockZ();
        Block highest = world.getHighestBlockAt(x, z);
        if (highest.getY() <= world.getMinHeight()) {
            return null;
        }

        Location target = highest.getLocation().add(0, 1, 0);

        if (target.getBlockY() >= world.getMaxHeight()) {
            return null;
        }

        if (!isSafeGround(highest)) {
            return null;
        }

        Block feet = target.getBlock();
        Block head = target.clone().add(0, 1, 0).getBlock();
        if (!isSafeSpace(feet) || !isSafeSpace(head)) {
            return null;
        }

        return target;
    }

    private boolean isSafeGround(Block block) {
        if (!block.getType().isSolid()) {
            return false;
        }
        if (block.isLiquid()) {
            return false;
        }
        return !isDangerousBlock(block.getType());
    }

    private boolean isSafeSpace(Block block) {
        if (block.isLiquid()) {
            return false;
        }
        return block.isPassable();
    }

    private boolean isDangerousBlock(Material type) {
        return type == Material.LAVA ||
               type == Material.FIRE ||
               type == Material.SOUL_FIRE ||
               type == Material.MAGMA_BLOCK ||
               type == Material.CAMPFIRE ||
               type == Material.SOUL_CAMPFIRE;
    }

    private Location trySafeAtBase(Location base) throws Exception {
        Location top = getTopBlockLocation(base);
        if (top != null) {
            return top;
        }
        return null;
    }

    private Location findSafeAroundRegion(ProtectedRegion region, Location playerLoc) throws Exception {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        World world = playerLoc.getWorld();
        if (world == null) {
            return null;
        }

        int minX = min.getBlockX() - 1;
        int maxX = max.getBlockX() + 1;
        int minZ = min.getBlockZ() - 1;
        int maxZ = max.getBlockZ() + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x != minX && x != maxX && z != minZ && z != maxZ) {
                    continue;
                }
                Location base = new Location(world, x, playerLoc.getY(), z);
                Location top = trySafeAtBase(base);
                if (top != null) {
                    return top;
                }
            }
        }
        return null;
    }
}
