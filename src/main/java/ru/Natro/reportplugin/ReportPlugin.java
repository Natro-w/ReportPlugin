package ru.Natro.reportplugin;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.level.Location;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.platform.PlayerAdapter;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ReportPlugin extends PluginBase {

    private static ReportPlugin instance;
    private PlayerAdapter<Player> adapter;
    private DiscordWebhook discord;
    private Storage storage;
    private VanishManager vanishManager;
    private Config config;
    private String lobbyWorld;

    // Track which staff are in investigation mode: staffUUID -> reportId
    private final Map<UUID, Integer> investigations = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadConfig();

        storage = new Storage(new File(getDataFolder(), "data"));
        vanishManager = new VanishManager();

        try {
            LuckPerms lp = LuckPermsProvider.get();
            adapter = lp.getPlayerAdapter(Player.class);
            getLogger().info("LuckPerms hooked successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to hook LuckPerms: " + e.getMessage());
        }

        registerCommands();
        getServer().getPluginManager().registerEvents(new ReportListener(), this);

        // Load default reasons if empty
        if (storage.getReasons().isEmpty()) {
            storage.addReason("Kill Aura", 7 * 86400);
            storage.addReason("Auto Loot", 3 * 86400);
            storage.addReason("Auto Bridge", 7 * 86400);
            storage.addReason("Reach", 7 * 86400);
            storage.addReason("Fly", 7 * 86400);
            storage.addReason("Speed", 3 * 86400);
            storage.addReason("Spam", 86400);
            storage.addReason("Toxicity", 2 * 86400);
            storage.addReason("Bug Abuse", 14 * 86400);
        }

        getLogger().info("ReportPlugin v1.1.0 enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("ReportPlugin disabled.");
    }

    private void registerCommands() {
        // /report
        getServer().getCommandMap().register("report", new Command("report", "Report a player", "/report") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player)) { sender.sendMessage("§cOnly players can use this."); return false; }
                Player p = (Player) sender;
                if (!p.hasPermission("reportplugin.use")) { p.sendMessage("§cNo permission."); return false; }
                openPlayerSelection(p);
                return true;
            }
        });

        // /reports - GUI
        getServer().getCommandMap().register("reports", new Command("reports", "Open reports dashboard", "/reports") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player)) { sender.sendMessage("§cOnly players can use this."); return false; }
                Player p = (Player) sender;
                if (!p.hasPermission("reportplugin.receive")) { p.sendMessage("§cNo permission."); return false; }
                openReportsDashboard(p);
                return true;
            }
        });

        // /report-start <id>
        getServer().getCommandMap().register("report-start", new Command("report-start", "Claim and investigate a report", "/report-start <id>") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player)) { sender.sendMessage("§cOnly players can use this."); return false; }
                Player p = (Player) sender;
                if (!p.hasPermission("reportplugin.receive")) { p.sendMessage("§cNo permission."); return false; }
                if (args.length < 1) { p.sendMessage("§cUsage: /report-start <id>"); return false; }
                try {
                    int id = Integer.parseInt(args[0]);
                    startInvestigation(p, id);
                } catch (NumberFormatException e) {
                    p.sendMessage("§cInvalid report ID.");
                }
                return true;
            }
        });

        // /report-end <yes|no>
        getServer().getCommandMap().register("report-end", new Command("report-end", "End current investigation", "/report-end <yes|no>") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player)) { sender.sendMessage("§cOnly players can use this."); return false; }
                Player p = (Player) sender;
                if (!p.hasPermission("reportplugin.receive")) { p.sendMessage("§cNo permission."); return false; }
                if (args.length < 1) { p.sendMessage("§cUsage: /report-end <yes|no>"); return false; }
                String choice = args[0].toLowerCase();
                if (!choice.equals("yes") && !choice.equals("no")) { p.sendMessage("§cUse: /report-end yes or /report-end no"); return false; }
                endInvestigation(p, choice.equals("yes"));
                return true;
            }
        });

        // /report-list <player> <send|receive>
        getServer().getCommandMap().register("report-list", new Command("report-list", "List reports by player", "/report-list <player> <send|receive>") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player)) { sender.sendMessage("§cOnly players can use this."); return false; }
                Player p = (Player) sender;
                if (!p.hasPermission("reportplugin.receive")) { p.sendMessage("§cNo permission."); return false; }
                if (args.length < 2) { p.sendMessage("§cUsage: /report-list <player> <send|receive>"); return false; }
                String target = args[0];
                String mode = args[1].toLowerCase();
                if (!mode.equals("send") && !mode.equals("receive")) { p.sendMessage("§cUse: send or receive"); return false; }
                List<ReportData> list = mode.equals("send") ? storage.getReportsByReporter(target) : storage.getReportsByTarget(target);
                if (list.isEmpty()) { p.sendMessage("§7No reports found for §e" + target + "§7 (" + mode + ")."); return true; }
                p.sendMessage("§l§6=== Reports " + (mode.equals("send") ? "by" : "on") + " " + target + " (" + list.size() + ") ===");
                for (ReportData rd : list) {
                    p.sendMessage("§7#" + rd.id + " §e" + rd.reason + " §7- " + rd.status.toColoredDisplay() + " §7- " + Storage.formatTimestamp(rd.timestamp) + " §7- " + rd.world);
                    if (mode.equals("send")) {
                        p.sendMessage("  §7Target: §e" + rd.target);
                    } else {
                        p.sendMessage("  §7Reporter: §e" + rd.reporter);
                    }
                }
                return true;
            }
        });

        // /report-reason add|remove|list
        getServer().getCommandMap().register("report-reason", new Command("report-reason", "Manage report reasons", "/report-reason <add|remove|list>") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player)) { sender.sendMessage("§cOnly players can use this."); return false; }
                Player p = (Player) sender;
                if (!p.isOp()) { p.sendMessage("§cOnly operators can use this."); return false; }
                if (args.length < 1) { p.sendMessage("§cUsage: /report-reason <add|remove|list>"); return false; }
                switch (args[0].toLowerCase()) {
                    case "add":
                        if (args.length < 3) { p.sendMessage("§cUsage: /report-reason add <name> <duration>"); return false; }
                        String reason = args[1];
                        long duration = parseDuration(args[2]);
                        storage.addReason(reason, duration);
                        p.sendMessage("§aReason '§e" + reason + "§a' added with duration §e" + Storage.formatTime(duration) + "§a.");
                        break;
                    case "remove":
                        if (args.length < 2) { p.sendMessage("§cUsage: /report-reason remove <name>"); return false; }
                        storage.removeReason(args[1]);
                        p.sendMessage("§aReason '§e" + args[1] + "§a' removed.");
                        break;
                    case "list":
                        List<ReportReason> reasons = storage.getReasons();
                        if (reasons.isEmpty()) { p.sendMessage("§7No reasons configured."); return true; }
                        p.sendMessage("§l§6=== Report Reasons ===");
                        for (ReportReason rr : reasons) {
                            p.sendMessage("§e" + rr.reason + " §7- " + Storage.formatTime(rr.duration));
                        }
                        break;
                    default:
                        p.sendMessage("§cUsage: /report-reason <add|remove|list>");
                }
                return true;
            }
        });

        // /report-stats
        getServer().getCommandMap().register("report-stats", new Command("report-stats", "View staff statistics", "/report-stats") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player)) { sender.sendMessage("§cOnly players can use this."); return false; }
                Player p = (Player) sender;
                if (!p.hasPermission("reportplugin.receive")) { p.sendMessage("§cNo permission."); return false; }
                StaffStatsData ss = storage.getStaffStats(p.getUniqueId().toString());
                p.sendMessage("§l§6=== Your Stats ===");
                p.sendMessage("§7Reports Handled: §e" + ss.handled);
                p.sendMessage("§aConfirmed: §e" + ss.confirmed);
                p.sendMessage("§cFalse Reports: §e" + ss.falseReports);
                p.sendMessage("§7Avg Investigation: §e" + (ss.handled > 0 ? Storage.formatTime(ss.totalTime / ss.handled) : "N/A"));
                return true;
            }
        });

        // /report-admin
        getServer().getCommandMap().register("report-admin", new Command("report-admin", "Admin dashboard", "/report-admin") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player)) { sender.sendMessage("§cOnly players can use this."); return false; }
                Player p = (Player) sender;
                if (!p.isOp()) { p.sendMessage("§cOnly operators can use this."); return false; }
                p.sendMessage("§l§6=== Report Admin Dashboard ===");
                p.sendMessage("§7Total Reports: §e" + storage.getTotalReports());
                p.sendMessage("§eOpen: §e" + storage.getOpenCount() + " §7| §eClaimed: §e" + storage.getClaimedCount() + " §7| §aResolved: §e" + storage.getResolvedCount() + " §7| §cRejected: §e" + storage.getRejectedCount());
                p.sendMessage("");
                p.sendMessage("§l§6Top Reported Players:");
                for (Map.Entry<String, Integer> e : storage.getTopReportedPlayers(5).entrySet()) {
                    p.sendMessage("§7- §e" + e.getKey() + " §7(" + e.getValue() + ")");
                }
                p.sendMessage("");
                p.sendMessage("§l§6Top Reporters:");
                for (Map.Entry<String, Integer> e : storage.getTopReporters(5).entrySet()) {
                    p.sendMessage("§7- §e" + e.getKey() + " §7(" + e.getValue() + ")");
                }
                p.sendMessage("");
                p.sendMessage("§l§6Top Staff:");
                for (StaffStatsData ss : storage.getTopStaff(5)) {
                    p.sendMessage("§7- §e" + ss.uuid + " §7(" + ss.handled + " handled, " + ss.confirmed + " confirmed)");
                }
                return true;
            }
        });
    }

    // ========== FORM: Player Selection ==========

    public void openPlayerSelection(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§cReport a Player", "§7Select the player you want to report:");

        for (Player target : getServer().getOnlinePlayers().values()) {
            if (target == player) continue;
            String prefix = getPlayerPrefix(target);
            String buttonText = prefix.isEmpty() ? "§e" + target.getName() : prefix + " §e" + target.getName();
            form.addButton(new ElementButton(buttonText));
        }

        player.showFormWindow(form);
    }

    // ========== FORM: Reports Dashboard ==========

    public void openReportsDashboard(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§6Reports Dashboard", "§7Select a category:");
        form.addButton(new ElementButton("§eOpen Reports\n§7" + storage.getOpenCount() + " pending"));
        form.addButton(new ElementButton("§bClaimed Reports\n§7" + storage.getClaimedCount() + " in progress"));
        form.addButton(new ElementButton("§aResolved Reports\n§7" + storage.getResolvedCount() + " confirmed"));
        form.addButton(new ElementButton("§cRejected Reports\n§7" + storage.getRejectedCount() + " denied"));
        form.addButton(new ElementButton("§dSearch Report\n§7Find by ID, player, or reason"));
        player.showFormWindow(form);
    }

    public void openReportListForm(Player player, ReportStatus status) {
        List<ReportData> list = storage.getReportsByStatus(status);
        String title;
        switch (status) {
            case OPEN: title = "§eOpen Reports"; break;
            case CLAIMED: title = "§bClaimed Reports"; break;
            case RESOLVED: title = "§aResolved Reports"; break;
            case REJECTED: title = "§cRejected Reports"; break;
            default: title = "§7Reports";
        }
        FormWindowSimple form = new FormWindowSimple(title + " §7(" + list.size() + ")", "§7Click to view details:");
        for (ReportData rd : list) {
            String text = "§7#" + rd.id + " §e" + rd.target + "\n§7" + rd.reason + " §8- " + Storage.formatTimestamp(rd.timestamp);
            form.addButton(new ElementButton(text));
        }
        form.addButton(new ElementButton("§cBack"));
        player.showFormWindow(form);
    }

    // ========== FORM: Reason Selection ==========

    public void openReasonSelection(Player reporter, String targetName) {
        FormWindowSimple form = new FormWindowSimple("§l§cReport " + targetName, "§7Select a reason:");
        form.addButton(new ElementButton("§bCustom Reason\n§7Type your own reason"));

        for (ReportReason rr : storage.getReasons()) {
            form.addButton(new ElementButton("§e" + rr.reason + "\n§7" + Storage.formatTime(rr.duration)));
        }

        form.addButton(new ElementButton("§cCancel"));
        reporter.showFormWindow(form);
    }

    // ========== FORM: Resolution Reason (after /report-end yes) ==========

    public void openResolutionForm(Player staff) {
        FormWindowSimple form = new FormWindowSimple("§aSelect Resolution", "§7Choose the confirmed reason:");
        for (ReportReason rr : storage.getReasons()) {
            form.addButton(new ElementButton("§e" + rr.reason + "\n§7" + Storage.formatTime(rr.duration)));
        }
        form.addButton(new ElementButton("§cCancel"));
        staff.showFormWindow(form);
    }

    // ========== INVESTIGATION LOGIC ==========

    public void startInvestigation(Player staff, int reportId) {
        ReportData rd = storage.getReport(reportId);
        if (rd == null) { staff.sendMessage("§cReport #" + reportId + " not found."); return; }
        if (rd.status != ReportStatus.OPEN) { staff.sendMessage("§cReport #" + reportId + " is already " + rd.status.name().toLowerCase() + "."); return; }

        if (!storage.claimReport(reportId, staff.getName())) { staff.sendMessage("§cCould not claim report."); return; }

        investigations.put(staff.getUniqueId(), reportId);

        // Vanish and spectate
        vanishManager.vanish(staff);

        // Teleport to reported player
        Player target = getServer().getPlayerExact(rd.target);
        if (target != null && target.isOnline()) {
            staff.teleportImmediate(Location.fromObject(target.getPosition()));
        } else {
            staff.sendMessage("§cTarget is offline. Teleported to spawn.");
            staff.teleportImmediate(Location.fromObject(getServer().getDefaultLevel().getSafeSpawn()));
        }

        // Show info
        String device = "Unknown";
        int os = 0;
        int ping = 0;
        if (target != null && target.isOnline()) {
            ping = target.getPing();
            os = target.getLoginChainData().getDeviceOS();
            device = Storage.deviceOsName(os) + " (" + target.getLoginChainData().getDeviceModel() + ")";
        }

        staff.sendMessage("§l§6=== Investigation #" + rd.id + " ===");
        staff.sendMessage("§7Player: §e" + rd.target);
        staff.sendMessage("§7World: §e" + rd.world);
        staff.sendMessage("§7Ping: §e" + ping + "ms");
        staff.sendMessage("§7Device: §e" + device);
        staff.sendMessage("§7Reason: §e" + rd.reason);
        staff.sendMessage("§7Reported By: §e" + rd.reporter);
        staff.sendMessage("§7Use §e/report-end <yes|no>§7 to conclude.");
    }

    public void endInvestigation(Player staff, boolean confirmed) {
        UUID uuid = staff.getUniqueId();
        if (!investigations.containsKey(uuid)) {
            staff.sendMessage("§cYou are not currently investigating any report.");
            return;
        }

        int reportId = investigations.get(uuid);

        // Teleport to lobby
        teleportToLobby(staff);

        // Unvanish
        vanishManager.unvanish(staff);

        if (confirmed) {
            // Open resolution form to choose the confirmed reason
            investigations.put(uuid, -reportId); // negative means waiting for resolution
            openResolutionForm(staff);
        } else {
            storage.resolveReport(reportId, false, "False Report", "", 0);
            investigations.remove(uuid);
            staff.sendMessage("§cReport #" + reportId + " marked as false report.");
        }
    }

    public void completeResolution(Player staff, String reason, long duration) {
        UUID uuid = staff.getUniqueId();
        Integer val = investigations.get(uuid);
        if (val == null || val >= 0) return;
        int reportId = -val;

        String punishment = duration > 0 ? "Ban " + Storage.formatTime(duration) : "Warning";
        storage.resolveReport(reportId, true, reason, punishment, duration);
        investigations.remove(uuid);
        staff.sendMessage("§aReport #" + reportId + " resolved: §e" + reason + " §a- " + punishment);

        DiscordWebhook discord = getDiscord();
        if (discord != null) {
            ReportData rd = storage.getReport(reportId);
            if (rd != null) discord.sendResolution(rd);
        }
    }

    private void teleportToLobby(Player player) {
        if (lobbyWorld != null && !lobbyWorld.isEmpty()) {
            var level = getServer().getLevelByName(lobbyWorld);
            if (level != null) {
                player.teleportImmediate(Location.fromObject(level.getSafeSpawn()));
                return;
            }
        }
        player.teleportImmediate(Location.fromObject(getServer().getDefaultLevel().getSafeSpawn()));
    }

    // ========== CONFIG ==========

    public void loadConfig() {
        config = new Config(new File(getDataFolder(), "config.yml"));
        String webhookUrl = config.getString("discord-webhook", "");
        if (!webhookUrl.isEmpty()) {
            discord = new DiscordWebhook(webhookUrl);
            getLogger().info("Discord webhook enabled");
        } else {
            discord = null;
        }
        lobbyWorld = config.getString("lobby-world", "");
    }

    // ========== UTILITY ==========

    public String getPlayerPrefix(Player player) {
        if (adapter == null) return "";
        try {
            CachedMetaData meta = adapter.getMetaData(player);
            String prefix = meta.getPrefix();
            if (prefix == null || prefix.isEmpty()) {
                var prefixes = meta.getPrefixes();
                if (!prefixes.isEmpty()) prefix = prefixes.get(prefixes.lastKey());
            }
            return prefix != null ? TextFormat.colorize(prefix) : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static long parseDuration(String input) {
        input = input.toLowerCase();
        long total = 0;
        StringBuilder num = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else {
                if (num.length() == 0) continue;
                long n = Long.parseLong(num.toString());
                num.setLength(0);
                switch (c) {
                    case 's': total += n; break;
                    case 'm': total += n * 60; break;
                    case 'h': total += n * 3600; break;
                    case 'd': total += n * 86400; break;
                    case 'w': total += n * 604800; break;
                }
            }
        }
        if (num.length() > 0) total += Long.parseLong(num.toString());
        return total;
    }

    public Storage getStorage() { return storage; }
    public DiscordWebhook getDiscord() { return discord; }
    public VanishManager getVanishManager() { return vanishManager; }
    public Map<UUID, Integer> getInvestigations() { return investigations; }

    public static ReportPlugin get() { return instance; }
}
