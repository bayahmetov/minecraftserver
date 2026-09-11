package com.invision.world;

import com.invision.world.command.WorldCommand;
import com.invision.world.db.Database;
import com.invision.world.economy.EconomyService;
import com.invision.world.listener.WorldProtectionListener;
import com.invision.world.map.WorldMapService;
import com.invision.world.world.ChunkService;
import com.invision.world.world.BoundaryVisualizer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class InvisionWorldPlugin extends JavaPlugin {
    private Database database;
    private EconomyService economy;
    private ChunkService chunks;
    private WorldMapService mapService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(this);
        database.init();

        economy = new EconomyService(this);
        if (!economy.isAvailable()) {
            getLogger().severe("Vault economy provider was not found. Install/enable EssentialsX Economy + Vault.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String worldName = getConfig().getString("world-name", "anarchy");
        World world = getServer().getWorld(worldName);
        if (world == null) world = new WorldCreator(worldName).createWorld();
        if (world == null) {
            getLogger().severe("Could not load/create world '" + worldName + "'.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        final World anarchyWorld = world;
        chunks = new ChunkService(this, database, economy, anarchyWorld);
        mapService = new WorldMapService(this, chunks);

        getServer().getPluginManager().registerEvents(new WorldProtectionListener(this, chunks), this);

        PluginCommand worldCommand = getCommand("world");
        if (worldCommand != null) worldCommand.setExecutor(new WorldCommand(this, chunks));

        // Multiverse spawn is the source of truth. The first plugin tick uses the current value;
        // this watcher also handles the common setup case where /mv setspawn is run after startup.
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            private double lastX = anarchyWorld.getSpawnLocation().getX();
            private double lastZ = anarchyWorld.getSpawnLocation().getZ();

            @Override
            public void run() {
                Location s = anarchyWorld.getSpawnLocation();
                if (Math.abs(s.getX() - lastX) > 0.5 || Math.abs(s.getZ() - lastZ) > 0.5) {
                    lastX = s.getX();
                    lastZ = s.getZ();
                    chunks.refreshFromSpawn();
                    chunks.applyWorldBorder(false);
                    for (World w : getServer().getWorlds()) {
                        if (chunks.isExpansionWorld(w)) chunks.applyBorderForWorld(w, false);
                    }
                    mapService.refreshAll();
                    getLogger().info("Multiverse spawn changed; territory center updated to " + lastX + ", " + lastZ);
                }
            }
        }, 100L, 100L);

        // Refresh contextual map markers for players currently holding the world map.
        getServer().getScheduler().runTaskTimer(this, () -> mapService.refreshAll(), 20L, 20L);
        long visualTicks = Math.max(2L, getConfig().getLong("visual-boundary.interval-ticks", 10L));
        getServer().getScheduler().runTaskTimer(this, new BoundaryVisualizer(this, chunks), 40L, visualTicks);

        for (World w : getServer().getWorlds()) {
            if (chunks.isExpansionWorld(w)) chunks.applyBorderForWorld(w, false);
        }
        getLogger().info("InvisionWorld enabled. World=" + anarchyWorld.getName()
                + ", initial area=16x16 chunks, base price=" + getConfig().getDouble("base-chunk-price", 100));
    }

    @Override
    public void onDisable() {
        if (database != null) database.close();
    }

    public Database database() { return database; }
    public EconomyService economy() { return economy; }
    public ChunkService chunks() { return chunks; }
    public WorldMapService mapService() { return mapService; }

    public String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    public String msg(String text) {
        String prefix = getConfig().getString("messages.prefix", "&8[&6InVision&8] &7");
        return color(prefix + text);
    }
}
