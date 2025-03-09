package net.licks92.wirelessredstone;

import io.sentry.Sentry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.licks92.wirelessredstone.commands.Admin.AdminCommandManager;
import net.licks92.wirelessredstone.commands.CommandManager;
import net.licks92.wirelessredstone.compat.InternalWorldEditHooker;
import net.licks92.wirelessredstone.listeners.BlockListener;
import net.licks92.wirelessredstone.listeners.PlayerListener;
import net.licks92.wirelessredstone.listeners.WorldListener;
import net.licks92.wirelessredstone.sentry.EventExceptionHandler;
import net.licks92.wirelessredstone.sentry.WirelessRedstoneSentryClientFactory;
import net.licks92.wirelessredstone.signs.WirelessReceiver;
import net.licks92.wirelessredstone.signs.WirelessScreen;
import net.licks92.wirelessredstone.signs.WirelessTransmitter;
import net.licks92.wirelessredstone.storage.StorageManager;
import net.licks92.wirelessredstone.string.StringManager;
import net.licks92.wirelessredstone.worldedit.WorldEditLoader;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class WirelessRedstone extends JavaPlugin {

    public static final String CHANNEL_FOLDER = "channels";

    private static WirelessRedstone instance;
    private static WRLogger wrLogger; // Updated naming style for clarity
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
        return wrLogger;
    }

    public static StringManager getStringManager() {
        return stringManager;
    }

    public static StorageManager getStorageManager() {
        return storageManager;
    }

    public static SignManager getSignManager() {
        return signManager;
    }

    public static CommandManager getCommandManager() {
        return commandManager;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Console sender
        ConsoleCommandSender console = getServer().getConsoleSender();

        // Initial compatibility check
        if (!Utils.isCompatible()) {
            logIncompatibleVersion(console);
            return;
        }

        // Initialize WRLogger with Adventure API
        config = ConfigManager.getConfig();
        wrLogger = new WRLogger("[WirelessRedstone]", console, config.getDebugMode(), config.getColorLogging());

        wrLogger.info("Initializing WirelessRedstone...");

        // Load configurations
        config.update(CHANNEL_FOLDER);

        sentryEnabled = config.getSentry() && !"TRUE".equalsIgnoreCase(System.getProperty("mc.development"));

        stringManager = new StringManager(config.getLanguage());
        storageManager = new StorageManager(config.getStorageType(), CHANNEL_FOLDER);

        // Initialize storage
        if (!storageManager.getStorage().initStorage()) {
            wrLogger.severe("Failed to initialize storage. Disabling plugin.");
            getPluginLoader().disablePlugin(this);
            return;
        }

        storageLoaded = true;

        // Initialize managers
        signManager = new SignManager();
        commandManager = new CommandManager();
        adminCommandManager = new AdminCommandManager();

        // Initialize Sentry for error reporting
        setupSentry();

        // Register events and commands
        registerEvents();
        registerCommands();

        // WorldEdit integration
        loadWorldEditIntegration();

        // Metrics tracking (if enabled)
        setupMetrics();

        // Perform update check (if configured)
        checkForUpdates();

        wrLogger.info("WirelessRedstone has been successfully enabled.");
    }

    @Override
    public void onDisable() {
        wrLogger.info("Disabling WirelessRedstone...");

        if (storageLoaded) {
            getStorage().close();
        }

        if (worldEditHooker != null) {
            worldEditHooker.unRegister();
        }

        // Cleanup components
        storageLoaded = false;
        adminCommandManager = null;
        commandManager = null;
        signManager = null;
        storageManager = null;
        stringManager = null;
        config = null;
        wrLogger = null;
        worldEditHooker = null;
        instance = null;

        if (sentryEnabled) {
            Sentry.close();
        }

        wrLogger.info("WirelessRedstone has been disabled.");
    }

    private void logIncompatibleVersion(ConsoleCommandSender console) {
        String serverVersion = Bukkit.getBukkitVersion();
        WRLogger incompatibleLogger = new WRLogger("[WirelessRedstone]", console, false, true);

        incompatibleLogger.severe("**********");
        incompatibleLogger.severe("WirelessRedstone is not compatible with this server version!");
        incompatibleLogger.severe("Server Version: " + serverVersion);
        incompatibleLogger.severe("Please check for supported versions on the plugin's page.");
        incompatibleLogger.severe("**********");

        getPluginLoader().disablePlugin(this);
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
                wrLogger.warning("Error gathering metrics: " + e.getMessage());
                return Collections.emptyMap();
            }
        }));
    }

    private void setupSentry() {
        if (!sentryEnabled) return;

        try (var resourceStream = getResource("plugin.yml")) {
            if (resourceStream == null) {
                wrLogger.severe("Could not load 'plugin.yml'. Missing from the jar.");
                return;
            }

            var pluginConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(resourceStream));
            var sentryDsn = pluginConfig.getString("sentry.dsn", "");
            Sentry.init(sentryDsn, new WirelessRedstoneSentryClientFactory());

            resetSentryContext();

            wrLogger.info("Sentry initialized for error reporting.");
        } catch (Exception e) {
            wrLogger.severe("Failed to initialize Sentry: " + e.getMessage());
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

        try {
            if (sentryEnabled) {
                EventExceptionHandler.registerEvents(new WorldListener(), this, ex -> {
                    wrLogger.warning("Event exception caught: " + ex.getMessage());
                    return false; // Do not cancel the event
                });
            }
        } catch (RuntimeException ex) {
            wrLogger.warning("Failed to register events.");
            Sentry.capture(ex);
        }

        pm.registerEvents(new WorldListener(), this);
        pm.registerEvents(new BlockListener(), this);
        pm.registerEvents(new PlayerListener(), this);
    }

    private void registerCommands() {
        Map<String, CommandExecutor> commands = Map.of(
                "wirelessredstone", commandManager,
                "wradmin", adminCommandManager
        );

        commands.forEach((commandName, executor) -> {
            Objects.requireNonNull(getCommand(commandName)).setExecutor(executor);
            Objects.requireNonNull(getCommand(commandName)).setTabCompleter((TabCompleter) executor);
        });
    }

    private void loadWorldEditIntegration() {
        if (getServer().getPluginManager().isPluginEnabled("WorldEdit")) {
            new WorldEditLoader();
            wrLogger.info("WorldEdit integration enabled.");
        } else {
            wrLogger.warning("WorldEdit not found. Skipping integration.");
        }
    }

    private void checkForUpdates() {
        if (!config.getUpdateCheck()) return;

        UpdateChecker.init(this).requestUpdateCheck().whenComplete((result, throwable) -> {
            if (throwable == null && result.updateAvailable()) {
                wrLogger.info("A new update is available: Version " + result.getNewestVersion());
            }
        });
    }

    private int countSigns(Class<?> signType) {
        if (storageManager == null || storageManager.getAllSigns() == null) return 0;
        return (int) storageManager.getAllSigns().stream().filter(signType::isInstance).count();
    }
}