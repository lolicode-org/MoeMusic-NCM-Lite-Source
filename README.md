# MoeMusic 网易云音源插件

简体中文 | [English](./README_en.md)

本插件是为 [MoeMusic](https://github.com/lolicode-org/MoeMusic) 播放器/模组设计的网易云音乐音源扩展，支持在 MoeMusic 中解析、搜索并播放网易云音乐。

## 功能特性

- **直接播放**：支持通过网易云歌曲 ID 或分享链接（包括移动端短链接）直接加入播放队列。
- **互动选择**：支持解析歌单、专辑或电台链接，并在游戏聊天栏中列出所有曲目供玩家点击选择。
- **游戏内搜索**：支持在游戏内通过指令直接搜索网易云音乐的歌曲库。
- **自动播放同步**：支持将网易云音乐的公开歌单同步为服务端的自动播放队列，并支持针对个别歌曲进行追加或排除。
- **音质调节**：支持配置最大请求音质。

> [!IMPORTANT]
> 本插件不提供任何形式的账号登录功能，因此无法访问需要登录权限的内容（如私人歌单、VIP 专享曲目等）。所有功能均基于公开可访问的网易云音乐资源实现。

---

## 安装方法

本插件需要依赖 MoeMusic 模组运行。

1. 从 [GitHub Releases](https://github.com/lolicode-org/MoeMusic-NCM-Lite-Source/releases) 获取插件的 JAR 文件（例如 `moemusic-ncmlite-source-<version>-full.jar`）。
   - *若从源码编译*：在项目根目录下运行 `./gradlew build`。编译生成的 JAR 文件位于 `build/libs/` 目录下（请选择带有 `-full` 后缀的文件）。
2. 将 JAR 文件放入 Minecraft 服务端（单人模式下为客户端）的 `config/moemusic/plugins/` 目录中。
3. 启动或重启 Minecraft 服务器/客户端。

---

## 配置说明

插件首次加载成功后，会在以下路径自动生成配置文件：
`config/moemusic/plugin-configs/moemusic-ncmlite-source.toml`

### 配置项

| 配置键名 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `playlist_id` | 字符串 | `""` | 自动播放所使用的公开网易云歌单 ID。设置后，MoeMusic 将从此歌单拉取歌曲进行自动播放。 |
| `add_track_ids` | 字符串列表 | `[]` | 强制追加到自动播放列表中的网易云歌曲 ID 列表。 |
| `remove_track_ids` | 字符串列表 | `[]` | 强制从自动播放列表中排除的网易云歌曲 ID 列表。 |
| `max_sound_quality` | 字符串 | `"ExHigh"` | 允许请求的最大音质等级。详情见下文。 |

#### 音质可选等级
- `Standard`（标准音质）
- `Higher`（较高音质）
- `ExHigh`（极高音质 - 默认）

> [!NOTE]
> 因本插件不支持账号登录，因此无法访问需要登录权限的高音质资源。设置 `max_sound_quality` 仅限制插件请求的最高音质等级，但实际可用的音质可能会受到网易云音乐的访问限制影响。
> 
> 因版权限制，部分歌曲可能无法在不登录的情况下获取到`ExHigh`音质的资源。

### 示例配置

```toml
playlist_id = "17694522788"
add_track_ids = ["2097486090"]
remove_track_ids = ["114514"]
max_sound_quality = "ExHigh"
```

---

## 使用指南

安装并配置完成后，你可以通过 MoeMusic 内置的 `/music` 指令使用网易云音源：

### 1. 点歌与播放
- **通过歌曲 ID**：`/music 2097486090`
- **通过网页链接**：`/music https://music.163.com/#/song?id=2097486090`
- **通过移动端短链接**：`/music https://163cn.tv/xxxxx`

### 2. 解析歌单、专辑或电台
在游戏内输入以下链接，聊天栏会显示曲目列表供玩家选择：
- 歌单链接：`/music https://music.163.com/#/playlist?id=17694522788`
- 专辑链接：`/music https://music.163.com/#/album?id=358640968`
- 电台链接：`/music https://music.163.com/#/dj?id=114514`

### 3. 搜索歌曲
仅搜索网易云音乐音源：
- `/music search --source ncmlite <关键词>`

### 4. 重新加载自动播放
如果你更新了网易云上的歌单，或者修改了插件的配置文件，可以使用以下指令即时刷新：
- 刷新自动播放列表：`/music reload autoplay`
- 重新加载所有配置文件：`/music reload all`

---

## 开源许可证

本项目采用 GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later) 开源许可证。详情请参阅 [LICENSE](./LICENSE) 文件。
