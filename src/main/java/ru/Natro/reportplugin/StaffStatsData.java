package ru.Natro.reportplugin;

public class StaffStatsData {
    public String uuid;
    public int handled;
    public int confirmed;
    public int falseReports;
    public long totalTime; // total investigation time in seconds

    public StaffStatsData() {}

    public StaffStatsData(String uuid) {
        this.uuid = uuid;
        this.handled = 0;
        this.confirmed = 0;
        this.falseReports = 0;
        this.totalTime = 0;
    }
}
