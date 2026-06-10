package ru.Natro.reportplugin;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.form.element.ElementButton;

public class ReportCommand extends Command {

    public ReportCommand() {
        super("report", "Report a player", "/report");
        setPermission("reportplugin.use");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return false;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("reportplugin.use")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return false;
        }

        openReportForm(player);
        return true;
    }

    private void openReportForm(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§cReport a Player", "§7Select the player you want to report:");

        ReportPlugin plugin = ReportPlugin.get();

        for (Player target : plugin.getServer().getOnlinePlayers().values()) {
            if (target == player) continue;

            String prefix = plugin.getPlayerPrefix(target);
            String buttonText = prefix.isEmpty()
                ? "§e" + target.getName()
                : prefix + " §e" + target.getName();

            form.addButton(new ElementButton(buttonText));
        }

        player.showFormWindow(form);
    }
}
