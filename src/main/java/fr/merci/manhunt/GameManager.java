package fr.merci.manhunt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class GameManager {

    public enum State { IDLE, HEAD_START, RUNNING }

    public static final String PREFIX = "&8[&6Manhunt&8] &r";

    private final ManhuntPlugin plugin;
    private final Settings settings;
    private final NamespacedKey trackerKey;

    private final Set<UUID> hunters = new LinkedHashSet<>();
    /** Dernière position connue du coureur dans chaque monde (clé = UUID du monde). */
    private final Map<UUID, Location> lastKnown = new HashMap<>();

    private UUID runner;
    private State state = State.IDLE;
    private BukkitTask task;
    private BossBar bar;

    private int headStartLeft;
    private int headStartTotal;
    private boolean timed;
    private int timeLeft;
    private int timeTotal;
    private int elapsed;
    private int tickCount;

    public GameManager(ManhuntPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.trackerKey = new NamespacedKey(plugin, "tracker");
    }

    // ------------------------------------------------------------------ utilitaires

    public static Component c(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    private static Component plain(String legacy) {
        return c(legacy).decoration(TextDecoration.ITALIC, false);
    }

    public static String fmt(int seconds) {
        int s = Math.max(0, seconds);
        if (s >= 3600) {
            return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        }
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString();
    }

    // ------------------------------------------------------------------ état

    public State getState() {
        return state;
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    public boolean isRunner(Player p) {
        return runner != null && runner.equals(p.getUniqueId());
    }

    public boolean isHunter(Player p) {
        return hunters.contains(p.getUniqueId());
    }

    public UUID getRunnerId() {
        return runner;
    }

    public Set<UUID> getHunters() {
        return Collections.unmodifiableSet(hunters);
    }

    public Player runnerPlayer() {
        return runner == null ? null : Bukkit.getPlayer(runner);
    }

    public List<Player> onlineHunters() {
        List<Player> list = new ArrayList<>();
        for (UUID id : hunters) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    public int getElapsed() {
        return elapsed;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public int getHeadStartLeft() {
        return headStartLeft;
    }

    public boolean isTimed() {
        return timed;
    }

    // ------------------------------------------------------------------ rôles

    /** @return null si OK, sinon message d'erreur */
    public String setRunner(Player p) {
        if (isActive()) {
            return "Impossible de changer le coureur pendant une partie.";
        }
        hunters.remove(p.getUniqueId());
        runner = p.getUniqueId();
        return null;
    }

    /** @return null si OK, sinon message d'erreur */
    public String addHunter(Player p) {
        if (isRunner(p)) {
            return p.getName() + " est le coureur.";
        }
        if (!hunters.add(p.getUniqueId())) {
            return p.getName() + " est déjà chasseur.";
        }
        if (isActive()) {
            giveCompass(p);
            updateCompassFor(p);
            if (bar != null) {
                p.showBossBar(bar);
            }
        }
        return null;
    }

    public boolean removeHunterByName(String name) {
        for (UUID id : new ArrayList<>(hunters)) {
            if (nameOf(id).equalsIgnoreCase(name)) {
                hunters.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    removeTrackers(p);
                }
                return true;
            }
        }
        return false;
    }

    public String clearHunters() {
        if (isActive()) {
            return "Impossible de vider les chasseurs pendant une partie.";
        }
        hunters.clear();
        return null;
    }

    // ------------------------------------------------------------------ boussole

    public boolean isTracker(ItemStack it) {
        if (it == null || it.getType() != Material.COMPASS || !it.hasItemMeta()) {
            return false;
        }
        return it.getItemMeta().getPersistentDataContainer().has(trackerKey, PersistentDataType.BYTE);
    }

    public boolean hasTracker(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isTracker(it)) {
                return true;
            }
        }
        return false;
    }

    private void removeTrackers(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isTracker(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }

    public void giveCompass(Player p) {
        if (hasTracker(p)) {
            return;
        }
        ItemStack it = new ItemStack(Material.COMPASS);
        CompassMeta m = (CompassMeta) it.getItemMeta();
        m.displayName(plain("&6&lTraqueur"));
        m.lore(List.of(
                plain("&7Pointe vers le coureur."),
                plain("&7Clic droit pour mettre à jour.")));
        m.getPersistentDataContainer().set(trackerKey, PersistentDataType.BYTE, (byte) 1);
        Location t = lastKnown.get(p.getWorld().getUID());
        if (t != null) {
            m.setLodestone(t);
            m.setLodestoneTracked(false);
        }
        it.setItemMeta(m);
        give(p, it);
    }

    private static void give(Player p, ItemStack... items) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(items);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    /** Met à jour toutes les boussoles du chasseur vers la dernière position connue du coureur dans son monde. */
    public void updateCompassFor(Player h) {
        Location t = lastKnown.get(h.getWorld().getUID());
        if (t == null) {
            return;
        }
        PlayerInventory inv = h.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (!isTracker(it)) {
                continue;
            }
            CompassMeta m = (CompassMeta) it.getItemMeta();
            Location cur = m.getLodestone();
            if (cur != null && cur.getWorld() != null && cur.getWorld().equals(t.getWorld())
                    && cur.getBlockX() == t.getBlockX()
                    && cur.getBlockY() == t.getBlockY()
                    && cur.getBlockZ() == t.getBlockZ()) {
                continue;
            }
            m.setLodestone(t.clone());
            m.setLodestoneTracked(false);
            it.setItemMeta(m);
            inv.setItem(i, it);
        }
    }

    public void updateCompasses() {
        for (Player h : onlineHunters()) {
            updateCompassFor(h);
        }
    }

    public void recordRunner() {
        Player r = runnerPlayer();
        if (r != null) {
            lastKnown.put(r.getWorld().getUID(), r.getLocation());
        }
    }

    /** Rafraîchit immédiatement (changement de monde, etc.). */
    public void refresh() {
        if (!isActive()) {
            return;
        }
        recordRunner();
        updateCompasses();
    }

    public void sendCompassInfo(Player h) {
        Player r = runnerPlayer();
        if (r == null) {
            h.sendActionBar(c("&cLe coureur est hors ligne."));
            return;
        }
        if (r.getWorld().equals(h.getWorld())) {
            int d = (int) h.getLocation().distance(r.getLocation());
            h.sendActionBar(c("&6Coureur à &f" + d + " blocs"));
            return;
        }
        Location t = lastKnown.get(h.getWorld().getUID());
        if (t == null) {
            h.sendActionBar(c("&cAucune trace du coureur dans cette dimension."));
        } else {
            int d = (int) h.getLocation().distance(t);
            h.sendActionBar(c("&eLe coureur est dans une autre dimension &7(son dernier passage ici : " + d + " blocs)"));
        }
    }

    // ------------------------------------------------------------------ partie

    /** @return null si OK, sinon message d'erreur */
    public String start() {
        if (isActive()) {
            return "Une partie est déjà en cours.";
        }
        if (runner == null) {
            return "Aucun coureur défini. Utilise /manhunt coureur <joueur>.";
        }
        Player r = runnerPlayer();
        if (r == null) {
            return "Le coureur n'est pas en ligne.";
        }
        List<Player> hs = onlineHunters();
        if (hs.isEmpty()) {
            return "Aucun chasseur en ligne. Utilise /manhunt chasseur ajouter <joueur>.";
        }

        lastKnown.clear();
        tickCount = 0;
        elapsed = 0;
        int delay = settings.getInt("delai-chasseurs");

        if (settings.getBool("tp-depart")) {
            for (Player h : hs) {
                h.teleport(r.getLocation());
            }
        }

        resetPlayer(r);
        for (Player h : hs) {
            resetPlayer(h);
        }
        recordRunner();

        if (settings.getBool("kit-coureur")) {
            give(r,
                    new ItemStack(Material.OAK_PLANKS, settings.getInt("quantite-planches")),
                    new ItemStack(Material.WOODEN_AXE),
                    new ItemStack(Material.WOODEN_PICKAXE));
        }
        String food = settings.getChoice("nourriture");
        int amount = settings.getInt("quantite-nourriture");
        if (food.equals("coureur") || food.equals("tous")) {
            give(r, new ItemStack(Material.COOKED_BEEF, amount));
        }
        for (Player h : hs) {
            giveCompass(h);
            if (food.equals("chasseurs") || food.equals("tous")) {
                give(h, new ItemStack(Material.COOKED_BEEF, amount));
            }
        }

        bar = BossBar.bossBar(c("&6Manhunt"), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
        }

        if (delay > 0) {
            state = State.HEAD_START;
            headStartTotal = delay;
            headStartLeft = delay;
            PotionEffect blind = new PotionEffect(PotionEffectType.BLINDNESS, (delay + 2) * 20, 0, false, false, false);
            for (Player h : hs) {
                h.addPotionEffect(blind);
            }
            Bukkit.broadcast(c(PREFIX + "&aLa partie commence ! &f" + r.getName()
                    + " &acourt, les chasseurs sont bloqués pendant &f" + delay + "s&a."));
        } else {
            beginRun();
        }

        updateBar();
        updateCompasses();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return null;
    }

    private void resetPlayer(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(e.getType());
        }
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setLevel(0);
        p.setExp(0f);
    }

    private void beginRun() {
        state = State.RUNNING;
        elapsed = 0;
        int mins = settings.getInt("temps-coureur");
        timed = mins > 0;
        timeTotal = mins * 60;
        timeLeft = timeTotal;

        Title title = Title.title(
                c("&c&lChasseurs lâchés !"),
                c(timed ? "&7Le coureur a &f" + mins + " min&7 pour battre l'Ender Dragon" : "&7Bonne chance !"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800)));
        for (Player h : onlineHunters()) {
            h.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
        }
    }

    private void tick() {
        if (!isActive()) {
            return;
        }
        tickCount++;
        recordRunner();

        if (state == State.HEAD_START) {
            headStartLeft--;
            if (headStartLeft <= 0) {
                beginRun();
            } else if (headStartLeft <= 5) {
                for (Player h : onlineHunters()) {
                    h.sendActionBar(c("&eDépart dans &c" + headStartLeft));
                    h.playSound(h.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                }
            }
        } else {
            elapsed++;
            if (timed) {
                timeLeft--;
                if (timeLeft <= 0) {
                    finish(false, "Le temps du coureur est écoulé !");
                    return;
                }
            }
        }

        updateBar();
        if (tickCount % Math.max(1, settings.getInt("intervalle-boussole")) == 0) {
            updateCompasses();
        }
    }

    private void updateBar() {
        if (bar == null) {
            return;
        }
        if (state == State.HEAD_START) {
            bar.name(c("&eChasseurs libérés dans &f" + fmt(headStartLeft)));
            bar.color(BossBar.Color.YELLOW);
            bar.progress(clamp(headStartLeft / (float) Math.max(1, headStartTotal)));
        } else if (timed) {
            float ratio = clamp(timeLeft / (float) Math.max(1, timeTotal));
            bar.name(c("&cTemps restant du coureur : &f" + fmt(timeLeft)));
            bar.color(ratio > 0.5f ? BossBar.Color.GREEN : ratio > 0.2f ? BossBar.Color.YELLOW : BossBar.Color.RED);
            bar.progress(ratio);
        } else {
            bar.name(c("&aManhunt en cours : &f" + fmt(elapsed)));
            bar.color(BossBar.Color.GREEN);
            bar.progress(1f);
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public void showBarTo(Player p) {
        if (bar != null) {
            p.showBossBar(bar);
        }
    }

    /**
     * Termine la partie.
     *
     * @param runnerWins TRUE = victoire coureur, FALSE = victoire chasseurs, null = partie arrêtée
     */
    public void finish(Boolean runnerWins, String reason) {
        if (!isActive()) {
            return;
        }
        Component title;
        if (runnerWins == null) {
            title = c("&e&lPartie arrêtée");
        } else if (runnerWins) {
            title = c("&a&lVictoire du coureur !");
        } else {
            title = c("&c&lVictoire des chasseurs !");
        }
        Title t = Title.title(title, c("&7" + reason),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofSeconds(1)));
        Bukkit.broadcast(c(PREFIX + "&f" + reason));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(t);
            if (runnerWins != null) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }
        cleanup();
    }

    public void shutdown() {
        if (isActive()) {
            cleanup();
        }
    }

    private void cleanup() {
        state = State.IDLE;
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
            bar = null;
        }
        for (Player h : onlineHunters()) {
            h.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        lastKnown.clear();
    }

    // ------------------------------------------------------------------ statut

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        String st = switch (state) {
            case IDLE -> "&7Aucune partie en cours";
            case HEAD_START -> "&eDépart : chasseurs libérés dans &f" + fmt(headStartLeft);
            case RUNNING -> timed
                    ? "&aEn cours &7- temps restant du coureur : &f" + fmt(timeLeft)
                    : "&aEn cours &7- &f" + fmt(elapsed) + " &7écoulées";
        };
        lines.add("&6État : " + st);
        lines.add("&6Coureur : &f" + (runner == null ? "&7(aucun)" : nameOf(runner)));
        List<String> names = new ArrayList<>();
        for (UUID id : hunters) {
            names.add(nameOf(id));
        }
        lines.add("&6Chasseurs (" + names.size() + ") : &f" + (names.isEmpty() ? "&7(aucun)" : String.join("&7, &f", names)));
        return lines;
    }
}
