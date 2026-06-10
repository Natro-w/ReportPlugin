package ru.Natro.reportplugin;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.window.FormWindowModal;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementInput;
import cn.nukkit.form.element.ElementLabel;

import java.util.*;

public class ReportListener implements Listener {

    // Tracks: reporter UUID -> target player name (for reason selection stage)
    private final Map<UUID, String> pendingTargets = new HashMap<>();

    @EventHandler(priority = EventPriority.NORMAL)
    public void onFormResponse(PlayerFormRespondedEvent event) {
        if (event.wasClosed()) return;

        Player player = event.getPlayer();
        ReportPlugin plugin = ReportPlugin.get();

        if (event.getWindow() instanceof FormWindowSimple) {
            handleSimpleForm(player, plugin, event);
        } else if (event.getWindow() instanceof FormWindowCustom) {
            handleCustomForm(player, plugin, event);
        }
    }

    private void handleSimpleForm(Player player, ReportPlugin plugin, PlayerFormRespondedEvent event) {
        FormWindowSimple form = (FormWindowSimple) event.getWindow();
        if (!(form.getResponse() instanceof FormResponseSimple)) return;

        FormResponseSimple response = (FormResponseSimple) form.getResponse();
        int clicked = response.getClickedButtonId();
        String title = form.getTitle();

        // ===== Player Selection Form =====
        if (title.contains("Report a Player")) {
            List<Player> players = new ArrayList<>();
            for (Player p : plugin.getServer().getOnlinePlayers().values()) {
                if (p != player) players.add(p);
            }
            if (clicked < 0 || clicked >= players.size()) return;

            String targetName = players.get(clicked).getName();
            pendingTargets.put(player.getUniqueId(), targetName);
            plugin.openReasonSelection(player, targetName);
            return;
        }

        // ===== Reports Dashboard =====
        if (title.contains("Reports Dashboard")) {
            switch (clicked) {
                case 0: plugin.openReportListForm(player, "OPEN"); break;
                case 1: plugin.openReportListForm(player, "CLAIMED"); break;
                case 2: plugin.openReportListForm(player, "RESOLVED"); break;
                case 3: plugin.openReportListForm(player, "REJECTED"); break;
                case 4: openSearchForm(player); break;
            }
            return;
        }

        // ===== Report List (by status) =====
        if (title.contains("Reports") || title.contains("Open") || title.contains("Claimed") || title.contains("Resolved") || title.contains("Rejected")) {
            String status = null;
            if (title.contains("Open")) status = "OPEN";
            else if (title.contains("Claimed")) status = "CLAIMED";
            else if (title.contains("Resolved")) status = "RESOLVED";
            else if (title.contains("Rejected")) status = "REJECTED";

            if (status != null) {
                List<ReportData> list = plugin.getStorage().getReportsByStatus(status);
                if (clicked >= 0 && clicked < list.size()) {
                    ReportData rd = list.get(clicked);
                    showReportDetail(player, rd);
                } else if (clicked == list.size()) {
                    // Back button
                    plugin.openReportsDashboard(player);
                }
            }
            return;
        }

        // ===== Reason Selection =====
        if (title.contains("Report ") && pendingTargets.containsKey(player.getUniqueId())) {
            String targetName = pendingTargets.get(player.getUniqueId());
            List<ReportReason> reasons = plugin.getStorage().getReasons();

            if (clicked == 0) {
                // Custom Reason
                openCustomReasonForm(player, targetName);
            } else if (clicked > 0 && clicked <= reasons.size()) {
                // Predefined reason
                ReportReason rr = reasons.get(clicked - 1);
                submitReport(player, targetName, rr.reason);
            }
            // Last button = Cancel, do nothing
            return;
        }

        // ===== Resolution Reason Form (after /report-end yes) =====
        if (title.contains("Select Resolution")) {
            List<ReportReason> reasons = plugin.getStorage().getReasons();
            if (clicked >= 0 && clicked < reasons.size()) {
                ReportReason rr = reasons.get(clicked);
                plugin.completeResolution(player, rr.reason, rr.duration);
            } else {
                // Cancel - mark as rejected
                plugin.getInvestigations().remove(player.getUniqueId());
                player.sendMessage("§cResolution cancelled. Use /report-end to try again.");
            }
            return;
        }

        // ===== Search Results =====
        if (title.contains("Search Results")) {
            // Back to dashboard
            if (clicked == 0) {
                plugin.openReportsDashboard(player);
            }
        }
    }

