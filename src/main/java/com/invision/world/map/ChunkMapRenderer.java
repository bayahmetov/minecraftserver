package com.invision.world.map;

import com.invision.world.world.ChunkService;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/** Full world overview: the map stays fixed, the player marker moves by chunk. */
public final class ChunkMapRenderer extends MapRenderer {
    private final ChunkService chunks;

    public ChunkMapRenderer(ChunkService chunks) {
        super(true);
        this.chunks = chunks;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        byte background = color(Color.fromRGB(12, 12, 16));
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) canvas.setPixel(x, z, background);
        }

        int outer = chunks.currentOuterLayer();
        int minX = chunks.initialMinChunkX() - 1 - outer;
        int maxX = chunks.initialMaxChunkX() + 1 + outer;
        int minZ = chunks.initialMinChunkZ() - 1 - outer;
        int maxZ = chunks.initialMaxChunkZ() + 1 + outer;
        int width = maxX - minX + 1;
        int height = maxZ - minZ + 1;
        int cell = Math.max(1, Math.min(128 / width, 128 / height));
        int mapW = width * cell;
        int mapH = height * cell;
        int ox = (128 - mapW) / 2;
        int oz = (128 - mapH) / 2;

        for (int cz = minZ; cz <= maxZ; cz++) {
            for (int cx = minX; cx <= maxX; cx++) {
                boolean open = chunks.isOpen(cx, cz);
                boolean buyable = chunks.isPurchasable(cx, cz);
                byte fill = open
                        ? color(Color.fromRGB(52, 160, 78))
                        : buyable
                        ? color(Color.fromRGB(235, 180, 32))
                        : color(Color.fromRGB(60, 60, 68));
                drawCell(canvas, ox + (cx - minX) * cell, oz + (cz - minZ) * cell, cell, fill);
            }
        }

        // One white outline: only the global playable boundary.
        int openMinX = chunks.initialMinChunkX() - outer;
        int openMaxX = chunks.initialMaxChunkX() + outer;
        int openMinZ = chunks.initialMinChunkZ() - outer;
        int openMaxZ = chunks.initialMaxChunkZ() + outer;
        byte outline = color(Color.WHITE);
        int left = ox + (openMinX - minX) * cell;
        int right = ox + (openMaxX - minX + 1) * cell - 1;
        int top = oz + (openMinZ - minZ) * cell;
        int bottom = oz + (openMaxZ - minZ + 1) * cell - 1;
        drawHorizontal(canvas, left, right, top, outline);
        drawHorizontal(canvas, left, right, bottom, outline);
        drawVertical(canvas, left, top, bottom, outline);
        drawVertical(canvas, right, top, bottom, outline);

        if (player.getWorld() == chunks.world()) {
            int pcx = player.getLocation().getChunk().getX();
            int pcz = player.getLocation().getChunk().getZ();
            int px = ox + (pcx - minX) * cell + Math.max(0, cell / 2);
            int pz = oz + (pcz - minZ) * cell + Math.max(0, cell / 2);
            drawMarker(canvas, px, pz, cell);
        }
    }

    private static void drawCell(MapCanvas canvas, int x, int z, int size, byte fill) {
        int right = Math.min(127, x + size - 1);
        int bottom = Math.min(127, z + size - 1);
        for (int px = Math.max(0, x); px <= right; px++) {
            for (int pz = Math.max(0, z); pz <= bottom; pz++) canvas.setPixel(px, pz, fill);
        }
        byte grid = color(Color.fromRGB(28, 28, 34));
        if (x >= 0 && x < 128) for (int pz = Math.max(0, z); pz <= bottom; pz++) canvas.setPixel(x, pz, grid);
        if (z >= 0 && z < 128) for (int px = Math.max(0, x); px <= right; px++) canvas.setPixel(px, z, grid);
    }

    private static void drawMarker(MapCanvas canvas, int x, int z, int cell) {
        byte blue = color(Color.fromRGB(55, 165, 255));
        byte white = color(Color.WHITE);
        int size = Math.max(3, Math.min(7, cell));
        int half = size / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int px = x + dx, pz = z + dz;
                if (px >= 0 && px < 128 && pz >= 0 && pz < 128) canvas.setPixel(px, pz, blue);
            }
        }
        if (x >= 0 && x < 128 && z >= 0 && z < 128) canvas.setPixel(x, z, white);
    }

    private static void drawHorizontal(MapCanvas canvas, int x1, int x2, int z, byte color) {
        if (z < 0 || z >= 128) return;
        for (int x = Math.max(0, x1); x <= Math.min(127, x2); x++) canvas.setPixel(x, z, color);
    }

    private static void drawVertical(MapCanvas canvas, int x, int z1, int z2, byte color) {
        if (x < 0 || x >= 128) return;
        for (int z = Math.max(0, z1); z <= Math.min(127, z2); z++) canvas.setPixel(x, z, color);
    }

    private static byte color(Color c) {
        return MapPalette.matchColor(c);
    }
}
