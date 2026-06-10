# ReportPlugin

Advanced report system for Lumi (Nukkit-MOT) Minecraft Bedrock servers. Features form-based reporting, Discord webhook integration, investigation workflow with spectator/vanish, staff statistics, and a full admin dashboard.

## Features

- **Form-based reporting** — players select the target and reason through intuitive forms
- **Predefined reasons** — configurable report reasons with ban durations (Kill Aura, Fly, etc.)
- **Custom reason support** — players can type their own reason
- **Report status workflow** — `OPEN` → `CLAIMED` → `RESOLVED` / `REJECTED`
- **Staff investigation** — `/report-start` puts staff in spectator + vanish, teleports to target
- **Staff vanish** — completely hidden from all players, spectator mode, not in player list
- **Resolution forms** — staff choose from predefined reasons and punishments
- **Discord webhook** — reports and resolutions are sent to a Discord channel
- **Reports dashboard** — `/reports` GUI with categories: Open, Claimed, Resolved, Rejected, Search
- **Search** — find reports by ID, player name, or reason
- **Staff statistics** — track handled, confirmed, false reports, and average investigation time
- **Admin dashboard** — total reports, top reported players, top reporters, top staff
- **LuckPerms integration** — player prefixes shown in the report form
- **JSON storage** — all data persisted in JSON files (no database setup required)
- **Auto-generated default reasons** — on first run if no reasons are configured

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/report` | `reportplugin.use` | Open the report form to select a player |
| `/reports` | `reportplugin.receive` | Open the reports dashboard GUI |
| `/report-start <id>` | `reportplugin.receive` | Claim a report, vanish, and teleport to the target |
| `/report-end <yes\|no>` | `reportplugin.receive` | End an investigation (yes = resolution form, no = false report) |
| `/report-list <player> <send\|receive>` | `reportplugin.receive` | List all reports by or against a player |
| `/report-stats` | `reportplugin.receive` | View your staff statistics |
| `/report-admin` | OP only | View the admin dashboard |
| `/report-reason add <name> <duration>` | OP only | Add a report reason with ban duration |
| `/report-reason remove <name>` | OP only | Remove a report reason |
| `/report-reason list` | OP only | List all configured reasons |

### Duration format for `/report-reason add`

Use shorthand: `7d`, `3h`, `30m`, `1w`, `0` = permanent

Examples:
- `/report-reason add "Kill Aura" 7d`
- `/report-reason add "Spam" 1h`
- `/report-reason add "Bug Abuse" 14d`

## Permissions

```yaml
permissions:
  reportplugin.use:
    default: true
    description: Allows using /report to report players
  reportplugin.receive:
    default: op
    description: Allows receiving report notifications and using staff commands
```

## Installation

1. Download the latest `ReportPlugin-x.x.x.jar` from the [Releases](https://github.com/Natro/ReportPlugin/releases) page
2. Place the JAR in your server's `plugins/` folder
3. Ensure [LuckPerms-Nukkit](https://luckperms.net/download) is installed in `plugins/`
4. Restart your server (or use a plugin manager to load)

## Configuration

After the first run, edit `plugins/ReportPlugin/config.yml`:

```yaml
# Discord Webhook URL for report notifications
# Leave empty to disable Discord integration
discord-webhook: "https://discord.com/api/webhooks/..."

# Lobby world name for teleporting staff after investigation
# Leave empty to use default world spawn
lobby-world: "lobby"
```

## Data Storage

All data is stored in `plugins/ReportPlugin/data/` as JSON files:

- **reports.json** — all reports with status, timestamps, investigation log
- **reasons.json** — predefined reasons with ban durations
- **stats.json** — staff statistics

## Building from Source

### Prerequisites

- Java 11+ (JDK)
- [Lumi server JAR](https://github.com/LuminiaDev/Lumi/releases) (`Lumi-1.6.0.jar` or later)
- [LuckPerms-Nukkit](https://luckperms.net/download) (`LuckPerms-Nukkit-5.5.55.jar` or later)

### Build with Maven

Update the `pom.xml` system paths to point to your local Lumi and LuckPerms JARs, then run:

```bash
mvn clean package
```

The compiled JAR will be in `target/ReportPlugin-x.x.x.jar`.

### Build without Maven (manual javac)

```bash
# Compile
javac -cp "path/to/Lumi-1.6.0.jar;path/to/LuckPerms-Nukkit-5.5.55.jar" `
  -d target/classes `
  src/main/java/ru/Natro/reportplugin/*.java

# Package JAR
cd target/classes
jar cf ../ReportPlugin-1.1.0.jar -C . .
jar uf ../ReportPlugin-1.1.0.jar -C ../../src/main/resources plugin.yml
jar uf ../ReportPlugin-1.1.0.jar -C ../../src/main/resources config.yml
```

## Project Structure

```
ReportPlugin/
├── .github/workflows/
│   └── maven.yml
├── src/main/
│   ├── java/ru/Natro/reportplugin/
│   │   ├── ReportPlugin.java      # Main plugin class & commands
│   │   ├── ReportListener.java    # Form response handling
│   │   ├── DiscordWebhook.java    # Discord webhook integration
│   │   ├── VanishManager.java     # Staff vanish/spectator logic
│   │   ├── Storage.java           # JSON persistence layer
│   │   ├── ReportData.java        # Report data model
│   │   ├── ReportReason.java      # Reason data model
│   │   └── StaffStatsData.java    # Staff stats data model
│   └── resources/
│       ├── plugin.yml
│       └── config.yml
├── pom.xml
├── README.md
├── LICENSE
└── .gitignore
```

## Report Status Flow

```
Player submits report
        │
        ▼
      OPEN ──────────────► /report-start <id>
        │                         │
        │                         ▼
        │                     CLAIMED
        │                         │
        │                    ┌────┴────┐
        │                    │         │
        │              /report-end  /report-end
        │                yes         no
        │                    │         │
        │                    ▼         ▼
        │               RESOLVED   REJECTED
        │
        └── Staff can view in /reports GUI
```

## Staff Investigation

When a staff member runs `/report-start <id>`:

1. Report status changes to **CLAIMED** (no other staff can claim it)
2. Staff enters **spectator mode** (fully vanished)
3. Staff is **teleported** to the reported player
4. Staff sees investigation info: world, ping, device, reason, reporter
5. Staff investigates and runs `/report-end yes` or `/report-end no`
6. If **yes**: staff is teleported to lobby, unvanished, opens **resolution form**
7. If **no**: report marked as **REJECTED** (false report)
8. Discord webhook (if configured) sends the resolution

## Discord Webhook Format

### New Report
```json
{
  "title": "New Report",
  "color": 15158332,
  "fields": [
    {"name": "Reporter", "value": "PlayerName", "inline": true},
    {"name": "Target", "value": "PlayerName", "inline": true},
    {"name": "Reason", "value": "Kill Aura"}
  ]
}
```

### Resolution
```json
{
  "title": "Report #42 RESOLVED",
  "color": 65280,
  "fields": [
    {"name": "Reporter", "value": "...", "inline": true},
    {"name": "Target", "value": "...", "inline": true},
    {"name": "Reason", "value": "...", "inline": true},
    {"name": "Handled By", "value": "...", "inline": true},
    {"name": "Result", "value": "...", "inline": true},
    {"name": "Punishment", "value": "Ban 7d"}
  ]
}
```

## License

[MIT](LICENSE)