    private void handleCustomForm(Player player, ReportPlugin plugin, PlayerFormRespondedEvent event) {
        FormWindowCustom form = (FormWindowCustom) event.getWindow();
        if (!(form.getResponse() instanceof FormResponseCustom)) return;

        String title = form.getTitle();

        // ===== Custom Reason Input =====
        if (title.contains("Custom Reason")) {
            FormResponseCustom response = (FormResponseCustom) form.getResponse();
            String reason = response.getInputResponse(1); // index 1 is the input field
            if (reason == null || reason.trim().isEmpty()) {
                player.sendMessage("§cReason cannot be empty.");
                return;
            }
            UUID uuid = player.getUniqueId();
            String targetName = pendingTargets.remove(uuid);
            if (targetName == null) return;
            submitReport(player, targetName, reason.trim());
            return;
        }

        // ===== Search Input =====
        if (title.contains("Search Report")) {
            FormResponseCustom response = (FormResponseCustom) form.getResponse();
            String query = response.getInputResponse(1);
            if (query == null || query.trim().isEmpty()) {
                player.sendMessage("§cPlease enter a search term.");
                return;
            }
            List<ReportData> results = plugin.getStorage().searchReports(query.trim());
            showSearchResults(player, results);
        }
    }

    private void openCustomReasonForm(Player player, String targetName) {
        FormWindowCustom form = new FormWindowCustom("§bCustom Reason - " + targetName);
        form.addElement(new ElementLabel("§7Enter the reason for reporting §e" + targetName));
        form.addElement(new ElementInput("§eReason:", "Type your reason here..."));
        player.showFormWindow(form);
    }

    private void openSearchForm(Player player) {
        FormWindowCustom form = new FormWindowCustom("§dSearch Report");
        form.addElement(new ElementLabel("§7Search by ID, player name, or reason"));
        form.addElement(new ElementInput("§eSearch:", "Enter search term..."));
        player.showFormWindow(form);
    }

    private void showSearchResults(Player player, List<ReportData> results) {
        FormWindowSimple form = new FormWindowSimple("§dSearch Results §7(" + results.size() + ")", "§7Click to view details:");
        if (results.isEmpty()) {
            form.addButton(new ElementButton("§cNo results found\n§7Click to go back"));
        } else {
            for (ReportData rd : results) {
                String text = "§7#" + rd.id + " §e" + rd.target + "\n§7" + rd.reason + " §8- " + rd.status;
                form.addButton(new ElementButton(text));
            }
        }
        form.addButton(new ElementButton("§cBack"));
        player.showFormWindow(form);
    }

    private void showReportDetail(Player player, ReportData rd) {
        player.sendMessage("§l§6=== Report #" + rd.id + " ===");
        player.sendMessage("§7Reporter: §e" + rd.reporter);
        player.sendMessage("§7Target: §e" + rd.target);
        player.sendMessage("§7Reason: §e" + rd.reason);
        player.sendMessage("§7Status: " + statusColor(rd.status) + rd.status);
        player.sendMessage("§7World: §e" + rd.world);
        player.sendMessage("§7Time: §e" + Storage.formatTimestamp(rd.timestamp));
        if (!rd.handledBy.isEmpty()) {
            player.sendMessage("§7Handled By: §e" + rd.handledBy);
            player.sendMessage("§7Started: §e" + Storage.formatTimestamp(rd.startedAt));
            player.sendMessage("§7Ended: §e" + Storage.formatTimestamp(rd.endedAt));
            if (!rd.result.isEmpty()) {
                player.sendMessage("§7Result: §e" + rd.result);
                player.sendMessage("§7Punishment: §e" + rd.punishment);
            }
        }
        player.sendMessage("§7Use §e/report-start " + rd.id + "§7 to claim.");
    }

    private void submitReport(Player reporter, String targetName, String reason) {
        String world = reporter.getLevel() != null ? reporter.getLevel().getName() : "unknown";
        int id = ReportPlugin.get().getStorage().createReport(reporter.getName(), targetName, reason, world);
        reporter.sendMessage("§aReport #" + id + " submitted. Thank you!");

        // Notify staff
        String msg = "§l§c[REPORT #" + id + "]§r\n §7" + reporter.getName() + " §7reported §e" + targetName + " §7for §e" + reason;
        for (Player staff : reporter.getServer().getOnlinePlayers().values()) {
            if (staff.hasPermission("reportplugin.receive")) {
                staff.sendMessage(msg);
            }
        }

        // Discord
        DiscordWebhook discord = ReportPlugin.get().getDiscord();
        if (discord != null) {
            discord.send(reporter.getName(), targetName, reason);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingTargets.remove(uuid);
        ReportPlugin plugin = ReportPlugin.get();
        plugin.getInvestigations().remove(uuid);
        plugin.getVanishManager().cleanup(event.getPlayer());
    }

    private String statusColor(String status) {
        switch (status) {
            case "OPEN": return "§e";
            case "CLAIMED": return "§b";
            case "RESOLVED": return "§a";
            case "REJECTED": return "§c";
            default: return "§7";
        }
    }
}
