package fr.merci.manhunt;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ManhuntPlugin extends JavaPlugin {

    private GameManager game;

    @Override
    public void onEnable() {
        Settings settings = new Settings(this);
        game = new GameManager(this, settings);

        PluginCommand cmd = getCommand("manhunt");
        if (cmd == null) {
            getLogger().severe("Commande 'manhunt' introuvable dans plugin.yml !");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ManhuntCommand handler = new ManhuntCommand(game, settings);
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);

        getServer().getPluginManager().registerEvents(new ManhuntListener(this, game), this);
        getLogger().info("Manhunt activé.");
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
    }
}
