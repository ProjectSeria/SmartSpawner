package github.nighter.smartspawner.hooks.economy.shops;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import github.nighter.smartspawner.hooks.economy.shops.providers.economyshopgui.EconomyShopGUIProvider;
import github.nighter.smartspawner.hooks.economy.shops.providers.economyshopgui.ESGUICompatibilityHandler;
import github.nighter.smartspawner.hooks.economy.shops.providers.shopguiplus.ShopGuiPlusProvider;
import github.nighter.smartspawner.hooks.economy.shops.providers.shopguiplus.SpawnerHook;
import github.nighter.smartspawner.hooks.economy.shops.providers.ultimateshop.UltimateShopProvider;
import github.nighter.smartspawner.hooks.economy.shops.providers.zshop.ZShopProvider;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class ShopIntegrationManager implements org.bukkit.event.Listener {
    private final SmartSpawner plugin;
    private ShopProvider activeProvider;
    private final List<ShopProvider> availableProviders = new ArrayList<>();
    private SpawnerHook spawnerHook = null;
    private ESGUICompatibilityHandler esguiCompatibilityHandler = null;
    private boolean initializedOnce = false;
    private boolean listenerRegistered = false;

    public void initialize() {
        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }

        availableProviders.clear();
        activeProvider = null;

        detectAndRegisterActiveProviders();
        selectActiveProvider();
        initializedOnce = true;
    }

    private void detectAndRegisterActiveProviders() {
        // Check configuration for preferred plugin first
        // sell_integration.yml, held by ItemPriceManager, which builds this class.
        String configuredShop = plugin.getItemPriceManager().getSellConfig()
                .getString("shop_integration.preferred_plugin", "auto");
        boolean autoDetect = "auto".equalsIgnoreCase(configuredShop);

        // If a specific shop is configured, only try to load that one
        if (!autoDetect) {
            if (tryRegisterSpecificProvider(configuredShop)) {
                return; // Stop here - we found and loaded the preferred plugin
            } else if (!initializedOnce) {
                plugin.getLogger().info("Configured shop plugin '" + configuredShop + "' is not enabled yet. Will auto-connect when loaded.");
            }
        }

        registerProviderIfAvailable("EconomyShopGUI", () -> {
            EconomyShopGUIProvider provider = new EconomyShopGUIProvider(plugin);

            // Initialize PluginCompatibilityHandler after creating the provider and only if it's null
            if (provider.isAvailable() && esguiCompatibilityHandler == null) {
                try {
                    esguiCompatibilityHandler = new ESGUICompatibilityHandler(plugin);
                    plugin.getServer().getPluginManager().registerEvents(esguiCompatibilityHandler, plugin);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to register ESGUICompatibilityHandler: " + e.getMessage());
                }
            }

            return provider;
        });


        // Only try ShopGUIPlus if the plugin is actually present and enabled
        if (isPluginAvailable("ShopGUIPlus")) {
            registerProviderIfAvailable("ShopGUIPlus", () -> {
                // Register the spawner hook event listener before creating the provider
                if (spawnerHook == null) {
                    try {
                        spawnerHook = new SpawnerHook(plugin);
                        plugin.getServer().getPluginManager().registerEvents(spawnerHook, plugin);
                    } catch (Exception e) {
                        throw e; // Re-throw to prevent provider registration
                    }
                }
                return new ShopGuiPlusProvider(plugin);
            });
        }

        if (isPluginAvailable("UltimateShop")) {
            registerProviderIfAvailable("UltimateShop", () -> new UltimateShopProvider(plugin));
        }

        // registerProviderIfAvailable("ZShop", () -> new ZShopProvider(plugin));
    }

    private boolean tryRegisterSpecificProvider(String providerName) {
        try {
            switch (providerName.toLowerCase()) {
                case "economyshopgui":
                    if (isPluginAvailable("EconomyShopGUI")) {
                        registerProviderIfAvailable("EconomyShopGUI", () -> {
                            EconomyShopGUIProvider provider = new EconomyShopGUIProvider(plugin);

                            // Initialize PluginCompatibilityHandler after creating the provider and only if it's null
                            if (provider.isAvailable() && esguiCompatibilityHandler == null) {
                                try {
                                    esguiCompatibilityHandler = new ESGUICompatibilityHandler(plugin);
                                    plugin.getServer().getPluginManager().registerEvents(esguiCompatibilityHandler, plugin);
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to register ESGUICompatibilityHandler: " + e.getMessage());
                                }
                            }

                            return provider;
                        });
                        return !availableProviders.isEmpty();
                    }
                    break;
                case "shopguiplus":
                    if (isPluginAvailable("ShopGUIPlus")) {
                        registerProviderIfAvailable("ShopGUIPlus", () -> {
                            if (spawnerHook == null) {
                                spawnerHook = new SpawnerHook(plugin);
                                plugin.getServer().getPluginManager().registerEvents(spawnerHook, plugin);
                            }
                            return new ShopGuiPlusProvider(plugin);
                        });
                        return !availableProviders.isEmpty();
                    }
                    break;
                case "ultimateshop":
                    if (isPluginAvailable("UltimateShop")) {
                        registerProviderIfAvailable("UltimateShop", () -> new UltimateShopProvider(plugin));
                        return !availableProviders.isEmpty();
                    }
                    break;
                case "zshop":
                    if (isPluginAvailable("ZShop")) {
                        registerProviderIfAvailable("ZShop", () -> new ZShopProvider(plugin));
                        return !availableProviders.isEmpty();
                    }
                    break;
            }
        } catch (Exception e) {
            // Provider could not be loaded; treat it as unavailable.
        }
        return false;
    }

    private boolean isPluginAvailable(String pluginName) {
        Plugin targetPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
        if (targetPlugin == null) {
            for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
                if (p.getName().equalsIgnoreCase(pluginName)) {
                    targetPlugin = p;
                    break;
                }
            }
        }
        return targetPlugin != null && targetPlugin.isEnabled();
    }

    private void registerProviderIfAvailable(String providerName, Supplier<ShopProvider> providerSupplier) {
        // If we already have an active provider and we're in single-provider mode, skip
        if (!availableProviders.isEmpty()) {
            return;
        }

        try {
            ShopProvider provider = providerSupplier.get();
            if (provider.isAvailable()) {
                availableProviders.add(provider);
            } else if (!initializedOnce) {
                plugin.getLogger().warning("Shop provider '" + providerName + "' reported it is not available.");
            }
        } catch (NoClassDefFoundError e) {
            if (!initializedOnce) plugin.getLogger().warning("Shop provider '" + providerName + "' class not found: " + e.getMessage());
        } catch (Throwable e) {
            if (!initializedOnce) plugin.getLogger().warning("Could not initialize shop provider '" + providerName + "': " + e.getMessage());
        }
    }

    private void selectActiveProvider() {
        if (availableProviders.isEmpty()) {
            if (!initializedOnce) plugin.getLogger().info("No compatible shop plugins found. Shop integration is disabled.");
            return;
        }

        // Since we now ensure only one provider is registered, just use the first (and only) one
        activeProvider = availableProviders.getFirst();
        plugin.getLogger().info("Auto-detected & successfully hook into shop plugin: " + activeProvider.getPluginName());
    }

    public double getPrice(Material material) {
        return getPrice(material, null);
    }

    public double getPrice(Material material, org.bukkit.entity.Player player) {
        if (activeProvider == null && !initializedOnce) {
            initialize();
        }
        if (activeProvider == null || material == null) {
            return 0.0;
        }

        try {
            return activeProvider.getSellPrice(material, player);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getPrice(Material material, long amount, org.bukkit.entity.Player player) {
        if (activeProvider == null && !initializedOnce) {
            initialize();
        }
        if (activeProvider == null || material == null || amount <= 0) {
            return 0.0;
        }

        try {
            return activeProvider.getSellPrice(material, amount, player);
        } catch (Exception e) {
            return getPrice(material, player) * amount;
        }
    }

    public String getActiveShopPlugin() {
        return activeProvider != null ? activeProvider.getPluginName() : "None";
    }

    public boolean hasActiveProvider() {
        if (activeProvider == null && !initializedOnce) {
            initialize();
        }
        return activeProvider != null;
    }

    @org.bukkit.event.EventHandler
    public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
        if (activeProvider != null) return;

        String pluginName = event.getPlugin().getName();
        if (pluginName.equalsIgnoreCase("UltimateShop") ||
            pluginName.equalsIgnoreCase("EconomyShopGUI") ||
            pluginName.equalsIgnoreCase("EconomyShopGUI-Premium") ||
            pluginName.equalsIgnoreCase("ShopGUIPlus") ||
            pluginName.equalsIgnoreCase("zShop")) {
            plugin.getLogger().info("Shop plugin '" + pluginName + "' enabled. Connecting shop integration...");
            initialize();
        }
    }

    public void cleanup() {
        availableProviders.clear();
        activeProvider = null;
        if (spawnerHook != null) {
            spawnerHook.unregister();
            spawnerHook = null;
        }
        if (esguiCompatibilityHandler != null) {
            esguiCompatibilityHandler = null;
        }
    }
}
