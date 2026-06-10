package ru.Natro.reportplugin;

import cn.nukkit.Player;
import cn.nukkit.Server;

import java.util.*;

public class VanishManager {

    private static VanishManager instance;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishManager() {
        instance = this;
    }

    public static VanishManager get() {
        return instance;
    }

    public void vanish(Player staff) {
        UUID uuid = staff.getUniqueId();
        vanished.add(uuid);

        // Set spectator mode
        staff.setGamemode(Player.SPECTATOR);

        // Hide from all other players
        for (Player p : Server.getInstance().getOnlinePlayers().values()) {
            if (p != staff) {
                staff.hidePlayer(p);
                staff.despawnFrom(p);
            }
        }

        // Hide admin status
        staff.setShowAdmin(false);

        staff.sendMessage("§7You are now vanished in spectator mode.");
    }

    public void unvanish(Player staff) {
        UUID uuid = staff.getUniqueId();
        if (!vanished.contains(uuid)) return;
        vanished.remove(uuid);

        // Restore survival mode
        staff.setGamemode(Player.SURVIVAL);

        // Show to all players
        for (Player p : Server.getInstance().getOnlinePlayers().values()) {
            if (p != staff) {
                staff.showPlayer(p);
                staff.spawnTo(p);
            }
        }

        staff.setShowAdmin(true);
        staff.sendMessage("§7You are now visible again.");
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    public void removeFromAllForms(Player staff) {
        // Despawn from all players (completely hidden)
        for (Player p : Server.getInstance().getOnlinePlayers().values()) {
            if (p != staff) {
                staff.despawnFrom(p);
            }
        }
    }

    public void cleanup(Player player) {
        vanished.remove(player.getUniqueId());
    }
}
