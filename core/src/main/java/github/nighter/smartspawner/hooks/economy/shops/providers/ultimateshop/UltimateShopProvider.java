package github.nighter.smartspawner.hooks.economy.shops.providers.ultimateshop;

import cn.superiormc.ultimateshop.api.ShopHelper;
import cn.superiormc.ultimateshop.managers.ConfigManager;
import cn.superiormc.ultimateshop.objects.ObjectShop;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import cn.superiormc.ultimateshop.objects.caches.ObjectUseTimesCache;
import cn.superiormc.ultimateshop.objects.items.AbstractSingleThing;
import cn.superiormc.ultimateshop.objects.items.GiveResult;
import cn.superiormc.ultimateshop.objects.items.ItemStorage;
import cn.superiormc.ultimateshop.objects.items.MaxSellResult;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerSellEvent;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UltimateShopProvider implements ShopProvider, Listener {
    private final SmartSpawner plugin;

    private static class PendingSaleRecord {
        final ObjectItem item;
        final ObjectUseTimesCache playerCache;
        final ObjectUseTimesCache serverCache;
        final int basePlayerSellTimes;
        final int baseServerSellTimes;
        int pendingAmount;

        PendingSaleRecord(ObjectItem item, ObjectUseTimesCache playerCache, ObjectUseTimesCache serverCache, int basePlayerSellTimes, int baseServerSellTimes) {
            this.item = item;
            this.playerCache = playerCache;
            this.serverCache = serverCache;
            this.basePlayerSellTimes = basePlayerSellTimes;
            this.baseServerSellTimes = baseServerSellTimes;
            this.pendingAmount = 0;
        }
    }

    private static class PlayerSession {
        final Map<ObjectItem, PendingSaleRecord> records = new HashMap<>();
        long timestamp = System.currentTimeMillis();
    }

    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();

    public UltimateShopProvider(SmartSpawner plugin) {
        this.plugin = plugin;
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerSell(SpawnerSellEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        commitSales(player.getUniqueId());
    }

    private void commitSales(UUID uuid) {
        PlayerSession session = activeSessions.remove(uuid);
        if (session == null) return;

        for (PendingSaleRecord record : session.records.values()) {
            if (record.pendingAmount <= 0) continue;

            if (record.playerCache != null) {
                int newPlayerTimes = record.basePlayerSellTimes + record.pendingAmount;
                record.playerCache.setSellUseTimes(newPlayerTimes, true, true);
            }
            if (record.serverCache != null) {
                int newServerTimes = record.baseServerSellTimes + record.pendingAmount;
                record.serverCache.setSellUseTimes(newServerTimes, true, true);
            }
        }
    }

    private void cleanupOldSession(UUID uuid) {
        PlayerSession session = activeSessions.get(uuid);
        if (session != null && System.currentTimeMillis() - session.timestamp > 1000L) {
            revertSession(uuid);
        }
    }

    private void revertSession(UUID uuid) {
        PlayerSession session = activeSessions.remove(uuid);
        if (session == null) return;

        for (PendingSaleRecord record : session.records.values()) {
            if (record.pendingAmount <= 0) continue;

            if (record.playerCache != null) {
                record.playerCache.setSellUseTimes(record.basePlayerSellTimes, false, false);
            }
            if (record.serverCache != null) {
                record.serverCache.setSellUseTimes(record.baseServerSellTimes, false, false);
            }
        }
    }

    @Override
    public String getPluginName() {
        return "UltimateShop";
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin ultimateShop = Bukkit.getPluginManager().getPlugin("UltimateShop");
            if (ultimateShop == null) {
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    if (p.getName().equalsIgnoreCase("UltimateShop")) {
                        ultimateShop = p;
                        break;
                    }
                }
            }
            return ultimateShop != null && ultimateShop.isEnabled();
        } catch (Throwable e) {
            plugin.getLogger().warning("Error initializing UltimateShop integration: " + e.getMessage());
            return false;
        }
    }

    @Override
    public double getSellPrice(Material material) {
        return getSellPrice(material, null);
    }

    @Override
    public double getSellPrice(Material material, Player player) {
        return getSellPrice(material, 1, player);
    }

    @Override
    public double getSellPrice(Material material, long amount, Player player) {
        if (material == null || material.isAir() || amount <= 0) {
            return 0.0;
        }

        int qty = (int) Math.min(amount, Integer.MAX_VALUE);
        ItemStack itemStack = new ItemStack(material, qty);

        try {
            if (ConfigManager.configManager != null && ConfigManager.configManager.getShops() != null) {
                UUID uuid = player != null ? player.getUniqueId() : null;
                if (uuid != null) {
                    cleanupOldSession(uuid);
                }

                PlayerSession session = uuid != null ? activeSessions.computeIfAbsent(uuid, k -> {
                    PlayerSession newSession = new PlayerSession();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        PlayerSession s = activeSessions.get(uuid);
                        if (s == newSession) {
                            revertSession(uuid);
                        }
                    }, 1L);
                    return newSession;
                }) : null;

                List<ObjectItem> targetItems = new ArrayList<>();
                try {
                    java.lang.reflect.Method m = ShopHelper.class.getMethod("getTargetItems", ItemStorage.class, Player.class);
                    Object res = m.invoke(null, ItemStorage.of(new ItemStack[]{itemStack}), player);
                    if (res instanceof List) {
                        targetItems.addAll((List<ObjectItem>) res);
                    }
                } catch (Throwable ignored) {
                }

                if (targetItems.isEmpty()) {
                    try {
                        java.lang.reflect.Method m = ShopHelper.class.getMethod("getTargetItem", ItemStack[].class, Player.class);
                        Object res = m.invoke(null, new ItemStack[]{itemStack}, player);
                        if (res instanceof ObjectItem) {
                            targetItems.add((ObjectItem) res);
                        }
                    } catch (Throwable ignored) {
                    }
                }

                if (targetItems.isEmpty()) {
                    for (ObjectShop shop : ConfigManager.configManager.getShops()) {
                        if (shop == null || shop.getProductList() == null) continue;
                        for (ObjectItem item : shop.getProductList()) {
                            if (item == null || item.empty || item.getSellPrice() == null || item.getSellPrice().empty || item.getReward() == null) {
                                continue;
                            }
                            if (item.getReward().singleProducts != null) {
                                for (cn.superiormc.ultimateshop.objects.items.products.ObjectSingleProduct product : item.getReward().singleProducts) {
                                    if (product == null || product.empty) continue;
                                    if (product.singleSection != null) {
                                        String matStr = product.singleSection.getString("material");
                                        if (matStr == null) matStr = product.singleSection.getString("item");
                                        if (matStr != null && matStr.equalsIgnoreCase(material.name())) {
                                            targetItems.add(item);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                for (ObjectItem item : targetItems) {
                    if (item == null || item.empty || item.getSellPrice() == null || item.getSellPrice().empty) {
                        continue;
                    }

                    ObjectUseTimesCache playerCache = player != null ? ShopHelper.getPlayerUseTimesCache(item, player) : null;
                    ObjectUseTimesCache serverCache = ShopHelper.getServerUseTimesCache(item);

                    PendingSaleRecord record = session != null ? session.records.get(item) : null;
                    int basePlayerTimes = record != null ? record.basePlayerSellTimes : (playerCache != null ? playerCache.getSellUseTimes() : (player != null ? ShopHelper.getSellUseTimes(item, player) : 0));
                    int baseServerTimes = record != null ? record.baseServerSellTimes : (serverCache != null ? serverCache.getSellUseTimes() : 0);
                    int currentPending = record != null ? record.pendingAmount : 0;

                    int effectivePlayerTimes = basePlayerTimes + currentPending;
                    int effectiveServerTimes = baseServerTimes + currentPending;

                    if (playerCache != null) {
                        playerCache.setSellUseTimes(effectivePlayerTimes, false, false);
                    }
                    if (serverCache != null) {
                        serverCache.setSellUseTimes(effectiveServerTimes, false, false);
                    }

                    GiveResult giveResult = item.getSellPrice().give(player, effectivePlayerTimes, qty);
                    if (giveResult == null || giveResult.getResultMap() == null || giveResult.getResultMap().isEmpty()) {
                        if (playerCache != null) playerCache.setSellUseTimes(basePlayerTimes + currentPending, false, false);
                        if (serverCache != null) serverCache.setSellUseTimes(baseServerTimes + currentPending, false, false);
                        continue;
                    }

                    double total = 0.0;
                    for (Map.Entry<AbstractSingleThing, BigDecimal> entry : giveResult.getResultMap().entrySet()) {
                        AbstractSingleThing thing = entry.getKey();
                        if (thing == null || thing.empty) continue;
                        BigDecimal val = entry.getValue();
                        if (val != null) {
                            total += val.doubleValue();
                        }
                    }

                    if (total > 0) {
                        if (session != null) {
                            if (record == null) {
                                record = new PendingSaleRecord(item, playerCache, serverCache, basePlayerTimes, baseServerTimes);
                                session.records.put(item, record);
                            }
                            record.pendingAmount += qty;

                            if (playerCache != null) {
                                playerCache.setSellUseTimes(basePlayerTimes + record.pendingAmount, false, false);
                            }
                            if (serverCache != null) {
                                serverCache.setSellUseTimes(baseServerTimes + record.pendingAmount, false, false);
                            }
                        }
                        return total;
                    } else {
                        if (playerCache != null) playerCache.setSellUseTimes(basePlayerTimes + currentPending, false, false);
                        if (serverCache != null) serverCache.setSellUseTimes(baseServerTimes + currentPending, false, false);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. Fallback: Direct API call using ShopHelper
        try {
            GiveResult giveResult = ShopHelper.getSellPrices(new ItemStack[] { itemStack }, player, 1);
            if (giveResult != null && giveResult.getResultMap() != null && !giveResult.getResultMap().isEmpty()) {
                double total = 0.0;
                for (Map.Entry<AbstractSingleThing, BigDecimal> entry : giveResult.getResultMap().entrySet()) {
                    if (entry.getKey() != null && !entry.getKey().empty && entry.getValue() != null) {
                        total += entry.getValue().doubleValue();
                    }
                }
                if (total > 0)
                    return total;
            }
        } catch (Throwable ignored) {
        }

        // Failsafe fallback: Calculate unit price for 1 item and multiply by amount (only if amount > 1 to prevent recursion)
        if (amount > 1) {
            double unitPrice = getSellPrice(material, 1, player);
            return unitPrice * amount;
        }

        return 0.0;
    }

    private double parseMathExpr(String expr, long qty) {
        if (expr == null || expr.isEmpty())
            return 0.0;
        try {
            expr = expr.replace("'", "").replace("\"", "").trim();
            double val = Double.parseDouble(expr);
            return val * qty;
        } catch (NumberFormatException ignored) {
        }

        try {
            String cleaned = expr.replaceAll("\\{[^\\}]+\\}", "0").trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)").matcher(cleaned);
            if (m.find()) {
                double val = Double.parseDouble(m.group(1));
                return val * qty;
            }
        } catch (Throwable ignored) {
        }
        return 0.0;
    }
}

