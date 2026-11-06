package fr.merci.manhunt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

/** Tous les paramètres modifiables en jeu avec /manhunt regler. Sauvegardés dans config.yml. */
public final class Settings {

    public enum Type { INT, BOOL, CHOICE }

    public record Option(String key, Type type, int min, int max, Object def, List<String> choices, String desc) {}

    public static final Map<String, Option> OPTIONS = new LinkedHashMap<>();

    private static void add(Option o) {
        OPTIONS.put(o.key(), o);
    }

    static {
        add(new Option("delai-chasseurs", Type.INT, 0, 600, 30, List.of(),
                "Secondes d'attente des chasseurs au départ (avance du coureur)"));
        add(new Option("temps-coureur", Type.INT, 0, 720, 0, List.of(),
                "Temps max du coureur pour finir, en minutes (0 = illimité)"));
        add(new Option("kit-coureur", Type.BOOL, 0, 0, true, List.of(),
                "Aide de départ du coureur : planches + hache + pioche en bois"));
        add(new Option("quantite-planches", Type.INT, 1, 64, 20, List.of(),
                "Nombre de planches du kit du coureur"));
        add(new Option("nourriture", Type.CHOICE, 0, 0, "aucune", List.of("aucune", "coureur", "chasseurs", "tous"),
                "Nourriture de départ : pour qui ?"));
        add(new Option("quantite-nourriture", Type.INT, 1, 64, 16, List.of(),
                "Quantité de steaks cuits donnés au départ"));
        add(new Option("intervalle-boussole", Type.INT, 1, 30, 2, List.of(),
                "Fréquence de mise à jour automatique de la boussole (secondes)"));
        add(new Option("tp-depart", Type.BOOL, 0, 0, false, List.of(),
                "Téléporte les chasseurs sur le coureur au départ"));
    }

    private final ManhuntPlugin plugin;

    public Settings(ManhuntPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        for (Option o : OPTIONS.values()) {
            c.addDefault("options." + o.key(), o.def());
        }
        c.options().copyDefaults(true);
        plugin.saveConfig();
    }

    private static String path(String key) {
        return "options." + key;
    }

    public int getInt(String key) {
        Option o = OPTIONS.get(key);
        int v = plugin.getConfig().getInt(path(key), (Integer) o.def());
        return Math.max(o.min(), Math.min(o.max(), v));
    }

    public boolean getBool(String key) {
        Option o = OPTIONS.get(key);
        return plugin.getConfig().getBoolean(path(key), (Boolean) o.def());
    }

    public String getChoice(String key) {
        Option o = OPTIONS.get(key);
        String v = plugin.getConfig().getString(path(key), (String) o.def());
        if (v == null || !o.choices().contains(v.toLowerCase(Locale.ROOT))) {
            return (String) o.def();
        }
        return v.toLowerCase(Locale.ROOT);
    }

    public String display(String key) {
        Option o = OPTIONS.get(key);
        return switch (o.type()) {
            case INT -> String.valueOf(getInt(key));
            case BOOL -> getBool(key) ? "oui" : "non";
            case CHOICE -> getChoice(key);
        };
    }

    /** Retourne null si OK, sinon un message d'erreur. */
    public String set(String rawKey, String raw) {
        String key = rawKey.toLowerCase(Locale.ROOT);
        Option o = OPTIONS.get(key);
        if (o == null) {
            return "Option inconnue : " + rawKey + ". Fais /manhunt options.";
        }
        switch (o.type()) {
            case INT -> {
                int v;
                try {
                    v = Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    return "« " + raw + " » n'est pas un nombre entier.";
                }
                if (v < o.min() || v > o.max()) {
                    return "La valeur doit être entre " + o.min() + " et " + o.max() + ".";
                }
                plugin.getConfig().set(path(key), v);
            }
            case BOOL -> {
                Boolean b = parseBool(raw);
                if (b == null) {
                    return "Valeur invalide : mets oui ou non.";
                }
                plugin.getConfig().set(path(key), b);
            }
            case CHOICE -> {
                String v = raw.toLowerCase(Locale.ROOT);
                if (!o.choices().contains(v)) {
                    return "Valeur invalide. Choix : " + String.join(", ", o.choices());
                }
                plugin.getConfig().set(path(key), v);
            }
        }
        plugin.saveConfig();
        return null;
    }

    private static Boolean parseBool(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "oui", "true", "on", "yes", "1" -> Boolean.TRUE;
            case "non", "false", "off", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }
}
