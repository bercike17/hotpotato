package hu.hotpotato;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerData {
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack[] extra;
    private final float exp;
    private final int level;
    private final Location lastLocation;
    
    public PlayerData(Player player) {
        this.contents = player.getInventory().getContents().clone();
        this.armor = player.getInventory().getArmorContents().clone();
        this.extra = player.getInventory().getExtraContents().clone();
        this.exp = player.getExp();
        this.level = player.getLevel();
        this.lastLocation = player.getLocation().clone();
    }
    
    public void restore(Player player) {
        player.getInventory().setContents(contents);
        player.getInventory().setArmorContents(armor);
        player.getInventory().setExtraContents(extra);
        player.setExp(exp);
        player.setLevel(level);
    }
    
    public Location getLastLocation() {
        return lastLocation;
    }
}