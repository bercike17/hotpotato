package hu.hotpotato;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class HotPotato extends JavaPlugin {
    
    private static HotPotato instance;
    private GameManager gameManager;
    private Location spawnLocation;
    private Location arenaLocation;
    
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadSpawn();
        loadArena();
        this.gameManager = new GameManager(this);
        getServer().getPluginManager().registerEvents(new EventListener(this, gameManager), this);
        getLogger().info("HotPotatoEvent enabled!");
    }
    
    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.getState() != GameManager.GameState.NONE) {
            gameManager.stopEvent();
        }
    }
    
    public static HotPotato getInstance() {
        return instance;
    }
    
    public GameManager getGameManager() {
        return gameManager;
    }
    
    public String getMsg(String path) {
        String prefix = getConfig().getString("messages.prefix", "&a&l[HOT POTATO] &r");
        String msg = getConfig().getString("messages." + path, "");
        if (msg == null || msg.isEmpty()) return "";
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }
    
    public String getMsg(String path, String... placeholders) {
        String msg = getMsg(path);
        if (msg.isEmpty()) return "";
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                msg = msg.replace(placeholders[i], placeholders[i+1]);
            }
        }
        return msg;
    }
    
    public Location getSpawnLocation() {
        if (spawnLocation == null) {
            World w = Bukkit.getWorld(getConfig().getString("settings.world", "world"));
            if (w != null) return w.getSpawnLocation();
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return spawnLocation.clone();
    }
    
    public void setSpawnLocation(Location loc) {
        this.spawnLocation = loc.clone();
        FileConfiguration cfg = getConfig();
        cfg.set("settings.spawn.world", loc.getWorld().getName());
        cfg.set("settings.spawn.x", loc.getX());
        cfg.set("settings.spawn.y", loc.getY());
        cfg.set("settings.spawn.z", loc.getZ());
        cfg.set("settings.spawn.yaw", loc.getYaw());
        cfg.set("settings.spawn.pitch", loc.getPitch());
        saveConfig();
    }
    
    private void loadSpawn() {
        FileConfiguration cfg = getConfig();
        if (cfg.contains("settings.spawn.world")) {
            World w = Bukkit.getWorld(cfg.getString("settings.spawn.world"));
            if (w != null) {
                spawnLocation = new Location(w,
                    cfg.getDouble("settings.spawn.x"),
                    cfg.getDouble("settings.spawn.y"),
                    cfg.getDouble("settings.spawn.z"),
                    (float) cfg.getDouble("settings.spawn.yaw"),
                    (float) cfg.getDouble("settings.spawn.pitch")
                );
            }
        }
    }
    
    public Location getArenaLocation() {
        if (arenaLocation == null) {
            World w = Bukkit.getWorld(getConfig().getString("settings.world", "world"));
            if (w != null) return w.getSpawnLocation();
            return null;
        }
        return arenaLocation.clone();
    }
    
    public void setArenaLocation(Location loc) {
        this.arenaLocation = loc.clone();
        FileConfiguration cfg = getConfig();
        cfg.set("settings.arena.world", loc.getWorld().getName());
        cfg.set("settings.arena.x", loc.getX());
        cfg.set("settings.arena.y", loc.getY());
        cfg.set("settings.arena.z", loc.getZ());
        cfg.set("settings.arena.yaw", loc.getYaw());
        cfg.set("settings.arena.pitch", loc.getPitch());
        saveConfig();
    }
    
    private void loadArena() {
        FileConfiguration cfg = getConfig();
        if (cfg.contains("settings.arena.world")) {
            World w = Bukkit.getWorld(cfg.getString("settings.arena.world"));
            if (w != null) {
                arenaLocation = new Location(w,
                    cfg.getDouble("settings.arena.x"),
                    cfg.getDouble("settings.arena.y"),
                    cfg.getDouble("settings.arena.z"),
                    (float) cfg.getDouble("settings.arena.yaw"),
                    (float) cfg.getDouble("settings.arena.pitch")
                );
            }
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("hotpotato")) return false;
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String sub = args[0].toLowerCase();
        boolean isPlayerCmd = sub.equals("join") || sub.equals("leave");
        
        if (!isPlayerCmd && !sender.hasPermission("hotpotato.admin")) {
            sender.sendMessage(getMsg("admin.no-permission"));
            return true;
        }
        
        switch (sub) {
            case "start":
                gameManager.startEvent();
                break;
            case "stop":
                gameManager.stopEvent();
                break;
            case "auto":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Hasznalat: /hotpotato auto <on|off|interval|status>");
                    return true;
                }
                handleAuto(sender, args);
                break;
            case "setspawn":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(getMsg("admin.not-player"));
                    return true;
                }
                setSpawnLocation(p.getLocation());
                sender.sendMessage(getMsg("admin.spawn-set"));
                break;
            case "setarena":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(getMsg("admin.not-player"));
                    return true;
                }
                setArenaLocation(p.getLocation());
                sender.sendMessage(getMsg("admin.arena-set"));
                break;
            case "status":
                gameManager.sendStatus(sender);
                break;
            case "join":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(getMsg("admin.not-player-join"));
                    return true;
                }
                gameManager.joinEvent(p);
                break;
            case "leave":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(getMsg("admin.not-player-leave"));
                    return true;
                }
                gameManager.leaveEvent(p);
                break;
            default:
                sendHelp(sender);
        }
        return true;
    }
    
    private void handleAuto(CommandSender sender, String[] args) {
        switch (args[1].toLowerCase()) {
            case "on":
                gameManager.setAutoEnabled(true);
                sender.sendMessage(getMsg("admin.auto-on"));
                break;
            case "off":
                gameManager.setAutoEnabled(false);
                sender.sendMessage(getMsg("admin.auto-off"));
                break;
            case "interval":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "Hasznalat: /hotpotato auto interval <perc>");
                    return;
                }
                try {
                    int min = Integer.parseInt(args[2]);
                    if (min < 1) throw new NumberFormatException();
                    gameManager.setAutoInterval(min);
                    sender.sendMessage(getMsg("admin.auto-interval", "%interval%", String.valueOf(min)));
                } catch (NumberFormatException e) {
                    sender.sendMessage(getMsg("admin.auto-invalid"));
                }
                break;
            case "status":
                sender.sendMessage(getMsg("admin.auto-status-header"));
                sender.sendMessage((gameManager.isAutoEnabled() ? ChatColor.GREEN + "BE" : ChatColor.RED + "KI"));
                sender.sendMessage(getMsg("admin.auto-status-next", "%remaining%", 
                    String.valueOf(gameManager.getAutoInterval() - gameManager.getAutoCounter())));
                break;
            default:
                sender.sendMessage(ChatColor.YELLOW + "Hasznalat: /hotpotato auto <on|off|interval|status>");
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(getMsg("help.header"));
        sender.sendMessage(getMsg("help.start"));
        sender.sendMessage(getMsg("help.stop"));
        sender.sendMessage(getMsg("help.auto"));
        sender.sendMessage(getMsg("help.setarena"));
        sender.sendMessage(getMsg("help.setspawn"));
        sender.sendMessage(getMsg("help.status"));
        sender.sendMessage(getMsg("help.join"));
        sender.sendMessage(getMsg("help.leave"));
    }
}