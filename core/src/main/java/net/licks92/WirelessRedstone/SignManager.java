package net.licks92.WirelessRedstone;

import net.licks92.WirelessRedstone.Compat.CompatMaterial;
import net.licks92.WirelessRedstone.Compat.CompatSignData;
import net.licks92.WirelessRedstone.Signs.SignType;
import net.licks92.WirelessRedstone.Signs.WirelessChannel;
import net.licks92.WirelessRedstone.Signs.WirelessPoint;
import net.licks92.WirelessRedstone.Signs.WirelessReceiver;
import net.licks92.WirelessRedstone.Signs.WirelessReceiverClock;
import net.licks92.WirelessRedstone.Signs.WirelessReceiverDelayer;
import net.licks92.WirelessRedstone.Signs.WirelessReceiverInverter;
import net.licks92.WirelessRedstone.Signs.WirelessReceiverSwitch;
import net.licks92.WirelessRedstone.Signs.WirelessScreen;
import net.licks92.WirelessRedstone.Signs.WirelessTransmitter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class SignManager {

    /**
     * Check if the player has access to a WirelessChannel.<br>
     * Player passed if the player is OP, has isAdmin permission or is an owner of the channel.
     *
     * @param player Player
     * @param channelName WirelessChannel name
     * @return If the player has access
     */
    public boolean hasAccessToChannel(Player player, String channelName) {
        WirelessChannel channel = WirelessRedstone.getStorageManager().getChannel(channelName);

        if (channel == null) {
            return true;
        }

        return channel.getOwners().contains(player.getUniqueId().toString()) || player.hasPermission(Permissions.isWirelessAdmin) || player.isOp();
    }

    /**
     * Check if the player has the permissions to place a sign.
     *
     * @param player Player
     * @param signType SignType
     * @return Boolean
     */
    public boolean canPlaceSign(Player player, SignType signType) {
        if (player.isOp() || player.hasPermission(Permissions.isWirelessAdmin)) {
            return true;
        }

        if (signType == SignType.TRANSMITTER) {
            return player.hasPermission(Permissions.canCreateTransmitter);
        } else if (signType == SignType.SCREEN) {
            return player.hasPermission(Permissions.canCreateScreen);
        } else {
            return player.hasPermission(Permissions.canCreateReceiver);
        }
    }

    /**
     * Place a sign with the correct sign lines at a location.<br>
     * Extra data for certain receivers is set at 0.
     *
     * @param channelName WirelessChannel name
     * @param location Location of the sign
     * @param type SignType
     * @return Boolean; Success or failure
     */
    public boolean placeSign(String channelName, Location location, SignType type) {
        return placeSign(channelName, location, type, 0);
    }

    /**
     * Place a sign with the correct sign lines at a location.
     *
     * @param channelName WirelessChannel name
     * @param location Location of the sign
     * @param type SignType
     * @param extraData Extra data needed for certain receivers
     * @return Boolean; Success or failure
     */
    public boolean placeSign(String channelName, Location location, SignType type, int extraData) {
        if (location.getBlock().getType() != Material.AIR) {
            return false;
        }

        if (!(location.getBlock().getType() == CompatMaterial.SIGN.getMaterial()
                || location.getBlock().getType() == CompatMaterial.WALL_SIGN.getMaterial())) {
            location.getBlock().setType(CompatMaterial.SIGN.getMaterial());
        }

        CompatSignData sign = new CompatSignData(location.getBlock());
        sign.setRotation(Utils.yawToFace(location.getYaw()));
        sign.setLine(1, channelName);

        switch (type) {
            case TRANSMITTER:
                sign.setLine(0, WirelessRedstone.getStringManager().tagsTransmitter.get(0));
                break;
            case SCREEN:
                sign.setLine(0, WirelessRedstone.getStringManager().tagsScreen.get(0));
                break;
            case RECEIVER:
                sign.setLine(0, WirelessRedstone.getStringManager().tagsReceiver.get(0));
                sign.setLine(2, WirelessRedstone.getStringManager().tagsReceiverDefaultType.get(0));
                break;
            case RECEIVER_INVERTER:
                sign.setLine(0, WirelessRedstone.getStringManager().tagsReceiver.get(0));
                sign.setLine(2, WirelessRedstone.getStringManager().tagsReceiverInverterType.get(0));
                break;
            case RECEIVER_SWITCH:
                sign.setLine(0, WirelessRedstone.getStringManager().tagsReceiver.get(0));
                sign.setLine(2, WirelessRedstone.getStringManager().tagsReceiverSwitchType.get(0));
                break;
            case RECEIVER_DELAYER:
                sign.setLine(0, WirelessRedstone.getStringManager().tagsReceiver.get(0));
                sign.setLine(2, WirelessRedstone.getStringManager().tagsReceiverDelayerType.get(0));
                sign.setLine(3, Integer.toString(extraData));
                break;
            case RECEIVER_CLOCK:
                sign.setLine(0, WirelessRedstone.getStringManager().tagsReceiver.get(0));
                sign.setLine(2, WirelessRedstone.getStringManager().tagsReceiverClockType.get(0));
                sign.setLine(3, Integer.toString(extraData));
                break;
        }

        return true;
    }

    /**
     * Save a WirelessRedstone sign to the database.
     *
     * @param channelName Name of the WirelessRedstone channel
     * @param block Place where the block is located
     * @param type What WirelessPoint type
     * @param direction Which direction is the sign facing
     * @param owners All the owners of the channel
     * @param delay Amount of delay for clock & delayer; this can be 0 if it is not one of these types
     * @return Return value<br>
     *      0 - Success; extended a channel
     *      1 - Success; created a channel
     *     -1 - Failure; Delayer delay must be >= 50
     *     -2 - Failure; Clock delay must be >= 50
     */
    public int registerSign(String channelName, Block block, SignType type, BlockFace direction, List<String> owners, int delay) {
        int result = 0;

        if (type == SignType.RECEIVER_DELAYER){
            if (delay < 50) {
                result = -1;
                return result;
            }
        } else if (type == SignType.RECEIVER_CLOCK) {
            if (delay < 50) {
                result = -2;
                return result;
            }
        }

        if (WirelessRedstone.getStorageManager().getChannel(channelName) == null) {
            result = 1;
            WirelessRedstone.getStorage().createChannel(new WirelessChannel(channelName, owners));
        }

        boolean isWallSign = block.getType() == CompatMaterial.WALL_SIGN.getMaterial();

        WirelessPoint point = null;
        switch (type) {
            case TRANSMITTER:
                point = new WirelessTransmitter(
                        block.getLocation().getBlockX(), 
                        block.getLocation().getBlockY(), 
                        block.getLocation().getBlockZ(), 
                        block.getLocation().getWorld().getName(), 
                        isWallSign,
                        direction,
                        owners.get(0)
                );
                break;
            case SCREEN:
                point = new WirelessScreen(
                        block.getLocation().getBlockX(),
                        block.getLocation().getBlockY(),
                        block.getLocation().getBlockZ(),
                        block.getLocation().getWorld().getName(),
                        isWallSign,
                        direction,
                        owners.get(0)
                );
                break;
            case RECEIVER:
                point = new WirelessReceiver(
                        block.getLocation().getBlockX(),
                        block.getLocation().getBlockY(),
                        block.getLocation().getBlockZ(),
                        block.getLocation().getWorld().getName(),
                        isWallSign,
                        direction,
                        owners.get(0)
                );
                break;
            case RECEIVER_INVERTER:
                point = new WirelessReceiverInverter(
                        block.getLocation().getBlockX(),
                        block.getLocation().getBlockY(),
                        block.getLocation().getBlockZ(),
                        block.getLocation().getWorld().getName(),
                        isWallSign,
                        direction,
                        owners.get(0)
                );
                break;
            case RECEIVER_SWITCH:
                point = new WirelessReceiverSwitch(
                        block.getLocation().getBlockX(),
                        block.getLocation().getBlockY(),
                        block.getLocation().getBlockZ(),
                        block.getLocation().getWorld().getName(),
                        isWallSign,
                        direction,
                        owners.get(0)
                );
                break;
            case RECEIVER_DELAYER:
                point = new WirelessReceiverDelayer(
                        block.getLocation().getBlockX(),
                        block.getLocation().getBlockY(),
                        block.getLocation().getBlockZ(),
                        block.getLocation().getWorld().getName(),
                        isWallSign,
                        direction,
                        owners.get(0),
                        delay
                );
                break;
            case RECEIVER_CLOCK:
                point = new WirelessReceiverClock(
                        block.getLocation().getBlockX(),
                        block.getLocation().getBlockY(),
                        block.getLocation().getBlockZ(),
                        block.getLocation().getWorld().getName(),
                        isWallSign,
                        direction,
                        owners.get(0),
                        delay
                );
                break;
        }

        WirelessRedstone.getStorage().createWirelessPoint(channelName, point);
        return result;
    }

    /**
     * Check if a sign is registred at a location.
     *
     * @param location Location of the sign
     * @return Boolean; Sign registred or not
     */
    public boolean isSignRegistred(Location location) {
        return WirelessRedstone.getStorageManager().getAllSigns().stream()
                .anyMatch(point -> Utils.sameLocation(point.getLocation(), location));
    }

    public boolean isWirelessRedstoneSign(Block block) {
        if (!(block.getState() instanceof Sign)) {
            return false;
        }

        Sign sign = (Sign) block.getState();

        if (Utils.getSignType(sign.getLine(0)) == null || sign.getLine(1).equalsIgnoreCase("")) {
            return false;
        }

        return isSignRegistred(block.getLocation());
    }

    public WirelessChannel getSignChannel(Location location) {
        return WirelessRedstone.getStorageManager().getChannels().stream()
                .filter(channel -> channel.getSigns().stream()
                        .anyMatch(point -> Utils.sameLocation(point.getLocation(), location)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Remove a sign from the database based on a location.
     *
     * @param channelName WirelessChannel name
     * @param location Location of the sign
     * @return Boolean; Success or failure (channel/sign not found)
     */
    public boolean removeSign(String channelName, Location location) {
        if (WirelessRedstone.getStorageManager().getChannel(channelName) == null) {
            return false;
        }

        WirelessChannel channel = WirelessRedstone.getStorageManager().getChannel(channelName);
        WirelessPoint point = channel.getSigns().stream()
                .filter(pointList -> Utils.sameLocation(pointList.getLocation(), location))
                .findFirst()
                .orElse(null);

        if (point == null) {
            return false;
        }

        WirelessRedstone.getStorage().removeWirelessPoint(channelName, point);
        return true;
    }

    public HashMap<WirelessChannel, Collection<WirelessPoint>> getAllInvalidPoints() {
        HashMap<WirelessChannel, Collection<WirelessPoint>> map = new HashMap<>();

        for (WirelessChannel channel : WirelessRedstone.getStorageManager().getChannels()) {
            List<WirelessPoint> points = channel.getSigns().stream()
                    .filter(point -> Bukkit.getWorld(point.getWorld()) == null)
                    .collect(Collectors.toList());

            if (!points.isEmpty()) {
                map.put(channel, points);
            }
        }

        return map;
    }

}