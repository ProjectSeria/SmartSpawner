package github.nighter.smartspawner.hooks.economy.shops.providers;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface ShopProvider {

    String getPluginName();

    boolean isAvailable();

    double getSellPrice(Material material);

    default double getSellPrice(Material material, Player player) {
        return getSellPrice(material);
    }

    default double getSellPrice(Material material, long amount, Player player) {
        if (material == null || amount <= 0) return 0.0;
        return getSellPrice(material, player) * amount;
    }
}