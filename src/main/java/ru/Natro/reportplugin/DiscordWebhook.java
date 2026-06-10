package ru.Natro.reportplugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {

    private final String url;

    public DiscordWebhook(String url) {
        this.url = url;
    }

    public void send(String reporter, String target, String reason) {
        if (url == null || url.isEmpty()) return;

        String json = "{\"embeds\":[{" +
            "\"title\":\"New Report\"," +
            "\"color\":15158332," +
            "\"fields\":[" +
            "{\"name\":\"Reporter\",\"value\":\"" + escape(reporter) + "\",\"inline\":true}," +
            "{\"name\":\"Target\",\"value\":\"" + escape(target) + "\",\"inline\":true}," +
            "{\"name\":\"Reason\",\"value\":\"" + escape(reason) + "\"}" +
            "]," +
            "\"footer\":{\"text\":\"Lumi Report System\"}," +
            "\"timestamp\":\"" + java.time.Instant.now().toString() + "\"" +
            "}]}";

        post(json);
    }

    public void sendResolution(ReportData rd) {
        if (url == null || url.isEmpty()) return;

        String color = rd.status.equals("RESOLVED") ? "65280" : "16711680";
        String json = "{\"embeds\":[{" +
            "\"title\":\"Report #" + rd.id + " " + rd.status + "\"," +
            "\"color\":" + color + "," +
            "\"fields\":[" +
            "{\"name\":\"Reporter\",\"value\":\"" + escape(rd.reporter) + "\",\"inline\":true}," +
            "{\"name\":\"Target\",\"value\":\"" + escape(rd.target) + "\",\"inline\":true}," +
            "{\"name\":\"Reason\",\"value\":\"" + escape(rd.reason) + "\",\"inline\":true}," +
            "{\"name\":\"Handled By\",\"value\":\"" + escape(rd.handledBy) + "\",\"inline\":true}," +
            "{\"name\":\"Result\",\"value\":\"" + escape(rd.result) + "\",\"inline\":true}," +
            "{\"name\":\"Punishment\",\"value\":\"" + escape(rd.punishment) + "\",\"inline\":true}" +
            "]," +
            "\"footer\":{\"text\":\"Lumi Report System\"}," +
            "\"timestamp\":\"" + java.time.Instant.now().toString() + "\"" +
            "}]}";

        post(json);
    }

    private void post(String json) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "LumiReportPlugin/1.0");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code < 200 || code > 299) {
                ReportPlugin.get().getLogger().warning("Discord webhook returned " + code);
            }
        } catch (Exception e) {
            ReportPlugin.get().getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
