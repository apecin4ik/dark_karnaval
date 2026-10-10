package me.darkcarnival;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class DarkCarnival extends JavaPlugin {

    private EventManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Схематика лежит внутри jar; копируем в plugins/DarkCarnival/schematics/, если её там ещё нет
        saveResource("schematics/carnival.schem", false);
        manager = new EventManager(this);
        manager.load();
        getServer().getPluginManager().registerEvents(new EventListener(manager), this);
        PluginCommand cmd = getCommand("event");
        if (cmd != null) {
            EventCommand ec = new EventCommand(manager);
            cmd.setExecutor(ec);
            cmd.setTabCompleter(ec);
        }
        manager.startScheduler();
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }
}
