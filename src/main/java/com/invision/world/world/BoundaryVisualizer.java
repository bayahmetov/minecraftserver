package com.invision.world.world;

import com.invision.world.InvisionWorldPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Subtle local visualization: only shows the real rectangular boundary/frontier when a player is nearby. */
public final class BoundaryVisualizer implements Runnable {
    private final InvisionWorldPlugin plugin;
    private final ChunkService chunks;

    public BoundaryVisualizer(InvisionWorldPlugin plugin, ChunkService chunks) {
        this.plugin = plugin;
        this.chunks = chunks;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("visual-boundary.enabled", true)) return;
        double radius = plugin.getConfig().getDouble("visual-boundary.radius", 48);
        double radius2 = radius * radius;
        int spacing = Math.max(2, plugin.getConfig().getInt("visual-boundary.spacing", 4));

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            World w = player.getWorld();
            if (!chunks.isProtectedWorld(w)) continue;
            ChunkService.Bounds b = chunks.boundsFor(w);
            Location l = player.getLocation();
            int minX = b.minChunkX() * 16;
            int maxX = (b.maxChunkX() + 1) * 16;
            int minZ = b.minChunkZ() * 16;
            int maxZ = (b.maxChunkZ() + 1) * 16;
            double y = Math.max(w.getMinHeight() + 1, Math.min(w.getMaxHeight() - 2, l.getY() + 1));

            drawLineIfNear(player, l, minX, minZ, maxX, minZ, y, radius2, spacing, Particle.DUST);
            drawLineIfNear(player, l, minX, maxZ, maxX, maxZ, y, radius2, spacing, Particle.DUST);
            drawLineIfNear(player, l, minX, minZ, minX, maxZ, y, radius2, spacing, Particle.DUST);
            drawLineIfNear(player, l, maxX, minZ, maxX, maxZ, y, radius2, spacing, Particle.DUST);

            // Highlight only currently purchasable chunks close to the player.
            int pcx = l.getChunk().getX();
            int pcz = l.getChunk().getZ();
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    if (!chunks.isPurchasable(w, cx, cz)) continue;
                    drawChunkOutline(player, cx, cz, y, radius2, spacing);
                }
            }
        }
    }

    private void drawLineIfNear(Player p, Location l, int x1, int z1, int x2, int z2, double y, double radius2, int spacing, Particle particle) {
        double dx = x2 - x1, dz = z2 - z1;
        int steps = Math.max(1, (int)Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) / spacing));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = x1 + dx * t, z = z1 + dz * t;
            if (squared2D(l.getX(), l.getZ(), x, z) > radius2) continue;
            p.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(org.bukkit.Color.RED, 0.8f));
        }
    }

    private void drawChunkOutline(Player p, int cx, int cz, double y, double radius2, int spacing) {
        Location l = p.getLocation();
        int x1 = cx * 16, x2 = x1 + 16, z1 = cz * 16, z2 = z1 + 16;
        drawLineIfNearColor(p, l, x1, z1, x2, z1, y, radius2, spacing);
        drawLineIfNearColor(p, l, x1, z2, x2, z2, y, radius2, spacing);
        drawLineIfNearColor(p, l, x1, z1, x1, z2, y, radius2, spacing);
        drawLineIfNearColor(p, l, x2, z1, x2, z2, y, radius2, spacing);
    }

    private void drawLineIfNearColor(Player p, Location l, int x1, int z1, int x2, int z2, double y, double radius2, int spacing) {
        double dx = x2 - x1, dz = z2 - z1;
        int steps = Math.max(1, (int)Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) / spacing));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = x1 + dx * t, z = z1 + dz * t;
            if (squared2D(l.getX(), l.getZ(), x, z) > radius2) continue;
            p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(org.bukkit.Color.YELLOW, 0.7f));
        }
    }

    private static double squared2D(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2, dz = z1 - z2;
        return dx * dx + dz * dz;
    }
}
