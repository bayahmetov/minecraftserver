package com.invision.world.map;

import com.invision.world.InvisionWorldPlugin;
import com.invision.world.world.ChunkService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.ArrayList;

/** One contextual map view; the world stays fixed while each player's marker is drawn separately. */
public final class WorldMapService {
    private final InvisionWorldPlugin plugin;
    private final ChunkService chunks;
    private final MapView view;

    public WorldMapService(InvisionWorldPlugin plugin, ChunkService chunks) {
        this.plugin = plugin;
        this.chunks = chunks;
        int savedId = plugin.getConfig().getInt("map-id", -1);
        MapView existing = savedId >= 0 ? Bukkit.getMap((short) savedId) : null;
        this.view = existing != null ? existing : Bukkit.createMap(chunks.world());
        for (var renderer : new ArrayList<>(this.view.getRenderers())) this.view.removeRenderer(renderer);
        this.view.addRenderer(new ChunkMapRenderer(chunks));
        this.view.setScale(MapView.Scale.CLOSE);
        this.view.setCenterX((int) Math.round(chunks.originX()));
        this.view.setCenterZ((int) Math.round(chunks.originZ()));
        this.view.setTrackingPosition(false);
        this.view.setUnlimitedTracking(false);
        this.view.setLocked(true);
        plugin.getConfig().set("map-id", this.view.getId());
        plugin.saveConfig();
    }

    public void give(Player player) {
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName(plugin.color("&b🗺 Карта мира"));
        meta.setLore(java.util.List.of(
                plugin.color("&7Зелёный — открыто"),
                plugin.color("&eЖёлтый — можно купить"),
                plugin.color("&8Серый — закрыто"),
                plugin.color("&bСиний — ты")
        ));
        mapItem.setItemMeta(meta);
        player.getInventory().addItem(mapItem);
        player.sendMessage(plugin.msg("&aКарта мира выдана. Возьми её в руку, чтобы открыть."));
        player.sendMap(view);
    }

    public void refreshAll() {
        view.setCenterX((int) Math.round(chunks.originX()));
        view.setCenterZ((int) Math.round(chunks.originZ()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == chunks.world() && isOurMap(player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand())) {
                player.sendMap(view);
            }
        }
    }

    public void refreshPlayer(Player player) {
        if (player.getWorld() == chunks.world() && isOurMap(player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand())) {
            player.sendMap(view);
        }
    }

    private boolean isOurMap(ItemStack main, ItemStack off) {
        return isOurMap(main) || isOurMap(off);
    }

    private boolean isOurMap(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta)) return false;
        return meta.getMapView() != null && meta.getMapView().getId() == view.getId();
    }

    public MapView view() { return view; }
}
