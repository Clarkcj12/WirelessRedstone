package net.licks92.WirelessRedstone.Libs;

import com.sun.rowset.CachedRowSetImpl;
import net.licks92.WirelessRedstone.WirelessRedstone;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import javax.sql.rowset.CachedRowSet;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

public class SQLite {

    private Plugin plugin;
    private String path;
    private Connection connection;
    private Boolean updateGlobalCache;

    private ArrayList<PreparedStatement> preparedStatements;
    private boolean isProcessing = false;

    public SQLite(Plugin plugin, String path, final Boolean updateGlobalCache) throws SQLException, ClassNotFoundException {
        this.plugin = plugin;
        this.path = path;
        this.updateGlobalCache = updateGlobalCache;
        this.preparedStatements = new ArrayList<>();

        openConnection();

        final Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (WirelessRedstone.getInstance() == null){
                    timer.cancel();
                    return;
                }

                if (!WirelessRedstone.getInstance().isEnabled()) { // Plugin was disabled
                    timer.cancel();
                    return;
                }

                if (getConnection() != null) {
                    try {
                        if (preparedStatements.size() > 0) {
                            PreparedStatement preparedStatement = preparedStatements.get(0);

                            if (preparedStatement == null) {
                                return;
                            }

                            WirelessRedstone.getWRLogger().debug("Excuting next preparedstatement.");

                            preparedStatement.execute();
                            preparedStatement.close();

                            if (updateGlobalCache) {
                                if (WirelessRedstone.getGlobalCache() == null)
                                    Bukkit.getScheduler().runTaskLater(WirelessRedstone.getInstance(), new Runnable() {
                                        @Override
                                        public void run() {
                                            WirelessRedstone.getGlobalCache().update(false); //We are already asking this async
                                        }
                                    }, 1L);
                                else WirelessRedstone.getGlobalCache().update(false);
                            }

                            preparedStatements.remove(0);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, 0, 50);
    }

    public Connection openConnection() throws SQLException, ClassNotFoundException {
        if (connection != null)
            return connection;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File file = new File(plugin.getDataFolder(), path);
        if (!(file.exists())) {
            try {
                file.createNewFile();
                WirelessRedstone.getWRLogger().debug("Created new DB file.");
            } catch (IOException e) {
                WirelessRedstone.getWRLogger().debug("Unable to create database!");
            }
        }

        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder().toPath().toString() + File.separator
                + path);

        WirelessRedstone.getWRLogger().debug("jdbc:sqlite:" + plugin.getDataFolder().toPath().toString() + File.separator
                + path);

        return connection;
    }

    public Connection getConnection() {
        return connection;
    }

    public void closeConnection() throws SQLException {
        connection.close();
    }

    public ResultSet query(final PreparedStatement preparedStatement) {
        try {
            return preparedStatement.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CachedRowSet query_test(final PreparedStatement preparedStatement) {
        CachedRowSet rowSet = null;

        if (getConnection() != null) {
            try {
                ExecutorService exe = Executors.newCachedThreadPool();

                Future<CachedRowSet> future = exe.submit(new Callable<CachedRowSet>() {
                    public CachedRowSet call() {
                        try {
                            ResultSet resultSet = preparedStatement.executeQuery();

                            CachedRowSet cachedRowSet = new CachedRowSetImpl();
                            cachedRowSet.populate(resultSet);
                            resultSet.close();

//                            preparedStatement.getConnection().close();
                            preparedStatement.close();
                            if (cachedRowSet.next()) {
                                return cachedRowSet;
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }

                        return null;
                    }
                });

                if (future.get() != null) {
                    rowSet = future.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        return rowSet;
    }

    /*
     * Execute a query
     *
     * @param preparedStatement query to be executed.
     */
    public void execute(final PreparedStatement preparedStatement) {
        execute(preparedStatement, updateGlobalCache);
    }

    /*
    * Execute a query
    *
    * @param preparedStatement query to be executed.
    * @param updateGlobalCache update the global cache
    */
    public void execute(final PreparedStatement preparedStatement, final Boolean updateGlobalCache) {
        preparedStatements.add(preparedStatement);
    }
}