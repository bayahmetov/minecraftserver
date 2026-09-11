package com.invision.world.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import com.invision.world.InvisionWorldPlugin;

public final class EconomyService {
    private final Economy economy;

    public EconomyService(InvisionWorldPlugin plugin) {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
    }

    public boolean isAvailable() { return economy != null; }
    public double balance(Player player) { return economy == null ? 0.0 : economy.getBalance(player); }
    public boolean withdraw(Player player, double amount) {
        return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess();
    }
    public boolean deposit(Player player, double amount) {
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }
    public String format(double amount) {
        return economy == null ? String.format("%.2f", amount) : economy.format(amount);
    }
}
