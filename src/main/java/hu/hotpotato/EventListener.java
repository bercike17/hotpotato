package hu.hotpotato;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class EventListener implements Listener {
    private final HotPotato plugin;
    private final GameManager game;

    public EventListener(HotPotato plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        game.handleJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        game.handleQuit(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (game.getState() == GameManager.GameState.NONE) return;
        if (!game.isParticipant(victim)) return;

        if (game.getHolder() != null && game.getHolder().getUniqueId().equals(victim.getUniqueId())) {
            e.getDrops().removeIf(game::isPotato);
        }

        if (game.getState() == GameManager.GameState.LOBBY || game.getState() == GameManager.GameState.COUNTDOWN) {
            game.removeParticipant(victim);
        } else {
            game.eliminatePlayer(victim, false);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (game.getState() != GameManager.GameState.ACTIVE) return;

        Entity clicked = e.getRightClicked();
        if (!(clicked instanceof Player target)) return;

        Player clicker = e.getPlayer();
        ItemStack hand = clicker.getInventory().getItemInMainHand();

        if (game.isPotato(hand)) {
            e.setCancelled(true);
            game.passPotato(clicker, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (game.getState() != GameManager.GameState.ACTIVE) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!game.isParticipant(victim) || !game.isParticipant(attacker)) return;
        
        if (game.getHolder() == null || !game.getHolder().getUniqueId().equals(attacker.getUniqueId())) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.getMsg("player.cannot-hit"));
            return;
        }
        
        e.setCancelled(false);
        e.setDamage(0);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (game.getState() != GameManager.GameState.ACTIVE) return;
        if (game.isPotato(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getMsg("player.cannot-drop"));
        }
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (game.getState() != GameManager.GameState.ACTIVE) return;
        if (!(e.getWhoClicked() instanceof Player)) return;

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        if (game.isPotato(current) || game.isPotato(cursor)) {
            if (e.getClickedInventory() != null &&
                !e.getClickedInventory().equals(e.getWhoClicked().getInventory())) {
                e.setCancelled(true);
                e.getWhoClicked().sendMessage(plugin.getMsg("player.cannot-move"));
            }
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();

        // Csak az eventben levo jatekosokra vonatkozik
        if (!game.isParticipant(player)) return;

        // Adminoknak (akiknek van hotpotato.start joguk) szabad minden
        if (player.hasPermission("hotpotato.start")) return;

        String message = e.getMessage();
        String[] args = message.split(" ");
        if (args.length == 0) return;

        String cmd = args[0].toLowerCase();

        // Hotpotato parancsok mindig menjenek
        if (cmd.startsWith("/hotpotato")) return;

        // Whitelist ellenorzese
        List<String> whitelist = plugin.getConfig().getStringList("command-whitelist");
        boolean allowed = false;

        for (String allowedCmd : whitelist) {
            String check = allowedCmd.toLowerCase();
            if (!check.startsWith("/")) {
                check = "/" + check;
            }
            if (cmd.equals(check)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            e.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Ezt a parancsot nem hasznalhatod az event alatt!");
        }
    }
}
