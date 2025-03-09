package net.licks92.wirelessredstone;

import io.sentry.Sentry;
import net.licks92.wirelessredstone.commands.Admin.AdminCommandManager;
import net.licks92.wirelessredstone.commands.CommandManager;
import net.licks92.wirelessredstone.compat.InternalWorldEditHooker;
import net.licks92.wirelessredstone.listeners.BlockListener;
import net.licks92.wirelessredstone.listeners.PlayerListener;
import net.licks92.wirelessredstone.listeners.WorldListener;
import net.licks92.wirelessredstone.materiallib.MaterialLib;
import net.licks92.wirelessredstone.sentry.EventExceptionHandler;
import net.licks92.wirelessredstone.sentry.WirelessRedstoneSentryClientFactory;
import net.licks92.wirelessredstone.signs.*;
import net.licks92.wirelessredstone.storage.StorageConfiguration;
import net.licks92.wirelessredstone.storage.StorageManager;
import net.licks92.wirelessredstone.string.StringManager;
import net.licks92.wirelessredstone.string.Strings;
import net.licks92.wirelessredstone.worldedit.WorldEditLoader;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class WirelessRedstone extends JavaPlugin {

    public static final String CHANNEL_FOLDER = "channels";

    private static WirelessRedstone instance;
    private static WRLogger WRLogger;
    private static StringManager stringManager;
    private static StorageManager storageManager;
    private static SignManager signManager;
    private static CommandManager commandManager;
    private static AdminCommandManager adminCommandManager;
    private static Metrics metrics;

    private ConfigManager config;
    private InternalWorldEditHooker worldEditHooker;
    private boolean storageLoaded = false;
    private boolean sentryEnabled = true;

    // Static getters for easy access
    public static WirelessRedstone getInstance() {
        return instance;
    }

    public static WRLogger getWRLogger() {
        return WRLogger;
    }

    public static StringManager getStringManager() {
        return stringManager;
    }

    public static Strings getStrings() {
        return getStringManager().getStrings();
    }

    public static StorageManager getStorageManager() {
        return storageManager;
    }

    public static StorageConfiguration getStorage() {
        return getStorageManager().getStorage();
    }

    public static SignManager getSignManager() {
        return signManager;
    }

    public static CommandManager getCommandManager() {
        return commandManager;
    }

    public static AdminCommandManager getAdminCommandManager() {
        return adminCommandManager;
    }

    public static Metrics getMetrics() {
        return metrics;
    }

    // Accessor methods for instance fields
    public boolean isSentryEnabled() {
        return sentryEnabled;
    }

    public InternalWorldEditHooker getWorldEditHooker() {
        return worldEditHooker;
    }

    public void setWorldEditHooker(InternalWorldEditHooker worldEditHooker) {
        this.worldEditHooker = worldEditHooker;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Compatibility Check
        if (!Utils.isCompatible()) {
            String serverVersion = Bukkit.getBukkitVersion();
            getLogger().severe("**********");
            getLogger().severe("WirelessRedstone is not compatible with this server version!");
            getLogger().severe("Server Version: " + serverVersion);
            getLogger().severe("Please check for supported versions on the plugin's page.");
            getLogger().severe("**********");
            getPluginLoader().disablePlugin(this);
            return;
        }

        // Initialize MaterialLib
        new MaterialLib(this).initialize();

        // Load Configuration
        config = ConfigManager.getConfig();
        config.update(CHANNEL_FOLDER);

        sentryEnabled = config.getSentry() && !"TRUE".equalsIgnoreCase(System.getProperty("mc.development"));
        WRLogger = new WRLogger("[WirelessRedstone]", getServer().getConsoleSender(), config.getDebugMode(), config.getColorLogging());
        stringManager = new StringManager(config.getLanguage());

        // Initialize Storage Manager
        storageManager = new StorageManager(config.getStorageType(), CHANNEL_FOLDER);
        if (!storageManager.getStorage().initStorage()) {
            getLogger().severe("Failed to initialize storage. Disabling plugin.");
            getPluginLoader().disablePlugin(this);
            return;
        }

        storageLoaded = true;

        // Initialize Managers
        signManager = new SignManager();
        commandManager = new CommandManager();
        adminCommandManager = new AdminCommandManager();

        // Initialize Sentry for error reporting
        setupSentry();

        // Register Events
        registerEvents();

        // Register Commands
        registerCommands();

        // Load WorldEdit Integration (if available)
        loadWorldEditIntegration();

        // Initialize Metrics (if enabled)
        setupMetrics();

        // Perform Update Check (if enabled)
        checkForUpdates();
    }

    @Override
    public void onDisable() {
        // Shutdown components before plugin unload
        if (storageLoaded) {
            getStorage().close();
        }

        if (worldEditHooker != null) {
            worldEditHooker.unRegister();
        }

        storageLoaded = false;
        adminCommandManager = null;
        commandManager = null;
        signManager = null;
        storageManager = null;
        stringManager = null;
        config = null;
        WRLogger = null;
        worldEditHooker = null;
        instance = null;

        if (sentryEnabled) {
            Sentry.close();
        }

        getLogger().info("WirelessRedstone has been disabled.");
    }

    private void setupMetrics() {
        if (!config.getMetrics()) return;

        metrics = new Metrics(this);

        // Main sign type metrics
        metrics.addCustomChart(new Metrics.AdvancedPie("main_sign_types", () -> {
            try {
                return Map.of(
                        "Transmitters", countSigns(WirelessTransmitter.class),
                        "Receivers", countSigns(WirelessReceiver.class),
                        "Screens", countSigns(WirelessScreen.class)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return Collections.emptyMap();
            }
        }));

        // Receiver sign type metrics
        metrics.addCustomChart(new Metrics.AdvancedPie("receiver_sign_types", () -> {
            try {
                return Map.of(
                        "Normal", countSigns(WirelessReceiver.class),
                        "Inverter", countSigns(WirelessReceiverInverter.class),
                        "Delayer", countSigns(WirelessReceiverDelayer.class),
                        "Clock", countSigns(WirelessReceiverClock.class),
                        "Switch", countSigns(WirelessReceiverSwitch.class)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return Collections.emptyMap();
            }
        }));
    }

    private int countSigns(Class<?> signType) {
        if (storageManager == null || storageManager.getAllSigns() == null) return 0;
        return (int) storageManager.getAllSigns().stream()
                .filter(signType::isInstance)
                .count();
    }

    private void setupSentry() {
        if (!sentryEnabled) return;

        try (var resourceStream = getResource("plugin.yml")) {
            if (resourceStream == null) {
                getLogger().severe("Could not load 'plugin.yml'. Missing from the jar.");
                return;
            }

            var pluginConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(resourceStream));
            var sentryDsn = pluginConfig.getString("sentry.dsn", "");
            Sentry.init(sentryDsn, new WirelessRedstoneSentryClientFactory());

            resetSentryContext();

            getLogger().info("Sentry initialized for error reporting.");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Sentry: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void resetSentryContext() {
        Sentry.clearContext();
        var version = Bukkit.getBukkitVersion().split("-")[0];
        Sentry.getStoredClient().addTag("MC_version", version);
        Sentry.getStoredClient().addTag("MC_implementation", "Paper");
    }

    private void registerEvents() {
        PluginManager pm = getServer().getPluginManager();
        boolean eventCatchingSuccess = true;

        try {
            if (sentryEnabled) {
                EventExceptionHandler eventExceptionHandler = new EventExceptionHandler() {
                    @Override
                    public boolean handle(Throwable ex, Event event) {
                        Sentry.capture(ex);
                        getLogger().severe("An error occurred during event: " + event.getEventName());
                        ex.printStackTrace();
                        return false;
                    }
                };

                EventExceptionHandler.registerEvents(new WorldListener(), this, eventExceptionHandler);
                EventExceptionHandler.registerEvents(new BlockListener(), this, eventExceptionHandler);
                EventExceptionHandler.registerEvents(new PlayerListener(), this, eventExceptionHandler);
            }
        } catch (RuntimeException ex) {
            eventCatchingSuccess = false;
            getLogger().warning("Couldn't register events with Sentry catcher.");
            Sentry.capture(ex);
        }

        if (!eventCatchingSuccess || !sentryEnabled) {
            pm.registerEvents(new WorldListener(), this);
            pm.registerEvents(new BlockListener(), this);
            pm.registerEvents(new PlayerListener(), this);
        }
    }

    private void registerCommands() {
        Map<@NotNull String, ? extends CommandExecutor> commands = Map.of(
                "wirelessredstone", commandManager,
                "wr", commandManager,
                "wredstone", commandManager,
                "wifi", commandManager,
                "wradmin", adminCommandManager,
                "wra", adminCommandManager
        );

        commands.forEach((commandName, manager) -> {
            if (getCommand(commandName) != null) {
                Objects.requireNonNull(getCommand(commandName)).setExecutor(manager);
                Objects.requireNonNull(getCommand(commandName)).setTabCompleter((TabCompleter) manager);
            } else {
                getLogger().warning("Command '" + commandName + "' is missing in plugin.yml.");
            }
        });
    }

    private void loadWorldEditIntegration() {
        try {
            if (getServer().getPluginManager().isPluginEnabled("WorldEdit")) {
                new WorldEditLoader();
                getLogger().info("WorldEdit integration enabled.");
            } else {
                getLogger().info("WorldEdit not found. Skipping integration.");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to enable WorldEdit integration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkForUpdates() {
        if (!config.getUpdateCheck()) return;

        UpdateChecker.init(this).requestUpdateCheck().whenComplete((result, throwable) -> {
            if (throwable != null || !result.updateAvailable()) return;

            Bukkit.getScheduler().runTask(this, () ->
                    getLogger().info("A new update is available: Version " + result.getNewestVersion()));
        });
    }
}