# MoeMusic NetEase Cloud Music Plugin

[简体中文](./README.md) | English

This plugin is a music source extension for [MoeMusic](https://github.com/lolicode-org/MoeMusic), allowing servers and players to parse, search, and play NetEase Cloud Music tracks in MoeMusic.

## Features

- **Direct Playback**: Queue songs using NetEase Cloud Music song IDs or sharing links (including mobile short links).
- **Interactive Choices**: Paste URLs of playlists, albums, or DJ radio channels to display a list of tracks in chat for players to choose from.
- **In-Game Search**: Search the NetEase library directly using the game chat interface.
- **Autoplay Syncing**: Synchronize a public NetEase playlist to the server's autoplay queue, with options to force-add or exclude specific track IDs.
- **Adjustable Quality**: Request standard, high, or lossless audio streams depending on your preference and NetEase VIP status.

> [!IMPORTANT]
> This plugin does not provide any account login functionality, so it cannot access content that requires login (such as private playlists, VIP-exclusive tracks, etc.). All features are based on publicly accessible NetEase Cloud Music resources.

---

## Installation

This plugin requires the MoeMusic mod to be installed on the Minecraft server (or client for singleplayer).

1. Obtain the plugin JAR file from the [GitHub Releases](https://github.com/lolicode-org/MoeMusic-NCM-Lite-Source/releases) (e.g., `moemusic-ncmlite-source-<version>-full.jar`).
   - *If building from source*: Run `./gradlew build` in the project root. The resulting JAR file will be located in the `build/libs/` directory (make sure to choose the one with the `-full` suffix).
2. Place the JAR file into the `config/moemusic/plugins/` directory of your Minecraft server or client instance.
3. Start or restart the Minecraft server/client.

---

## Configuration

After the first startup with the plugin installed, a configuration file is automatically generated at:
`config/moemusic/plugin-configs/moemusic-ncmlite-source.toml`

### Configuration Options

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `playlist_id` | String | `""` | The ID of a public NetEase Cloud Music playlist. When set, MoeMusic will pull songs from this playlist for the autoplay queue. |
| `add_track_ids` | List of Strings | `[]` | NetEase song IDs to manually append to the autoplay list. |
| `remove_track_ids` | List of Strings | `[]` | NetEase song IDs to exclude/remove from the autoplay list. |
| `max_sound_quality` | String | `"ExHigh"` | The maximum audio quality level requested. See details below. |

#### Sound Quality Levels
- `Standard` (Standard Quality)
- `Higher` (High Quality)
- `ExHigh` (Extreme Quality - Default)

> [!NOTE]
> Since this plugin does not support account login, it cannot access high-quality resources that require login. Setting `max_sound_quality` only limits the maximum quality level the plugin will request, but the actual available quality may be affected by NetEase's access restrictions.
> 
> Due to copyright restrictions, some songs may not be available in `ExHigh` quality without logging in.

### Example Configuration

```toml
playlist_id = "17694522788"
add_track_ids = ["2097486090"]
remove_track_ids = ["114514"]
max_sound_quality = "ExHigh"
```

---

## Usage

Once installed and configured, you can interact with NetEase Cloud Music using the standard MoeMusic command system:

### 1. Queue Songs
- **Via Song ID**: `/music 2097486090`
- **Via Share Link**: `/music https://music.163.com/#/song?id=2097486090`
- **Via Short Link**: `/music https://163cn.tv/xxxxx`

### 2. Browse Playlists, Albums, or DJ Radios
Paste the URL of a playlist, album, or DJ channel to display choices in chat:
- `/music https://music.163.com/#/playlist?id=17694522788`
- `/music https://music.163.com/#/album?id=358640968`
- `/music https://music.163.com/#/dj?id=114514`

### 3. Search Songs
Search the NetEase Cloud Music library:
- `/music search --source ncmlite <query>`

### 4. Refresh Autoplay
If you updated the playlist on NetEase or modified the plugin configuration, refresh the autoplay queue without restarting:
- `/music reload autoplay`
- `/music reload all`

---

## License

This project is licensed under the GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later). See the [LICENSE](./LICENSE) file for details.
