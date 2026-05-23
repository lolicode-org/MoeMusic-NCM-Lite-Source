package org.lolicode.moemusic.ncmlite

import org.lolicode.moemusic.api.LocalizedText
import org.lolicode.moemusic.api.plugin.*
import org.lolicode.ncmapilitekt.NeteaseClient
import org.lolicode.ncmapilitekt.model.SongLevel

object NCMPlugin : Plugin {

    const val MOD_ID = "moemusic-ncmlite-source"

    // -------------------------------------------------------------------------
    // MoeMusic Plugin identity
    // -------------------------------------------------------------------------

    override val id: String = MOD_ID
    override val displayName: LocalizedText = LocalizedText.key("plugin.moemusic.ncmlite")
    override val configSpec: PluginConfigSpec<NCMConfig> = pluginConfigSpec(::NCMConfig) {
        string(
            "playlist_id",
            { it.playlistId },
            updater = { config, value -> config.copy(playlistId = value) },
            validator = { candidate, value ->
                when {
                    value.isNotBlank() && value.any { !it.isDigit() } ->
                        LocalizedText.key("config.moemusic.ncmlite.source.validation.playlist_numeric")
                    else -> null
                }
            },
        )
        stringList(
            "add_track_ids",
            { it.addTrackIds },
            updater = { config, value -> config.copy(addTrackIds = value) },
            validator = { _, value -> validateTrackIds(value, "config.moemusic.ncmlite.source.add_track_ids") },
        )
        stringList(
            "remove_track_ids",
            { it.removeTrackIds },
            updater = { config, value -> config.copy(removeTrackIds = value) },
            validator = { _, value -> validateTrackIds(value, "config.moemusic.ncmlite.source.remove_track_ids") },
        )
        enumSelector<SongLevel>(
            "max_sound_quality",
            { it.maxSoundQuality },
            updater = { config, value -> config.copy(maxSoundQuality = value) },
        )
    }
    override val version: String = "1.0.0"
    override val supportedApiVersions: String = ">=1.0.0 <2.0.0"

    // -------------------------------------------------------------------------
    // MoeMusic Plugin lifecycle
    // -------------------------------------------------------------------------

    override fun onServerRuntimeLoad(ctx: ServerRuntimeContext) {
        val config = ctx.loadConfig(configSpec)
        val source = NCMSource(createClient(), config)
        ctx.registerMusicSource(source)
        ctx.onConfigChanged(configSpec) { updatedConfig ->
            source.update(createClient(), updatedConfig)
            ctx.logger.info("NCM config updated (playlistId={})", updatedConfig.playlistId.ifBlank { "<disabled>" })
        }
        ctx.logger.info("NCMSource registered (source id='{}', playlistId={})", NCMSource.SOURCE_ID, config.playlistId.ifBlank { "<disabled>" })
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createClient(): NeteaseClient = NeteaseClient()

    private fun validateTrackIds(ids: List<String>, labelKey: String): LocalizedText? {
        val invalid = ids.firstOrNull { it.isNotBlank() && it.any { ch -> !ch.isDigit() } }
        return if (invalid != null) {
            LocalizedText.key("config.moemusic.ncmlite.source.validation.track_ids_numeric", LocalizedText.key(labelKey))
        } else {
            null
        }
    }
}
