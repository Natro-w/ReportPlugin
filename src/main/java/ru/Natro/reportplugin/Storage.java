package ru.Natro.reportplugin;

import cn.nukkit.utils.Config;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class Storage {

    private final File dataDir;
    private Config reportConfig;
    private Config reasonConfig;
    private Config statsConfig;
    private int nextId;
    private final List<ReportData> reports = new ArrayList<>();
    private final List<ReportReason> reasons = new ArrayList<>();
    private final Map<String, StaffStatsData> stats = new HashMap<>();

    public Storage(File dataDir) {
        this.dataDir = dataDir;
        if (!dataDir.exists()) dataDir.mkdirs();
        loadAll();
    }

    public synchronized int createReport(String reporter, String target, String reason, String world) {
        int id = nextId++;
        ReportData rd = new ReportData(id, reporter, target, reason, world);
        reports.add(rd);
        saveReports();
        return id;
    }

    public synchronized ReportData getReport(int id) {
        return reports.stream().filter(r -> r.id == id).findFirst().orElse(null);
    }

    public synchronized List<ReportData> getReportsByStatus(ReportStatus status) {
        return reports.stream().filter(r -> r.status == status).collect(Collectors.toList());
    }

    public synchronized List<ReportData> getReportsByReporter(String player) {
        return reports.stream().filter(r -> r.reporter.equalsIgnoreCase(player)).collect(Collectors.toList());
    }

    public synchronized List<ReportData> getReportsByTarget(String player) {
        return reports.stream().filter(r -> r.target.equalsIgnoreCase(player)).collect(Collectors.toList());
    }

    public synchronized List<ReportData> getAllReports() {
        return new ArrayList<>(reports);
    }

    public synchronized List<ReportData> searchReports(String query) {
        String q = query.toLowerCase();
        return reports.stream().filter(r ->
            String.valueOf(r.id).equals(query) ||
            r.reporter.toLowerCase().contains(q) ||
            r.target.toLowerCase().contains(q) ||
            r.reason.toLowerCase().contains(q)
        ).collect(Collectors.toList());
    }

    public synchronized boolean claimReport(int id, String staffName) {
        ReportData rd = getReport(id);
        if (rd == null || rd.status != ReportStatus.OPEN) return false;
        rd.status = ReportStatus.CLAIMED;
        rd.handledBy = staffName;
        rd.startedAt = System.currentTimeMillis() / 1000;
        saveReports();
        return true;
    }

    public synchronized boolean resolveReport(int id, boolean confirmed, String result, String punishment, long duration) {
        ReportData rd = getReport(id);
        if (rd == null || rd.status != ReportStatus.CLAIMED) return false;
        rd.status = confirmed ? ReportStatus.RESOLVED : ReportStatus.REJECTED;
        rd.endedAt = System.currentTimeMillis() / 1000;
        rd.result = result;
        rd.punishment = punishment;
        rd.punishmentDuration = duration;
        saveReports();
        updateStaffStats(rd.handledBy, confirmed, rd.endedAt - rd.startedAt);
        return true;
    }

    public synchronized Map<String, Integer> getTopReportedPlayers(int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (ReportData rd : reports) {
            counts.merge(rd.target, 1, Integer::sum);
        }
        return sortByValueDesc(counts, limit);
    }

    public synchronized Map<String, Integer> getTopReporters(int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (ReportData rd : reports) {
            counts.merge(rd.reporter, 1, Integer::sum);
        }
        return sortByValueDesc(counts, limit);
    }

    public synchronized int getTotalReports() {
        return reports.size();
    }

    public synchronized int getOpenCount() {
        return (int) reports.stream().filter(r -> r.status == ReportStatus.OPEN).count();
    }

    public synchronized int getClaimedCount() {
        return (int) reports.stream().filter(r -> r.status == ReportStatus.CLAIMED).count();
    }

    public synchronized int getResolvedCount() {
        return (int) reports.stream().filter(r -> r.status == ReportStatus.RESOLVED).count();
    }

    public synchronized int getRejectedCount() {
        return (int) reports.stream().filter(r -> r.status == ReportStatus.REJECTED).count();
    }

    public synchronized List<ReportReason> getReasons() {
        return new ArrayList<>(reasons);
    }

    public synchronized ReportReason getReason(String reason) {
        return reasons.stream().filter(r -> r.reason.equalsIgnoreCase(reason)).findFirst().orElse(null);
    }

    public synchronized void addReason(String reason, long duration) {
        reasons.removeIf(r -> r.reason.equalsIgnoreCase(reason));
        reasons.add(new ReportReason(reason, duration));
        saveReasons();
    }

    public synchronized void removeReason(String reason) {
        reasons.removeIf(r -> r.reason.equalsIgnoreCase(reason));
        saveReasons();
    }

    public synchronized StaffStatsData getStaffStats(String uuid) {
        return stats.computeIfAbsent(uuid, StaffStatsData::new);
    }

    public synchronized List<StaffStatsData> getTopStaff(int limit) {
        return stats.values().stream()
            .sorted((a, b) -> Integer.compare(b.handled, a.handled))
            .limit(limit)
            .collect(Collectors.toList());
    }

    private synchronized void updateStaffStats(String staffName, boolean confirmed, long timeSpent) {
        String uuid = resolveStaffUuid(staffName);
        StaffStatsData ss = getStaffStats(uuid);
        ss.handled++;
        if (confirmed) ss.confirmed++;
        else ss.falseReports++;
        ss.totalTime += timeSpent;
        saveStats();
    }

    private String resolveStaffUuid(String name) {
        if (ReportPlugin.get().getServer().getOnlinePlayers().containsKey(name)) {
            return ReportPlugin.get().getServer().getOnlinePlayers().get(name).getUniqueId().toString();
        }
        return name;
    }

    @SuppressWarnings("unchecked")
    private void loadAll() {
        reportConfig = new Config(new File(dataDir, "reports.json"), Config.JSON);
        reasonConfig = new Config(new File(dataDir, "reasons.json"), Config.JSON);
        statsConfig = new Config(new File(dataDir, "stats.json"), Config.JSON);

        nextId = reportConfig.getInt("nextId", 1);

        List<Map<String, Object>> rawReports = (List<Map<String, Object>>) reportConfig.getList("reports", new ArrayList<>());
        for (Map<String, Object> m : rawReports) {
            ReportData rd = new ReportData();
            rd.id = ((Number) m.getOrDefault("id", 0)).intValue();
            rd.reporter = (String) m.getOrDefault("reporter", "");
            rd.target = (String) m.getOrDefault("target", "");
            rd.reason = (String) m.getOrDefault("reason", "");
            rd.status = ReportStatus.fromName((String) m.getOrDefault("status", "OPEN"));
            rd.timestamp = ((Number) m.getOrDefault("timestamp", 0L)).longValue();
            rd.world = (String) m.getOrDefault("world", "");
            rd.handledBy = (String) m.getOrDefault("handledBy", "");
            rd.startedAt = ((Number) m.getOrDefault("startedAt", 0L)).longValue();
            rd.endedAt = ((Number) m.getOrDefault("endedAt", 0L)).longValue();
            rd.result = (String) m.getOrDefault("result", "");
            rd.punishment = (String) m.getOrDefault("punishment", "");
            rd.punishmentDuration = ((Number) m.getOrDefault("punishmentDuration", 0L)).longValue();
            reports.add(rd);
        }

        List<Map<String, Object>> rawReasons = (List<Map<String, Object>>) reasonConfig.getList("reasons", new ArrayList<>());
        for (Map<String, Object> m : rawReasons) {
            String reason = (String) m.getOrDefault("reason", "");
            long duration = ((Number) m.getOrDefault("duration", 0L)).longValue();
            reasons.add(new ReportReason(reason, duration));
        }

        Map<String, Object> rawStats = (Map<String, Object>) statsConfig.get("stats");
        if (rawStats != null) {
            for (Map.Entry<String, Object> entry : rawStats.entrySet()) {
                Map<String, Object> m = (Map<String, Object>) entry.getValue();
                StaffStatsData ss = new StaffStatsData(entry.getKey());
                ss.handled = ((Number) m.getOrDefault("handled", 0)).intValue();
                ss.confirmed = ((Number) m.getOrDefault("confirmed", 0)).intValue();
                ss.falseReports = ((Number) m.getOrDefault("falseReports", 0)).intValue();
                ss.totalTime = ((Number) m.getOrDefault("totalTime", 0L)).longValue();
                stats.put(entry.getKey(), ss);
            }
        }
    }

    private synchronized void saveReports() {
        List<Map<String, Object>> rawReports = new ArrayList<>();
        for (ReportData rd : reports) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rd.id);
            m.put("reporter", rd.reporter);
            m.put("target", rd.target);
            m.put("reason", rd.reason);
            m.put("status", rd.status.name());
            m.put("timestamp", rd.timestamp);
            m.put("world", rd.world);
            m.put("handledBy", rd.handledBy);
            m.put("startedAt", rd.startedAt);
            m.put("endedAt", rd.endedAt);
            m.put("result", rd.result);
            m.put("punishment", rd.punishment);
            m.put("punishmentDuration", rd.punishmentDuration);
            rawReports.add(m);
        }
        reportConfig.set("nextId", nextId);
        reportConfig.set("reports", rawReports);
        reportConfig.save();
    }

    private synchronized void saveReasons() {
        List<Map<String, Object>> rawReasons = new ArrayList<>();
        for (ReportReason rr : reasons) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reason", rr.reason);
            m.put("duration", rr.duration);
            rawReasons.add(m);
        }
        reasonConfig.set("reasons", rawReasons);
        reasonConfig.save();
    }

    private synchronized void saveStats() {
        Map<String, Object> rawStats = new LinkedHashMap<>();
        for (Map.Entry<String, StaffStatsData> entry : stats.entrySet()) {
            StaffStatsData ss = entry.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("handled", ss.handled);
            m.put("confirmed", ss.confirmed);
            m.put("falseReports", ss.falseReports);
            m.put("totalTime", ss.totalTime);
            rawStats.put(entry.getKey(), m);
        }
        statsConfig.set("stats", rawStats);
        statsConfig.save();
    }

    private static Map<String, Integer> sortByValueDesc(Map<String, Integer> map, int limit) {
        return map.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    public static String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0) sb.append(mins).append("m ");
        if (secs > 0) sb.append(secs).append("s");
        return sb.toString().trim();
    }

    public static String formatTimestamp(long unix) {
        if (unix <= 0) return "N/A";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        return sdf.format(new java.util.Date(unix * 1000));
    }

    public static String deviceOsName(int os) {
        switch (os) {
            case 1: return "Android";
            case 2: return "iOS";
            case 3: return "macOS";
            case 4: return "FireOS";
            case 5: return "GearVR";
            case 6: return "HoloLens";
            case 7: return "Windows 10";
            case 8: return "Windows";
            case 9: return "Dedicated";
            case 10: return "PlayStation";
            case 11: return "Nintendo Switch";
            case 12: return "Xbox One";
            case 13: return "Windows Phone";
            default: return "Unknown";
        }
    }
}
