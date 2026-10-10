package me.darkcarnival;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class EventCommand implements CommandExecutor, TabCompleter {

    private final EventManager manager;

    public EventCommand(EventManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "start":
                if (!sender.hasPermission("darkcarnival.admin")) {
                    sender.sendMessage(manager.prefix() + "§cНет прав.");
                    return true;
                }
                if (manager.isActive()) {
                    sender.sendMessage(manager.prefix() + "§cИвент уже идёт.");
                } else if (!manager.start()) {
                    sender.sendMessage(manager.prefix() + "§cНе удалось запустить (смотри консоль: мир или место не найдены).");
                }
                return true;
            case "stop":
                if (!sender.hasPermission("darkcarnival.admin")) {
                    sender.sendMessage(manager.prefix() + "§cНет прав.");
                    return true;
                }
                if (!manager.isActive()) {
                    sender.sendMessage(manager.prefix() + "§cИвент сейчас не идёт.");
                } else {
                    manager.stop();
                    sender.sendMessage(manager.prefix() + "§eИвент остановлен без наград.");
                }
                return true;
            case "reload":
                if (!sender.hasPermission("darkcarnival.admin")) {
                    sender.sendMessage(manager.prefix() + "§cНет прав.");
                    return true;
                }
                manager.load();
                sender.sendMessage(manager.prefix() + "§aКонфиг перезагружен.");
                return true;
            case "top":
                for (String line : manager.topLines()) {
                    sender.sendMessage(line);
                }
                return true;
            default:
                if (!sender.hasPermission("darkcarnival.use")) {
                    sender.sendMessage(manager.prefix() + "§cНет прав.");
                    return true;
                }
                java.util.UUID id = (sender instanceof Player) ? ((Player) sender).getUniqueId() : null;
                for (String line : manager.infoLines(id)) {
                    sender.sendMessage(line);
                }
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("top");
            if (sender.hasPermission("darkcarnival.admin")) {
                subs.add("start");
                subs.add("stop");
                subs.add("reload");
            }
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
