package net.licks92.wirelessredstone;

import net.licks92.wirelessredstone.signs.SignType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Utils {

    private static final List<BlockFace> AXIS = List.of(BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST);
    private static final List<BlockFace> FULL_AXIS = List.of(
            BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST,
            BlockFace.UP, BlockFace.DOWN);

    private static final Map<Integer, BlockFace> WALL_DIRECTIONS = Map.of(
            2, BlockFace.NORTH, 3, BlockFace.SOUTH, 4, BlockFace.WEST, 5, BlockFace.EAST
    );

    private static final Map<Integer, BlockFace> FLOOR_DIRECTIONS = Map.ofEntries(
            Map.entry(0, BlockFace.SOUTH), Map.entry(1, BlockFace.SOUTH_SOUTH_WEST),
            Map.entry(2, BlockFace.SOUTH_WEST), Map.entry(3, BlockFace.WEST_SOUTH_WEST),
            Map.entry(4, BlockFace.WEST), Map.entry(5, BlockFace.WEST_NORTH_WEST),
            Map.entry(6, BlockFace.NORTH_WEST), Map.entry(7, BlockFace.NORTH_NORTH_WEST),
            Map.entry(8, BlockFace.NORTH), Map.entry(9, BlockFace.NORTH_NORTH_EAST),
            Map.entry(10, BlockFace.NORTH_EAST), Map.entry(11, BlockFace.EAST_NORTH_EAST),
            Map.entry(12, BlockFace.EAST), Map.entry(13, BlockFace.EAST_SOUTH_EAST),
            Map.entry(14, BlockFace.SOUTH_EAST), Map.entry(15, BlockFace.SOUTH_SOUTH_EAST)
    );

    /**
     * Gets the current Minecraft version from Paper's API.
     *
     * @return A string representing the Minecraft version
     */
    private static String getMinecraftVersion() {
        // Utilize Paper's API for better version handling
        return Bukkit.getMinecraftVersion();
    }

    /**
     * Parses the Minecraft version into a `Version` object.
     *
     * @return Optional Version if parsing is successful
     */
    private static Optional<Version> parseVersion() {
        try {
            String version = getMinecraftVersion();
            String[] parts = version.split("\\.");
            return Optional.of(new Version(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Checks if the server version is compatible with Minecraft 1.8 or newer.
     *
     * @return True if compatible; otherwise, false.
     */
    public static boolean isCompatible() {
        return parseVersion()
                .map(version -> version.major() > 1 || (version.major() == 1 && version.minor() >= 8))
                .orElse(false);
    }

    /**
     * Checks if the new material system (1.13+) is present.
     *
     * @return True if the new material system is enabled; otherwise, false.
     */
    public static boolean isNewMaterialSystem() {
        return parseVersion()
                .map(version -> version.major() > 1 || (version.major() == 1 && version.minor() >= 13))
                .orElse(false);
    }

    /**
     * Unified message sender with enhanced customization.
     *
     * @param message     The message to send
     * @param sender      The target recipient
     * @param isError     Whether the message indicates an error
     * @param prefix      An optional prefix (null for no prefix)
     * @param checkSilent Whether to suppress message in silent mode
     */
    private static void sendMessageToSender(String message, CommandSender sender, boolean isError, String prefix, boolean checkSilent) {
        if (checkSilent && ConfigManager.getConfig().getSilentMode()) return;

        String formattedMessage = Optional.ofNullable(prefix)
                .map(p -> ChatColor.GRAY + "[" + ChatColor.RED + p + ChatColor.GRAY + "] ")
                .orElse("") +
                (isError ? ChatColor.RED : ChatColor.GREEN) + message;

        sender.sendMessage(formattedMessage);
    }

    public static void sendFeedback(String message, CommandSender sender, boolean isError) {
        sendMessageToSender(message, sender, isError, "WirelessRedstone", false);
    }

    public static void sendFeedback(String message, CommandSender sender, boolean isError, boolean checkSilent) {
        sendMessageToSender(message, sender, isError, "WirelessRedstone", checkSilent);
    }

    public static void sendCommandFeedback(String message, CommandSender sender, boolean isError) {
        sendMessageToSender(message, sender, isError, null, false);
    }

    public static void sendCommandFeedback(String message, CommandSender sender, boolean isError, boolean checkSilent) {
        sendMessageToSender(message, sender, isError, null, checkSilent);
    }

    /**
     * Converts a legacy direction ID into a BlockFace.
     *
     * @param isWallSign Indicates if the block is a wall sign
     * @param direction  The legacy direction ID
     * @return Converted BlockFace
     */
    public static BlockFace getBlockFace(boolean isWallSign, int direction) {
        return isWallSign
                ? WALL_DIRECTIONS.getOrDefault(direction, BlockFace.NORTH)
                : FLOOR_DIRECTIONS.getOrDefault(direction, BlockFace.SOUTH);
    }

    @Deprecated
    public static byte getRawData(boolean isTorch, BlockFace blockFace) {
        return switch (blockFace) {
            case NORTH -> (byte) (isTorch ? 4 : 2);
            case SOUTH -> (byte) (isTorch ? 3 : 3);
            case WEST -> (byte) (isTorch ? 2 : 4);
            case EAST -> (byte) (isTorch ? 1 : 5);
            default -> (byte) 0;
        };
    }

    /**
     * Generates a JSON-based tellraw teleport command for a player.
     *
     * @param playerName The name of the player.
     * @return A formatted tellraw command string.
     */
    public static String getTeleportString(String playerName) {
        return """
            tellraw %s [
                "",
                {"text": "[", "color": "gray",
                 "clickEvent": {"action": "run_command", "value": "%%COMMAND"},
                 "hoverEvent": {"action": "show_text", "value": {"text": "", "extra": [{"text": "%%HOVERTEXT"}]}}
                },
                {"text": "\\u27A4", "color": "aqua", "bold": true,
                 "clickEvent": {"action": "run_command", "value": "%%COMMAND"},
                 "hoverEvent": {"action": "show_text", "value": {"text": "", "extra": [{"text": "%%HOVERTEXT"}]}}
                },
                {"text": "] ", "color": "gray",
                 "clickEvent": {"action": "run_command", "value": "%%COMMAND"},
                 "hoverEvent": {"action": "show_text", "value": {"text": "", "extra": [{"text": "%%HOVERTEXT"}]}}
                },
                {"text": "Name %%NAME, type: %%TYPE, world: %%WORLD, x: %%XCOORD, y: %%YCOORD, z: %%ZCOORD",
                 "color": "green",
                 "clickEvent": {"action": "run_command", "value": "%%COMMAND"},
                 "hoverEvent": {"action": "show_text", "value": {"text": "", "extra": [{"text": "%%HOVERTEXT"}]}}
                }
            ]
            """.formatted(playerName);
    }

    /**
     * Retrieves all possible BlockFace directions.
     *
     * @param includeUpAndDown Whether to include vertical directions
     * @return A collection of BlockFace
     */
    public static Collection<BlockFace> getAxisBlockFaces(boolean includeUpAndDown) {
        return includeUpAndDown ? FULL_AXIS : AXIS;
    }

    public static Collection<BlockFace> getAxisBlockFaces() {
        return FULL_AXIS;
    }

    /**
     * Converts a yaw angle to a horizontal BlockFace.
     *
     * @param yaw The yaw angle
     * @return The determined BlockFace
     */
    public static BlockFace yawToFace(float yaw) {
        return AXIS.get(Math.round(yaw / 90) & 0x3);
    }

    public static boolean sameLocation(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null || loc1.getWorld() == null || loc2.getWorld() == null) {
            return false;
        }
        return loc1.getBlockX() == loc2.getBlockX() &&
                loc1.getBlockY() == loc2.getBlockY() &&
                loc1.getBlockZ() == loc2.getBlockZ() &&
                loc1.getWorld().getName().equalsIgnoreCase(loc2.getWorld().getName());
    }

    public static SignType getType(String text, @NotNull String line) {
        return switch (text.toUpperCase()) {
            case "TRANSMITTER", "T" -> SignType.TRANSMITTER;
            case "RECEIVER", "R" -> SignType.RECEIVER;
            case "SCREEN", "S" -> SignType.SCREEN;
            case "INVERTER", "I" -> SignType.RECEIVER_INVERTER;
            case "SWITCHER" -> SignType.RECEIVER_SWITCH;
            case "CLOCK", "C" -> SignType.RECEIVER_CLOCK;
            case "DELAYER", "D" -> SignType.RECEIVER_DELAYER;
            default -> null;
        };
    }

    private record Version(int major, int minor) {}
}