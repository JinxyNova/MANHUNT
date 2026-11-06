package fr.merci.manhunt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class ManhuntCommand implements TabExecutor {

    private static final String ADMIN = "manhunt.admin";
    private static final List<String> PUBLIC_SUBS = List.of("aide", "options", "statut");
    private static final List<String> ADMIN_SUBS = List.of(
            "aide", "coureur", "chasseur", "demarrer", "arreter", "boussole", "regler", "options", "statut", "recharger");
    private static final List<String> HUNTER_SUBS = List.of("ajouter", "retirer", "liste", "vider", "tous");

    private final GameManager game;
    private final Settings settings;

    public ManhuntCommand(GameManager game, Settings settings) {
        this.game = game;
        this.settings = settings;
    }

    private static void send(CommandSender s, String legacy) {
        s.sendMessage(GameManager.c(GameManager.PREFIX + legacy));
    }

    private static void err(CommandSender s, String msg) {
        send(s, "&c" + msg);
    }

    // ------------------------------------------------------------------ exécution

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "aide", "help", "?" -> help(sender);
            case "options", "settings", "reglages" -> listOptions(sender);
            case "statut", "status", "info" -> status(sender);
            default -> {
                if (!sender.hasPermission(ADMIN)) {
                    err(sender, "Tu n'as pas la permission.");
                    return true;
                }
                admin(sender, sub, args);
            }
        }
        return true;
    }

    private void admin(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "coureur", "runner" -> {
                if (args.length < 2) {
                    err(sender, "Usage : /manhunt coureur <joueur>");
                    return;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    err(sender, "Joueur introuvable : " + args[1]);
                    return;
                }
                String e = game.setRunner(t);
                if (e != null) {
                    err(sender, e);
                    return;
                }
                Bukkit.broadcast(GameManager.c(GameManager.PREFIX + "&f" + t.getName() + " &aest le coureur !"));
            }
            case "chasseur", "hunter" -> hunter(sender, args);
            case "demarrer", "start" -> {
                String e = game.start();
                if (e != null) {
                    err(sender, e);
                }
            }
            case "arreter", "stop" -> {
                if (!game.isActive()) {
                    err(sender, "Aucune partie en cours.");
                } else {
                    game.finish(null, "Partie arrêtée par " + sender.getName() + ".");
                }
            }
            case "boussole", "compass" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        err(sender, "Joueur introuvable : " + args[1]);
                        return;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    err(sender, "Usage : /manhunt boussole <joueur>");
                    return;
                }
                if (game.hasTracker(target)) {
                    err(sender, target.getName() + " a déjà une boussole traqueuse.");
                    return;
                }
                game.giveCompass(target);
                game.updateCompassFor(target);
                send(sender, "&aBoussole donnée à &f" + target.getName() + "&a.");
            }
            case "regler", "set" -> {
                if (args.length < 3) {
                    err(sender, "Usage : /manhunt regler <option> <valeur>  (liste : /manhunt options)");
                    return;
                }
                String e = settings.set(args[1], args[2]);
                if (e != null) {
                    err(sender, e);
                    return;
                }
                String key = args[1].toLowerCase(Locale.ROOT);
                send(sender, "&e" + key + " &7= &f" + settings.display(key)
                        + (game.isActive() ? " &7(certaines options ne s'appliquent qu'à la prochaine partie)" : ""));
            }
            case "recharger", "reload" -> {
                settings.load();
                send(sender, "&aConfiguration rechargée.");
            }
            default -> err(sender, "Sous-commande inconnue. Fais /manhunt aide.");
        }
    }

    private void hunter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            err(sender, "Usage : /manhunt chasseur <ajouter|retirer|liste|vider|tous> [joueur]");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "ajouter", "add" -> {
                if (args.length < 3) {
                    err(sender, "Usage : /manhunt chasseur ajouter <joueur>");
                    return;
                }
                Player t = Bukkit.getPlayerExact(args[2]);
                if (t == null) {
                    err(sender, "Joueur introuvable : " + args[2]);
                    return;
                }
                String e = game.addHunter(t);
                if (e != null) {
                    err(sender, e);
                } else {
                    send(sender, "&f" + t.getName() + " &aest maintenant chasseur.");
                }
            }
            case "retirer", "remove" -> {
                if (args.length < 3) {
                    err(sender, "Usage : /manhunt chasseur retirer <joueur>");
                    return;
                }
                if (game.removeHunterByName(args[2])) {
                    send(sender, "&f" + args[2] + " &an'est plus chasseur.");
                } else {
                    err(sender, args[2] + " n'est pas chasseur.");
                }
            }
            case "liste", "list" -> {
                List<String> names = new ArrayList<>();
                for (UUID id : game.getHunters()) {
                    names.add(game.nameOf(id));
                }
                send(sender, "&6Chasseurs (" + names.size() + ") : &f"
                        + (names.isEmpty() ? "&7(aucun)" : String.join("&7, &f", names)));
            }
            case "vider", "clear" -> {
                String e = game.clearHunters();
                if (e != null) {
                    err(sender, e);
                } else {
                    send(sender, "&aListe des chasseurs vidée.");
                }
            }
            case "tous", "all" -> {
                int n = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!game.isRunner(p) && game.addHunter(p) == null) {
                        n++;
                    }
                }
                send(sender, "&f" + n + " &ajoueur(s) ajouté(s) comme chasseurs.");
            }
            default -> err(sender, "Usage : /manhunt chasseur <ajouter|retirer|liste|vider|tous> [joueur]");
        }
    }

    private void help(CommandSender s) {
        send(s, "&6Commandes Manhunt &7(alias : /mh)");
        String[][] lines = {
                {"coureur <joueur>", "Choisit le coureur"},
                {"chasseur ajouter|retirer <joueur>", "Gère les chasseurs"},
                {"chasseur liste|vider|tous", "Liste, vide, ou met tout le monde chasseur"},
                {"demarrer", "Lance la partie"},
                {"arreter", "Arrête la partie"},
                {"boussole [joueur]", "Donne une boussole traqueuse"},
                {"regler <option> <valeur>", "Change un paramètre"},
                {"options", "Liste des paramètres et leurs valeurs"},
                {"statut", "État de la partie"},
                {"recharger", "Recharge config.yml"},
        };
        for (String[] l : lines) {
            s.sendMessage(GameManager.c("&e/manhunt " + l[0] + " &8- &7" + l[1]));
        }
        s.sendMessage(GameManager.c("&7Le coureur gagne s'il tue l'Ender Dragon, les chasseurs gagnent s'ils le tuent."));
    }

    private void listOptions(CommandSender s) {
        send(s, "&6Paramètres &7(/manhunt regler <option> <valeur>)");
        for (Settings.Option o : Settings.OPTIONS.values()) {
            s.sendMessage(GameManager.c("&e" + o.key() + " &7= &f" + settings.display(o.key())
                    + " &8- &7" + o.desc()));
        }
    }

    private void status(CommandSender s) {
        for (String line : game.describe()) {
            s.sendMessage(GameManager.c(line));
        }
    }

    // ------------------------------------------------------------------ auto-complétion (TAB)

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            return filter(admin ? ADMIN_SUBS : PUBLIC_SUBS, args[0]);
        }
        if (!admin) {
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            switch (sub) {
                case "coureur", "runner", "boussole", "compass":
                    return filter(onlineNames(), args[1]);
                case "chasseur", "hunter":
                    return filter(HUNTER_SUBS, args[1]);
                case "regler", "set":
                    return filter(Settings.OPTIONS.keySet(), args[1]);
                default:
                    return List.of();
            }
        }

        if (args.length == 3) {
            switch (sub) {
                case "chasseur", "hunter": {
                    String action = args[1].toLowerCase(Locale.ROOT);
                    if (action.equals("ajouter") || action.equals("add")) {
                        return filter(onlineNames(), args[2]);
                    }
                    if (action.equals("retirer") || action.equals("remove")) {
                        List<String> names = new ArrayList<>();
                        for (UUID id : game.getHunters()) {
                            names.add(game.nameOf(id));
                        }
                        return filter(names, args[2]);
                    }
                    return List.of();
                }
                case "regler", "set": {
                    Settings.Option o = Settings.OPTIONS.get(args[1].toLowerCase(Locale.ROOT));
                    if (o == null) {
                        return List.of();
                    }
                    return switch (o.type()) {
                        case BOOL -> filter(List.of("oui", "non"), args[2]);
                        case CHOICE -> filter(o.choices(), args[2]);
                        case INT -> {
                            Set<String> sugg = new LinkedHashSet<>();
                            sugg.add(String.valueOf(o.def()));
                            sugg.add(String.valueOf(o.min()));
                            sugg.add(String.valueOf(o.max()));
                            yield filter(sugg, args[2]);
                        }
                    };
                }
                default:
                    return List.of();
            }
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
