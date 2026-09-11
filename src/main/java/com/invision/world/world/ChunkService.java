package com.invision.world.world;

import com.invision.world.InvisionWorldPlugin;
import com.invision.world.db.Database;
import com.invision.world.economy.EconomyService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

public final class ChunkService {
    private final InvisionWorldPlugin plugin;
    private final Database db;
    private final EconomyService economy;
    private final World world;

    private int centerChunkX;
    private int centerChunkZ;
    private int chunksPerSide;
    private int halfChunks;
    private int initialMinChunkX;
    private int initialMaxChunkX;
    private int initialMinChunkZ;
    private int initialMaxChunkZ;
    private double originX;
    private double originZ;
    private double basePrice;
    private double multiplier;
    private int maxLayer;

    public ChunkService(InvisionWorldPlugin plugin, Database db, EconomyService economy, World world) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.world = world;
        refreshFromSpawn();
        applyWorldBorder(false);
    }

    public void refreshFromSpawn() {
        Location spawn = world.getSpawnLocation();
        centerChunkX = spawn.getChunk().getX();
        centerChunkZ = spawn.getChunk().getZ();

        chunksPerSide = plugin.getConfig().getInt("initial-chunks-per-side", 16);
        if (chunksPerSide < 2) chunksPerSide = 16;
        if ((chunksPerSide & 1) != 0) chunksPerSide++;
        halfChunks = chunksPerSide / 2;

        // Exactly 16x16 chunks when default config is used.
        initialMinChunkX = centerChunkX - halfChunks;
        initialMaxChunkX = centerChunkX + halfChunks - 1;
        initialMinChunkZ = centerChunkZ - halfChunks;
        initialMaxChunkZ = centerChunkZ + halfChunks - 1;

        // WorldBorder is aligned to chunk edges, while the spawn can be inside its chunk.
        originX = (initialMinChunkX * 16.0) + (chunksPerSide * 8.0);
        originZ = (initialMinChunkZ * 16.0) + (chunksPerSide * 8.0);

        basePrice = plugin.getConfig().getDouble("base-chunk-price", 100.0);
        multiplier = plugin.getConfig().getDouble("price-multiplier", 2.0);
        maxLayer = plugin.getConfig().getInt("max-purchase-layer", 50);
    }

    public World world() { return world; }
    public double originX() { return originX; }
    public double originZ() { return originZ; }
    public int centerChunkX() { return centerChunkX; }
    public int centerChunkZ() { return centerChunkZ; }
    public int initialMinChunkX() { return initialMinChunkX; }
    public int initialMaxChunkX() { return initialMaxChunkX; }
    public int initialMinChunkZ() { return initialMinChunkZ; }
    public int initialMaxChunkZ() { return initialMaxChunkZ; }
    public int initialSizeBlocks() { return chunksPerSide * 16; }

    public boolean isInitial(int x, int z) {
        return x >= initialMinChunkX && x <= initialMaxChunkX
                && z >= initialMinChunkZ && z <= initialMaxChunkZ;
    }

    public boolean isOpen(int x, int z) {
        return isInitial(x, z) || db.isPurchased(world.getName(), x, z);
    }

    public int layerFor(int x, int z) {
        if (isInitial(x, z)) return 0;
        int dx = x < initialMinChunkX ? initialMinChunkX - x
                : Math.max(0, x - initialMaxChunkX);
        int dz = z < initialMinChunkZ ? initialMinChunkZ - z
                : Math.max(0, z - initialMaxChunkZ);
        return Math.max(dx, dz);
    }

    public double priceForLayer(int layer) {
        if (layer <= 1) return basePrice;
        if (layer > maxLayer) return Double.POSITIVE_INFINITY;
        double price = basePrice * Math.pow(multiplier, layer - 1);
        return Double.isFinite(price) ? price : Double.POSITIVE_INFINITY;
    }

    public boolean hasOpenNeighbor(int x, int z) {
        return isOpen(x + 1, z) || isOpen(x - 1, z) || isOpen(x, z + 1) || isOpen(x, z - 1);
    }

    public boolean isPurchasable(int x, int z) {
        return !isOpen(x, z) && hasOpenNeighbor(x, z) && layerFor(x, z) <= maxLayer;
    }

    public PurchaseResult purchase(Player player, int x, int z) {
        if (!player.getWorld().getName().equals(world.getName())) {
            return PurchaseResult.fail("Ты должен находиться в мире анархии.");
        }
        if (isOpen(x, z)) return PurchaseResult.fail("Этот чанк уже открыт.");
        if (!hasOpenNeighbor(x, z)) return PurchaseResult.fail("Этот чанк не соприкасается с открытой территорией.");

        int layer = layerFor(x, z);
        if (layer > maxLayer) return PurchaseResult.fail("Достигнут максимальный слой расширения.");
        double price = priceForLayer(layer);
        if (!Double.isFinite(price)) return PurchaseResult.fail("Цена этого слоя слишком велика.");
        if (!economy.isAvailable()) return PurchaseResult.fail("Экономика недоступна: нужен Vault + EssentialsX Economy.");

        double fund = db.fund(world.getName());
        if (fund + 1e-9 < price) {
            return PurchaseResult.fail("В фонде " + economy.format(fund) + ", а нужно " + economy.format(price) + ".");
        }

        Database.PurchaseDbResult result = db.purchaseChunk(
                world.getName(), x, z, layer, player.getUniqueId().toString(), price);
        if (!result.success()) {
            return PurchaseResult.fail(result.reason().equals("Not enough fund")
                    ? "Кто-то уже потратил фонд. Попробуй снова."
                    : "Не удалось открыть чанк: " + result.reason());
        }

        return PurchaseResult.ok(layer, price);
    }

    /**
     * Native WorldBorder is intentionally NOT used for the dynamic chunk boundary because
     * Minecraft only supports a square border. The actual rectangular boundary is enforced
     * by WorldProtectionListener and visualized locally near the player.
     */
    public void applyWorldBorder(boolean ignored) {
        // Keep the native border out of the gameplay path; remove interference from older versions.
        WorldBorder border = world.getWorldBorder();
        border.setWarningDistance(0);
        border.setDamageBuffer(0);
        border.setDamageAmount(0);
        border.setSize(59_999_968.0);
        border.setCenter(0, 0);
    }

    public void applyBorderForWorld(World target, boolean ignored) {
        WorldBorder border = target.getWorldBorder();
        border.setWarningDistance(0);
        border.setDamageBuffer(0);
        border.setDamageAmount(0);
        border.setSize(59_999_968.0);
        border.setCenter(0, 0);
    }

    public Bounds boundsFor(World target) {
        int cx = target.getSpawnLocation().getChunk().getX();
        int cz = target.getSpawnLocation().getChunk().getZ();
        int minX = cx - halfChunks;
        int maxX = cx + halfChunks - 1;
        int minZ = cz - halfChunks;
        int maxZ = cz + halfChunks - 1;
        if (target.getName().equals(world.getName())) {
            int[] b = db.purchasedBounds(world.getName());
            if (b != null) {
                minX = Math.min(minX, b[0]);
                maxX = Math.max(maxX, b[1]);
                minZ = Math.min(minZ, b[2]);
                maxZ = Math.max(maxZ, b[3]);
            }
        } else if (isExpansionWorld(target)) {
            for (int[] purchased : db.purchasedChunks(world.getName())) {
                int dx = purchased[0] - centerChunkX;
                int dz = purchased[1] - centerChunkZ;
                minX = Math.min(minX, cx + dx);
                maxX = Math.max(maxX, cx + dx);
                minZ = Math.min(minZ, cz + dz);
                maxZ = Math.max(maxZ, cz + dz);
            }
        }
        return new Bounds(minX, maxX, minZ, maxZ);
    }

    public boolean isExpansionWorld(World target) {
        java.util.List<String> configured = plugin.getConfig().getStringList("protected-worlds");
        if (!configured.isEmpty()) {
            for (String name : configured) {
                if (target.getName().equalsIgnoreCase(name)) return !target.getName().equalsIgnoreCase(world.getName());
            }
        }
        String main = world.getName();
        String name = target.getName();
        return name.equalsIgnoreCase(main + "_nether")
                || name.equalsIgnoreCase(main + "_the_end")
                || name.equalsIgnoreCase(main + "_end");
    }

    public boolean isProtectedWorld(World target) {
        return target.getName().equals(world.getName()) || isExpansionWorld(target);
    }

    public boolean isOpen(World target, int chunkX, int chunkZ) {
        int targetCenterX = target.getSpawnLocation().getChunk().getX();
        int targetCenterZ = target.getSpawnLocation().getChunk().getZ();
        int dx = chunkX - targetCenterX;
        int dz = chunkZ - targetCenterZ;
        if (Math.abs(dx) < halfChunks && Math.abs(dz) < halfChunks) return true;
        if (!isProtectedWorld(target)) return true;
        int baseX = centerChunkX + dx;
        int baseZ = centerChunkZ + dz;
        return db.isPurchased(world.getName(), baseX, baseZ);
    }

    public boolean isPurchasable(World target, int chunkX, int chunkZ) {
        int targetCenterX = target.getSpawnLocation().getChunk().getX();
        int targetCenterZ = target.getSpawnLocation().getChunk().getZ();
        int dx = chunkX - targetCenterX;
        int dz = chunkZ - targetCenterZ;
        int baseX = centerChunkX + dx;
        int baseZ = centerChunkZ + dz;
        return target.getName().equals(world.getName()) && isPurchasable(baseX, baseZ);
    }

    public int currentOuterLayer() { return Math.max(0, db.maxPurchasedLayer(world.getName())); }

    public record Bounds(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {}

}
