package com.invision.world.command;

import com.invision.world.InvisionWorldPlugin;
import com.invision.world.world.ChunkService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WorldCommand implements CommandExecutor {
    private final InvisionWorldPlugin plugin;
    private final ChunkService chunks;

    public WorldCommand(InvisionWorldPlugin plugin, ChunkService chunks) {
        this.plugin = plugin;
        this.chunks = chunks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) { sendInfo(player); return true; }
        switch (args[0].toLowerCase()) {
            case "buy" -> buy(player, args);
            case "donate" -> donate(player, args);
            case "fund" -> fund(player);
            case "map" -> plugin.mapService().give(player);
            case "stats" -> stats(player);
            case "reload" -> reload(player);
            default -> player.sendMessage(plugin.msg("&e/world &7→ info, map, buy, donate <сумма>, fund, stats, reload"));
        }
        return true;
    }

    private void sendInfo(Player p) {
        int cx = p.getLocation().getChunk().getX(), cz = p.getLocation().getChunk().getZ();
        int layer = chunks.layerFor(cx, cz);
        boolean open = chunks.isOpen(cx, cz);
        p.sendMessage(plugin.msg("&6&l🌎 МИР &7— &f" + chunks.world().getName()));
        p.sendMessage(plugin.msg("&7Центр: &f" + fmt(chunks.originX()) + ", " + fmt(chunks.originZ())));
        p.sendMessage(plugin.msg("&7Твой чанк: &f" + cx + ", " + cz + " &7| " + (open ? "&aОТКРЫТ" : "&cЗАКРЫТ")));
        p.sendMessage(plugin.msg("&7Слой: &e" + layer + " &7| цена: &6" + plugin.economy().format(chunks.priceForLayer(Math.max(1, layer)))));
        p.sendMessage(plugin.msg("&7Куплено: &e" + plugin.database().purchasedCount(chunks.world().getName())
                + " &7| Фонд: &6" + plugin.economy().format(plugin.database().fund(chunks.world().getName()))));
        p.sendMessage(plugin.msg("&7Карта: &e/world map"));
    }

    private String fmt(double d) { return String.format("%.1f", d); }

    private void buy(Player p, String[] a) {
        if (!p.getWorld().getName().equals(chunks.world().getName())) {
            p.sendMessage(plugin.msg("&cПерейди в мир анархии.")); return;
        }
        int tx = p.getLocation().getChunk().getX(), tz = p.getLocation().getChunk().getZ();
        if (a.length == 1) {
            plugin.mapService().give(p);
            return;
        }
        if (a.length == 2) {
            switch (a[1].toLowerCase()) {
                case "north", "n" -> tz--;
                case "south", "s" -> tz++;
                case "west", "w" -> tx--;
                case "east", "e" -> tx++;
                default -> { p.sendMessage(plugin.msg("&cНаправление: north/south/east/west.")); return; }
            }
        } else if (a.length == 3) {
            try { tx = Integer.parseInt(a[1]); tz = Integer.parseInt(a[2]); }
            catch (NumberFormatException e) { p.sendMessage(plugin.msg("&cКоординаты чанка должны быть числами.")); return; }
        } else {
            p.sendMessage(plugin.msg("&e/world buy &7или &e/world buy <x> <z>")); return;
        }

        var result = chunks.purchase(p, tx, tz);
        if (!result.success()) { p.sendMessage(plugin.msg("&c" + result.message())); return; }
        plugin.getServer().broadcastMessage(plugin.msg("🌎 &e" + p.getName() + " &7открыл чанк &f[" + tx + ", " + tz + "]&7. Граница обновлена по фактической территории."));
        p.sendMessage(plugin.msg("&a✓ Чанк открыт. Из фонда потрачено: &6" + plugin.economy().format(result.price())));
        plugin.mapService().refreshAll();
    }

    private void donate(Player p, String[] a) {
        if (a.length < 2) { p.sendMessage(plugin.msg("Использование: &e/world donate <сумма>")); return; }
        try {
            double amount = Double.parseDouble(a[1]);
            if (!Double.isFinite(amount) || amount <= 0) { p.sendMessage(plugin.msg("&cНекорректная сумма.")); return; }
            if (!plugin.economy().withdraw(p, amount)) { p.sendMessage(plugin.msg("&cНедостаточно денег.")); return; }
            plugin.database().donateToFund(chunks.world().getName(), amount);
            p.sendMessage(plugin.msg("&a❤ В фонд: &6" + plugin.economy().format(amount)
                    + " &7| теперь &6" + plugin.economy().format(plugin.database().fund(chunks.world().getName()))));
        } catch (NumberFormatException e) { p.sendMessage(plugin.msg("&cНекорректная сумма.")); }
    }

    private void fund(Player p) { p.sendMessage(plugin.msg("🌍 Фонд мира: &6" + plugin.economy().format(plugin.database().fund(chunks.world().getName())))); }

    private void stats(Player p) {
        String w = chunks.world().getName();
        p.sendMessage(plugin.msg("&6&lРасширение"));
        p.sendMessage(plugin.msg("&7Куплено: &e" + plugin.database().purchasedCount(w)));
        p.sendMessage(plugin.msg("&7Потрачено: &6" + plugin.economy().format(plugin.database().totalSpent(w))));
        p.sendMessage(plugin.msg("&7Фонд: &6" + plugin.economy().format(plugin.database().fund(w))));
        p.sendMessage(plugin.msg("&7Центр: &f" + fmt(chunks.originX()) + ", " + fmt(chunks.originZ())));
    }

    private void reload(Player p) {
        if (!p.hasPermission("invisionworld.admin")) { p.sendMessage(plugin.msg("&cНет прав.")); return; }
        plugin.reloadConfig();
        chunks.refreshFromSpawn();
        chunks.applyWorldBorder(false);
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            if (chunks.isExpansionWorld(w)) chunks.applyBorderForWorld(w, false);
        }
        plugin.mapService().refreshAll();
        p.sendMessage(plugin.msg("&aКонфигурация и внешняя граница обновлены."));
    }
}
