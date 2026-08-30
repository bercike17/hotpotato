package hu.hotpotato;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GameManager {
    private final HotPotato plugin;
    private GameState state = GameState.NONE;
    private Player holder = null;
    private int timer = 0;
    private int explodeTime = 0;
    private final Map<UUID, PlayerData> savedData = new HashMap<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Set<UUID> participants = new HashSet<>();
    private BukkitRunnable lobbyTask;
    private BukkitRunnable countdownTask;
    private BukkitRunnable timerTask;
    private BukkitRunnable autoTask;
    private boolean autoEnabled;
    private int autoInterval;
    private int autoCounter = 0;
    private final NamespacedKey potatoKey;

    public enum GameState {
        NONE, LOBBY, COUNTDOWN, ACTIVE
    }

    public GameManager(HotPotato plugin) {
        this.plugin = plugin;
        this.potatoKey = new NamespacedKey(plugin, "hotpotato");
        this.autoEnabled = plugin.getConfig().getBoolean("settings.auto-enabled", false);
        this.autoInterval = plugin.getConfig().getInt("settings.auto-interval", 30);
        startAutoTimer();
    }

    public GameState getState() { return state; }
    public boolean isActive() { return state == GameState.ACTIVE; }
    public boolean isLobby() { return state == GameState.LOBBY; }
    public boolean isCountdown() { return state == GameState.COUNTDOWN; }
    public boolean isAutoEnabled() { return autoEnabled; }
    public int getAutoInterval() { return autoInterval; }
    public int getAutoCounter() { return autoCounter; }
    public Player getHolder() { return holder; }

    public void setAutoEnabled(boolean enabled) {
        this.autoEnabled = enabled;
        this.autoCounter = 0;
        if (enabled && (autoTask == null || autoTask.isCancelled())) {
            startAutoTimer();
        }
    }

    public void setAutoInterval(int minutes) {
        this.autoInterval = minutes;
        this.autoCounter = 0;
    }

    private void startAutoTimer() {
        autoTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!autoEnabled || state != GameState.NONE) return;
                autoCounter++;
                if (autoCounter >= autoInterval) {
                    autoCounter = 0;
                    World w = Bukkit.getWorld(plugin.getConfig().getString("settings.world", "world"));
                    if (w == null) return;
                    long online = w.getPlayers().stream().filter(p -> !eliminated.contains(p.getUniqueId())).count();
                    if (online >= plugin.getConfig().getInt("settings.min-players", 2)) {
                        Bukkit.broadcastMessage(msg("event.auto-start"));
                        startEvent();
                    }
                }
            }
        };
        autoTask.runTaskTimer(plugin, 1200L, 1200L);
    }

    public void startEvent() {
        if (state != GameState.NONE) return;
        World w = Bukkit.getWorld(plugin.getConfig().getString("settings.world", "world"));
        if (w == null) {
            Bukkit.getLogger().warning("[HotPotato] " + ChatColor.stripColor(msg("admin.world-not-found")));
            return;
        }

        state = GameState.LOBBY;

        Bukkit.broadcastMessage(msg("event.lobby-started"));
        Bukkit.broadcastMessage(msg("event.lobby-time", "%time%", 
            String.valueOf(plugin.getConfig().getInt("settings.lobby-time", 30))));
        Bukkit.broadcastMessage(msg("event.lobby-auto-join"));

        for (Player p : w.getPlayers()) {
            addParticipant(p);
        }

        lobbyTask = new BukkitRunnable() {
            int remaining = plugin.getConfig().getInt("settings.lobby-time", 30);
            @Override
            public void run() {
                if (state != GameState.LOBBY) { cancel(); return; }
                if (remaining <= 0) {
                    cancel();
                    startCountdown();
                    return;
                }
                if (remaining % 5 == 0 || remaining <= 3) {
                    Bukkit.broadcastMessage(msg("event.lobby-ending", "%remaining%", String.valueOf(remaining)));
                }
                remaining--;
            }
        };
        lobbyTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startCountdown() {
        if (participants.size() < plugin.getConfig().getInt("settings.min-players", 2)) {
            Bukkit.broadcastMessage(msg("event.not-enough-players-cancel"));
            resetEvent();
            return;
        }

        state = GameState.COUNTDOWN;
        int countdownTime = plugin.getConfig().getInt("settings.countdown-time", 10);

        Bukkit.broadcastMessage(msg("event.countdown-start"));
        Bukkit.broadcastMessage(msg("event.countdown-time", "%time%", String.valueOf(countdownTime)));

        countdownTask = new BukkitRunnable() {
            int remaining = countdownTime;
            @Override
            public void run() {
                if (state != GameState.COUNTDOWN) { cancel(); return; }
                if (remaining <= 0) {
                    cancel();
                    startGame();
                    return;
                }
                Bukkit.broadcastMessage(msg("event.countdown-number", "%number%", String.valueOf(remaining)));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
                remaining--;
            }
        };
        countdownTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startGame() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) players.add(p);
        }

        if (players.size() < 2) {
            Bukkit.broadcastMessage(msg("event.not-enough-players"));
            resetEvent();
            return;
        }

        state = GameState.ACTIVE;
        timer = 0;
        int min = plugin.getConfig().getInt("settings.min-explosion-time", 30);
        int max = plugin.getConfig().getInt("settings.max-explosion-time", 120);
        explodeTime = new Random().nextInt(max - min + 1) + min;

        Bukkit.broadcastMessage(msg("event.game-started"));
        Bukkit.broadcastMessage(msg("event.explosion-time", "%time%", String.valueOf(explodeTime)));

        Player first = players.get(new Random().nextInt(players.size()));
        setHolder(first);

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.ACTIVE) { cancel(); return; }
                tick();
            }
        };
        timerTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void tick() {
        timer++;
        if (timer % 10 == 0 && holder != null && holder.isOnline()) {
            Bukkit.broadcastMessage(msg("game.timer-holder", 
                "%player%", holder.getName(),
                "%current%", String.valueOf(timer),
                "%max%", String.valueOf(explodeTime)));
        }
        if (timer >= explodeTime) {
            explode();
        }
    }

    private void explode() {
        if (holder == null || !holder.isOnline()) {
            checkWin();
            return;
        }
        Player victim = holder;
        Location loc = victim.getLocation();
        loc.getWorld().createExplosion(loc, 3.0f, false, false);
        loc.getWorld().strikeLightningEffect(loc);
        Bukkit.broadcastMessage(msg("game.player-exploded", "%player%", victim.getName()));
        eliminatePlayer(victim, false);
    }

    public void eliminatePlayer(Player player, boolean isQuit) {
        UUID uuid = player.getUniqueId();
        eliminated.add(uuid);
        participants.remove(uuid);

        if (!isQuit) {
            restorePlayer(player);
            player.setGlowing(false);
            player.teleport(plugin.getSpawnLocation());
            player.sendMessage(msg("game.eliminated"));
        } else {
            savedData.remove(uuid);
        }

        if (holder != null && holder.getUniqueId().equals(uuid)) {
            holder = null;
            if (state == GameState.ACTIVE) passToRandom();
        }
    }

    public void removeParticipant(Player player) {
        UUID uuid = player.getUniqueId();
        if (!participants.contains(uuid)) return;
        participants.remove(uuid);
        restorePlayer(player);
        player.setGlowing(false);
        player.sendMessage(msg("game.eliminated-lobby"));
        updateGlow();
    }

    public void handleQuit(Player player) {
        if (state == GameState.LOBBY || state == GameState.COUNTDOWN) {
            if (participants.contains(player.getUniqueId())) {
                restorePlayer(player);
                player.setGlowing(false);
                participants.remove(player.getUniqueId());
            }
        } else if (state == GameState.ACTIVE) {
            if (participants.contains(player.getUniqueId())) {
                restorePlayer(player);
                eliminatePlayer(player, true);
            }
        }
    }

    public void handleJoin(Player player) {
        if (state == GameState.LOBBY) {
            World arenaWorld = null;
            Location arena = plugin.getArenaLocation();
            if (arena != null) arenaWorld = arena.getWorld();
            else arenaWorld = Bukkit.getWorld(plugin.getConfig().getString("settings.world", "world"));
            
            if (arenaWorld != null && player.getWorld().equals(arenaWorld)) {
                if (!participants.contains(player.getUniqueId())) {
                    addParticipant(player);
                    Bukkit.broadcastMessage(msg("player.joined-broadcast", "%player%", player.getName()));
                }
            }
        } else if (state == GameState.COUNTDOWN || state == GameState.ACTIVE) {
            if (eliminated.contains(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.setGlowing(false);
                    player.teleport(plugin.getSpawnLocation());
                    eliminated.remove(player.getUniqueId());
                });
            }
        }
    }

    public void joinEvent(Player player) {
        if (state == GameState.NONE) {
            player.sendMessage(msg("player.no-event-running"));
            return;
        }
        if (participants.contains(player.getUniqueId())) {
            player.sendMessage(msg("player.already-joined"));
            return;
        }
        if (state == GameState.LOBBY) {
            Location arena = plugin.getArenaLocation();
            if (arena != null) {
                player.teleport(arena);
            }
            addParticipant(player);
            Bukkit.broadcastMessage(msg("player.joined-broadcast", "%player%", player.getName()));
            return;
        }
        if (state == GameState.COUNTDOWN) {
            player.sendMessage(msg("player.lobby-closed"));
            return;
        }
        if (state == GameState.ACTIVE) {
            player.sendMessage(msg("player.game-running"));
            return;
        }
    }

    public void leaveEvent(Player player) {
        if (state == GameState.NONE || !participants.contains(player.getUniqueId())) {
            player.sendMessage(msg("player.not-in-event"));
            return;
        }
        if (state == GameState.LOBBY || state == GameState.COUNTDOWN) {
            removeParticipant(player);
            player.teleport(plugin.getSpawnLocation());
            Bukkit.broadcastMessage(msg("game.player-left", "%player%", player.getName()));
            return;
        }
        if (state == GameState.ACTIVE) {
            player.sendMessage(msg("player.left-active"));
            eliminatePlayer(player, false);
            Bukkit.broadcastMessage(msg("game.player-eliminated", "%player%", player.getName()));
            return;
        }
    }

    private void addParticipant(Player p) {
        if (participants.contains(p.getUniqueId())) return;
        participants.add(p.getUniqueId());
        saveAndClear(p);
        p.sendMessage(msg("player.joined"));
    }

    public void passPotato(Player from, Player to) {
        if (state != GameState.ACTIVE) return;
        if (holder == null || !holder.getUniqueId().equals(from.getUniqueId())) return;

        removePotato(from);
        setHolder(to);

        from.sendMessage(msg("game.potato-passed-direct", "%target%", to.getName()));
        to.sendMessage(msg("game.potato-received", "%player%", from.getName()));
        from.playSound(from.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        to.playSound(to.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    private void setHolder(Player player) {
        this.holder = player;
        givePotato(player);
        player.sendMessage(msg("game.you-are-holder"));
        updateGlow();
    }

    private void givePotato(Player player) {
        ItemStack potato = new ItemStack(Material.BAKED_POTATO);
        ItemMeta meta = potato.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(plugin.getConfig().getString("messages.potato-name", "&c&lFORRO KRUMPLI")));
            List<String> lore = new ArrayList<>();
            lore.add(color(plugin.getConfig().getString("messages.potato-lore", "&7Add tovabb mielott felrobban!")));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(potatoKey, PersistentDataType.BYTE, (byte) 1);
            potato.setItemMeta(meta);
        }
        player.getInventory().addItem(potato);
    }

    private void removePotato(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isPotato(item)) {
                player.getInventory().remove(item);
                break;
            }
        }
    }

    public boolean isPotato(ItemStack item) {
        if (item == null || item.getType() != Material.BAKED_POTATO) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(potatoKey, PersistentDataType.BYTE);
    }

    private void passToRandom() {
        List<Player> alive = new ArrayList<>();
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) alive.add(p);
        }
        if (alive.isEmpty()) {
            resetEvent();
            return;
        }
        setHolder(alive.get(new Random().nextInt(alive.size())));
        Bukkit.broadcastMessage(msg("game.potato-passed-random"));
        checkWin();
    }

    private void checkWin() {
        List<Player> alive = new ArrayList<>();
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) alive.add(p);
        }

        if (alive.size() <= 1) {
            if (alive.size() == 1) {
                Player winner = alive.get(0);
                String rewardType = plugin.getConfig().getString("settings.reward-type", "item");

                if (rewardType.equalsIgnoreCase("command")) {
                    List<String> commands = plugin.getConfig().getStringList("settings.reward-commands");
                    for (String cmd : commands) {
                        String formatted = cmd.replace("%player%", winner.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
                    }
                    winner.sendMessage(msg("game.reward-command"));
                } else {
                    String rewardMat = plugin.getConfig().getString("settings.reward-material", "DIAMOND_BLOCK");
                    int rewardAmt = plugin.getConfig().getInt("settings.reward-amount", 5);
                    Material mat = Material.matchMaterial(rewardMat);
                    if (mat != null) {
                        winner.getInventory().addItem(new ItemStack(mat, rewardAmt));
                    }
                    winner.sendMessage(msg("game.reward-item"));
                }

                Bukkit.broadcastMessage(msg("game.winner-announce", "%player%", winner.getName()));
                restorePlayer(winner);
                winner.setGlowing(false);
                winner.teleport(plugin.getSpawnLocation());
            } else {
                Bukkit.broadcastMessage(msg("game.no-winner"));
            }
            resetEvent();
        }
    }

    public void stopEvent() {
        if (state == GameState.NONE) return;

        if (lobbyTask != null) lobbyTask.cancel();
        if (countdownTask != null) countdownTask.cancel();
        if (timerTask != null) timerTask.cancel();

        for (UUID uuid : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                restorePlayer(p);
                p.setGlowing(false);
                if (state == GameState.ACTIVE || state == GameState.COUNTDOWN) {
                    p.teleport(plugin.getSpawnLocation());
                }
            }
        }
        resetEvent();
        Bukkit.broadcastMessage(msg("event.stopped"));
    }

    private void resetEvent() {
        clearGlow();
        if (lobbyTask != null) lobbyTask.cancel();
        if (countdownTask != null) countdownTask.cancel();
        if (timerTask != null) timerTask.cancel();
        participants.clear();
        eliminated.clear();
        savedData.clear();
        holder = null;
        state = GameState.NONE;
    }

    private void saveAndClear(Player player) {
        savedData.put(player.getUniqueId(), new PlayerData(player));
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setExtraContents(new ItemStack[1]);
        player.setExp(0);
        player.setLevel(0);
    }

    public void restorePlayer(Player player) {
        PlayerData data = savedData.remove(player.getUniqueId());
        if (data != null) {
            data.restore(player);
        } else {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setExtraContents(new ItemStack[1]);
        }
    }

    public boolean isParticipant(Player player) {
        return participants.contains(player.getUniqueId());
    }

    public void sendStatus(CommandSender sender) {
        sender.sendMessage(color("&6=== Hot Potato Status ==="));
        sender.sendMessage(color("&eAllapot: &f" + state));
        if (state == GameState.LOBBY) {
            sender.sendMessage(color("&eLobby van, lehet csatlakozni!"));
        } else if (state == GameState.COUNTDOWN) {
            sender.sendMessage(color("&eVisszaszamlalas a jatek indulasaig..."));
        } else if (state == GameState.ACTIVE) {
            sender.sendMessage(color("&eIdozito: &c" + timer + "/" + explodeTime + " &emsp"));
            sender.sendMessage(color("&eKrumpli: " + (holder != null ? "&c" + holder.getName() : "&7Senki")));
            sender.sendMessage(color("&eJatekosok: &f" + participants.size()));
        }
        sender.sendMessage(color("&eAuto: " + (autoEnabled ? "&aBE" : "&cKI") + " &7(" + autoInterval + " perc)"));
    }

    private void updateGlow() {
        if (!plugin.getConfig().getBoolean("settings.glow-enabled", true)) return;
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.setGlowing(false);
        }
        if (holder != null) {
            Player p = Bukkit.getPlayer(holder.getUniqueId());
            if (p != null) p.setGlowing(true);
        }
    }

    private void clearGlow() {
        for (UUID uuid : new HashSet<>(participants)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.setGlowing(false);
        }
        for (UUID uuid : new HashSet<>(eliminated)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.setGlowing(false);
        }
        if (holder != null) {
            Player p = Bukkit.getPlayer(holder.getUniqueId());
            if (p != null) p.setGlowing(false);
        }
    }

    private String msg(String path, String... placeholders) {
        return plugin.getMsg(path, placeholders);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}