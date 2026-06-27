# Clog Hunter Exporter

A RuneLite plugin that exports your Old School RuneScape Collection Log progress to a local JSON file for use with Clog Hunter.

## Features

- Automatically records Collection Log pages as they are opened.
- Exports obtained item data to a local JSON file.
- Tracks pages already scanned.
- Displays pages that still need to be opened.
- Supports incremental scanning across multiple sessions.
- Includes a quick button to open the export directory.

## How To Use

1. Enable **Clog Hunter Exporter** in RuneLite.
2. Open the in-game Collection Log.
3. Click through each Collection Log page you want to capture.
4. The plugin automatically saves progress as you browse.
5. Import the generated JSON file into the Clog Hunter desktop application.

## Export File

The plugin creates:

```text
clog_hunter_export.json
```

The file is stored in RuneLite's local data directory and can be accessed through the **Open Export Folder** button in the plugin panel.

## Plugin Panel

The sidebar panel displays:

- Current account
- Recording status
- Last page scanned
- Total pages captured
- Last export time
- List of unscanned Collection Log pages

This allows you to quickly identify which pages still need to be opened.

## Important Notes

- Collection Log pages must be opened at least once before they can be exported.
- The plugin does **not** automate gameplay.
- The plugin does **not** perform clicks or interact with the game.
- All data is stored locally on your computer.
- No account information is transmitted externally.

## Intended Use

Clog Hunter Exporter is designed specifically for use with the **Clog Hunter** Collection Log tracking application.

## License

BSD 2-Clause License