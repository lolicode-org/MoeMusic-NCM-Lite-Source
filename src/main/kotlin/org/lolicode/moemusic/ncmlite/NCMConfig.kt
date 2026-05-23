package org.lolicode.moemusic.ncmlite

import kotlinx.serialization.Serializable
import org.lolicode.ncmapilitekt.model.SongLevel

/**
 * Plugin configuration for the MoeMusic NCM source.
 *
 * The file is loaded from (and written back to) the plugin's own TOML config via
 * [org.lolicode.moemusic.api.PluginContext.loadConfig].
 *
 * @property playlistId       Netease playlist ID. When blank, [NCMSource.getAutoplayTracks]
 *                            returns an empty list.
 * @property addTrackIds      Native Netease song IDs to **add** to the auto-play list
 *                            regardless of the active mode.  Applied last.
 * @property removeTrackIds   Native Netease song IDs to **remove** from the auto-play list
 *                            regardless of the active mode.  Applied before [addTrackIds].
 * @property maxSoundQuality  Maximum audio quality level to request from the NCM API when
 *                            resolving a playback URL (default [SongLevel.ExHigh]).
 *                            Higher levels such as [SongLevel.Lossless] or [SongLevel.HiRes]
 *                            require a matching Netease VIP subscription.
 */
@Serializable
data class NCMConfig(
    val playlistId: String = "",
    val addTrackIds: List<String> = emptyList(),
    val removeTrackIds: List<String> = emptyList(),
    val maxSoundQuality: SongLevel = SongLevel.ExHigh,
)
