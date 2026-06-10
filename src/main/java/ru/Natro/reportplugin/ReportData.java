package ru.Natro.reportplugin;

public class ReportData {
    public int id;
    public String reporter;
    public String target;
    public String reason;
    public String status; // OPEN, CLAIMED, RESOLVED, REJECTED
    public long timestamp;
    public String world;
    public String handledBy;
    public long startedAt;
    public long endedAt;
    public String result;
    public String punishment;
    public long punishmentDuration;

    public ReportData() {}

    public ReportData(int id, String reporter, String target, String reason, String world) {
        this.id = id;
        this.reporter = reporter;
        this.target = target;
        this.reason = reason;
        this.status = "OPEN";
        this.timestamp = System.currentTimeMillis() / 1000;
        this.world = world;
        this.handledBy = "";
        this.startedAt = 0;
        this.endedAt = 0;
        this.result = "";
        this.punishment = "";
        this.punishmentDuration = 0;
    }
}
