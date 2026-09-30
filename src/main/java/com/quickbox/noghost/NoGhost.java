package com.quickbox.noghost;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class NoGhost extends JavaPlugin implements Listener {

    private final AtomicLong hitsReceived = new AtomicLong(0);
    private final AtomicLong hitsCancelled = new AtomicLong(0);
    private final AtomicLong hitsLogged = new AtomicLong(0);

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private boolean enabled;
    private boolean logConsole;
    private boolean logFile;
    private String serverName;

    private File logFileHandle;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        logFileHandle = new File(getDataFolder(), "hits.log");
        if (!logFileHandle.exists()) {
            try {
                logFileHandle.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create hits.log: " + e.getMessage());
            }
        }

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("noghost") != null) {
            getCommand("noghost").setExecutor(this);
            getCommand("noghost").setTabCompleter(this);
        }

        getLogger().info("NoGhost (QuickBox Edition) enabled. Logging to: " + logFileHandle.getAbsolutePath());
    }

    @Override
    public void onDisable() {
        getLogger().info("NoGhost disabled. Received=" + hitsReceived.get()
                + " Cancelled=" + hitsCancelled.get()
                + " Logged=" + hitsLogged.get());
    }

    private void loadConfigValues() {
        this.serverName = getConfig().getString("server-name", "QuickBox");
        this.enabled = getConfig().getBoolean("enabled", true);
        this.logConsole = getConfig().getBoolean("logging.console", true);
        this.logFile = getConfig().getBoolean("logging.file", true);
    }

    // =========================================================
    // EVENT LISTENER — LOGGER ONLY
    // =========================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!enabled) return;

        long received = hitsReceived.incrementAndGet();

        boolean cancelled = event.isCancelled();
        if (cancelled) {
            hitsCancelled.incrementAndGet();
        }

        String attackerName;
        if (event.getDamager() instanceof Player attacker) {
            attackerName = attacker.getName();
        } else {
            attackerName = event.getDamager().getType().name();
        }

        String targetName;
        if (event.getEntity() instanceof Player target) {
            targetName = target.getName();
        } else {
            targetName = event.getEntity().getType().name();
        }

        double damage = event.getFinalDamage();
        String world = event.getEntity().getWorld().getName();
        String time = dateFormat.format(new Date());

        String line = "[" + time + "]"
                + " attacker=" + attackerName
                + " target=" + targetName
                + " damage=" + String.format("%.2f", damage)
                + " world=" + world
                + " cancelled=" + cancelled;

        if (logConsole) {
            getLogger().info(line);
        }

        if (logFile) {
            writeToFile(line);
        }

        hitsLogged.incrementAndGet();
    }

    private synchronized void writeToFile(String line) {
        if (logFileHandle == null) return;
        try (PrintWriter out = new PrintWriter(new FileWriter(logFileHandle, true))) {
            out.println(line);
        } catch (IOException e) {
            getLogger().warning("Failed to write to hits.log: " + e.getMessage());
        }
    }

    // =========================================================
    // COMMANDS
    // =========================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("noghost")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Usage: /noghost <status|reload>");
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            if (!sender.hasPermission("noghost.status")) {
                sender.sendMessage(prefix() + getConfig().getString("messages.no-permission", "&cNo permission."));
                return true;
            }
            showStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("noghost.admin")) {
                sender.sendMessage(prefix() + getConfig().getString("messages.no-permission", "&cNo permission."));
                return true;
            }
            reloadConfig();
            loadConfigValues();
            sender.sendMessage(prefix() + getConfig().getString("messages.reload", "&aReloaded."));
            return true;
        }

        sender.sendMessage(prefix() + ChatColor.YELLOW + "Usage: /noghost <status|reload>");
        return true;
    }

    private void showStatus(CommandSender sender) {
        List<String> statusLines = getConfig().getStringList("messages.status");

        String status = enabled ? "&aENABLED" : "&cDISABLED";

        for (String raw : statusLines) {
            String line = raw
                    .replace("%status%", status)
                    .replace("%received%", String.valueOf(hitsReceived.get()))
                    .replace("%cancelled%", String.valueOf(hitsCancelled.get()))
                    .replace("%logged%", String.valueOf(hitsLogged.get()))
                    .replace("%server%", serverName);

            sender.sendMessage(color(line));
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String prefix() {
        return color(getConfig().getString("messages.prefix", "&8[&bNoGhost&8] "));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    // =========================================================
    // TAB COMPLETE
    // =========================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && command.getName().equalsIgnoreCase("noghost")) {
            String typed = args[0].toLowerCase();
            List<String> options = new ArrayList<>();
            if ("status".startsWith(typed)) options.add("status");
            if ("reload".startsWith(typed)) options.add("reload");
            return options;
        }
        return Collections.emptyList();
    }
                               }
