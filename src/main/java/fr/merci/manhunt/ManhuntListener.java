package fr.merci.manhunt;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public final class ManhuntListener implements Listener {

    private final ManhuntPlugin plugin;
    private final GameManager game;

    public ManhuntListener(ManhuntPlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    private boolean frozen(Player p) {
        return game.getState() == GameManager.State.HEAD_START && game.isHunter(p);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!game.isActive()) {
            return;
        }
        Player p = e.getEntity();
        if (game.isRunner(p)) {
            game.finish(false, p.getName() + " est mort : les chasseurs gagnent !");
        } else if (game.isHunter(p)) {
            e.getDrops().removeIf(game::isTracker);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (game.isActive() && game.isHunter(p)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (game.isActive() && p.isOnline()) {
                    game.giveCompass(p);
                    game.updateCompassFor(p);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof EnderDragon && game.isActive()) {
            game.finish(true, "L'Ender Dragon est vaincu : le coureur gagne !");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (game.isActive() && game.isRunner(e.getPlayer())) {
            game.finish(null, "Le coureur s'est déconnecté, la partie est arrêtée.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        game.showBarTo(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        game.refresh();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack it = e.getItem();
        Player p = e.getPlayer();
        if (!game.isActive() || !game.isHunter(p) || !game.isTracker(it)) {
            return;
        }
        game.refresh();
        game.updateCompassFor(p);
        game.sendCompassInfo(p);
    }

    // --- Pendant l'avance du coureur, les chasseurs sont figés ---

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!frozen(e.getPlayer())) {
            return;
        }
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && frozen(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }
}
