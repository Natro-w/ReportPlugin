package ru.Natro.reportplugin;

public class ReportReason {
    public String reason;
    public long duration; // duration in seconds (0 = permanent)

    public ReportReason() {}

    public ReportReason(String reason, long duration) {
        this.reason = reason;
        this.duration = duration;
    }
}
