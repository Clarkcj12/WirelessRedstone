package net.licks92.wirelessredstone;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Optional;

public class WRLogger {

    private final Optional<ConsoleCommandSender> console;
    private final String prefix;
    private final boolean debug;
    private final boolean color;

    /**
     * Creates an instance of WRLogger.
     *
     * @param prefix  This is added before all messages
     * @param console Console reference from Bukkit/Spigot
     * @param debug   Enable debug mode
     * @param color   Enable color messages
     */
    public WRLogger(String prefix, ConsoleCommandSender console, boolean debug, boolean color) {
        this.debug = debug;
        this.color = color;
        this.console = Optional.ofNullable(console); // Safe handling using Optional
        this.prefix = color ? ChatColor.RED + prefix + ChatColor.RESET : prefix;
    }

    /**
     * Display a info message to the console.
     *
     * @param msg Message
     */
    public void info(String msg) {
        if (color) console.sendMessage(prefix + " " + msg);
        else Bukkit.getLogger().info(msg);
    }

    /**
     * Display a debug message to the console if debug mode is enabled.
     *
     * @param msg Message
     */
    public void debug(String msg) {
        if (debug) {
            if (color) console.sendMessage(prefix + ChatColor.GOLD + "[Debug] " + ChatColor.RESET + msg);
            else Bukkit.getLogger().info(prefix + "[Debug] " + msg);
        }
    }

    /**
     * Display a severe message to the console.
     *
     * @param msg Message
     */
    public void severe(String msg) {
        if (color) console.sendMessage(prefix + ChatColor.DARK_RED + "[SEVERE] " + ChatColor.RESET + msg);
        else Bukkit.getLogger().severe(prefix + " " + msg);
    }

    /**
     * Display a warning message to the console.
     *
     * @param msg Message
     */
    public void warning(String msg) {
        if (color) console.sendMessage(prefix + ChatColor.YELLOW + "[WARNING] " + ChatColor.RESET + msg);
        else Bukkit.getLogger().warning(prefix + " " + msg);
    }

}
