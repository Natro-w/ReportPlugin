package ru.Natro.reportplugin;

public enum ReportStatus {
    OPEN("§e"),
    CLAIMED("§b"),
    RESOLVED("§a"),
    REJECTED("§c");

    private final String color;

    ReportStatus(String color) {
        this.color = color;
    }

    public String getColor() {
        return color;
    }

    public String getDisplayName() {
        String name = name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    public String toColoredDisplay() {
        return color + getDisplayName();
    }

    public static ReportStatus fromName(String name) {
        if (name == null) return OPEN;
        for (ReportStatus s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        return OPEN;
    }
}
