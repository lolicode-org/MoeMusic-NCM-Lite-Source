package org.lolicode.moemusic.ncmlite

import kotlinx.coroutines.delay
import org.lolicode.moemusic.api.*
import org.lolicode.moemusic.api.model.*
import org.lolicode.moemusic.api.model.SearchResult
import org.lolicode.ncmapilitekt.NeteaseClient
import org.lolicode.ncmapilitekt.api.*
import org.lolicode.ncmapilitekt.model.*
import org.slf4j.LoggerFactory
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpTimeoutException
import javax.net.ssl.SSLException
import kotlin.time.Duration.Companion.milliseconds

internal sealed interface NCMTrackKey {
    data class Song(val songId: String) : NCMTrackKey
    data class DjProgram(val programId: String, val mainSongId: String?) : NCMTrackKey
}

private const val SONG_TRACK_PREFIX = "song:"
private const val SONG_TRACK_KIND = "song"
private const val DJ_PROGRAM_TRACK_PREFIX = "dj-program:"
private val HTTP_URL_PATTERN = Regex(
    """https?://[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]+""",
    RegexOption.IGNORE_CASE,
)
private val NETEASE_HOSTS = setOf("music.163.com", "y.music.163.com", "163cn.tv", "163.fm")

internal fun parseNcmNumericId(identifier: String): String? =
    identifier.takeIf { it.isNotBlank() && it.all(Char::isDigit) }

internal fun ncmSongTrackId(songId: String): String = "$SONG_TRACK_PREFIX$songId"

internal fun ncmDjProgramTrackId(programId: String, mainSongId: String): String =
    "$DJ_PROGRAM_TRACK_PREFIX$programId:$SONG_TRACK_PREFIX$mainSongId"

internal fun parseNcmTrackKey(identifier: String): NCMTrackKey? {
    val input = identifier.trim()
    parseNcmNumericId(input)?.let { return NCMTrackKey.Song(it) }

    if (input.startsWith(SONG_TRACK_PREFIX)) {
        return parseNcmNumericId(input.removePrefix(SONG_TRACK_PREFIX))?.let(NCMTrackKey::Song)
    }

    if (input.startsWith(DJ_PROGRAM_TRACK_PREFIX)) {
        val suffix = input.removePrefix(DJ_PROGRAM_TRACK_PREFIX)
        val parts = suffix.split(":")
        return when (parts.size) {
            1 -> parseNcmNumericId(parts[0])
                ?.let { NCMTrackKey.DjProgram(programId = it, mainSongId = null) }
            3 if parts[1] == SONG_TRACK_KIND -> {
                val programId = parseNcmNumericId(parts[0]) ?: return null
                val mainSongId = parseNcmNumericId(parts[2]) ?: return null
                NCMTrackKey.DjProgram(programId = programId, mainSongId = mainSongId)
            }
            else -> null
        }
    }

    return null
}

internal fun extractNeteaseUrl(identifier: String): String? =
    HTTP_URL_PATTERN.findAll(identifier)
        .map { it.value.trimEnd(*URL_TRAILING_PUNCTUATION) }
        .firstOrNull(::isSupportedNeteaseUrl)

internal fun isSupportedNeteaseUrl(identifier: String): Boolean {
    val uri = runCatching { URI(identifier) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme != "http" && scheme != "https") return false
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
    return host in NETEASE_HOSTS
}

internal fun isNeteaseDirectMediaUrl(identifier: String): Boolean {
    val uri = runCatching { URI(identifier) }.getOrNull() ?: return false
    return isSupportedNeteaseUrl(identifier) && uri.path.orEmpty().endsWith("/song/media/outer/url")
}

private val URL_TRAILING_PUNCTUATION = charArrayOf(
    '.', ',', ';', ':', '!', '?',
    ')', ']', '}',
    '\'', '"',
)

private fun unavailableReason(removed: Boolean, regionBlocked: Boolean, vipRequired: Boolean): LocalizedText? = when {
    removed -> LocalizedText.key("error.moemusic.ncmlite.track.removed")
    regionBlocked -> LocalizedText.key("error.moemusic.ncmlite.track.region_blocked")
    vipRequired -> LocalizedText.key("error.moemusic.ncmlite.track.vip_only")
    else -> null
}

private fun Artist.toNcmArtistInfo(): ArtistInfo? {
    val displayName = name?.takeIf(String::isNotBlank)
    val effectiveId = id?.takeIf(String::isNotBlank) ?: displayName ?: return null
    return ArtistInfo(
        id = effectiveId,
        name = displayName ?: effectiveId,
    )
}

private fun List<Artist>?.toNcmArtistInfos(): List<ArtistInfo> =
    this?.mapNotNull { artist -> artist.toNcmArtistInfo() } ?: emptyList()

private fun UserInfo.toNcmArtistInfo(): ArtistInfo? {
    val displayName = nickname?.takeIf(String::isNotBlank)
    val effectiveId = userId?.takeIf(String::isNotBlank) ?: displayName ?: return null
    return ArtistInfo(
        id = effectiveId,
        name = displayName ?: effectiveId,
    )
}

internal fun Song.toNcmTrackInfo(): TrackInfo {
    val songId = parseNcmNumericId(id.orEmpty()).orEmpty()
    return TrackInfo(
        id = songId.takeIf(String::isNotBlank)?.let(::ncmSongTrackId).orEmpty(),
        title = name.orEmpty(),
        artists = artists.toNcmArtistInfos(),
        durationMs = duration,
        coverUrl = album?.picUrl?.takeIf(String::isNotBlank),
        sourceId = NCMSource.SOURCE_ID,
        album = album?.name?.takeIf(String::isNotBlank),
        unavailableReason = unavailableReason(
            removed = (privilege?.st ?: 0) < 0,
            regionBlocked = privilege?.toast == true,
            vipRequired = (privilege?.fee ?: 0) !in setOf(0, 8) && (privilege?.payed ?: 0) == 0,
        ),
    )
}

internal fun DjProgram.toNcmTrackInfo(): TrackInfo? {
    val programId = parseNcmNumericId(id.orEmpty()) ?: return null
    val song = mainSong ?: return null
    val mainSongId = parseNcmNumericId(song.id.orEmpty()) ?: return null
    return TrackInfo(
        id = ncmDjProgramTrackId(programId, mainSongId),
        title = name?.takeIf(String::isNotBlank) ?: song.name.orEmpty(),
        artists = song.artists.toNcmArtistInfos().ifEmpty {
            dj?.toNcmArtistInfo()?.let(::listOf) ?: emptyList()
        },
        durationMs = song.duration.takeIf { it > 0 } ?: duration,
        coverUrl = coverUrl?.takeIf(String::isNotBlank)
            ?: picUrl?.takeIf(String::isNotBlank)
            ?: song.album?.picUrl?.takeIf(String::isNotBlank)
            ?: channel?.coverUrl?.takeIf(String::isNotBlank),
        sourceId = NCMSource.SOURCE_ID,
        album = channel?.name?.takeIf(String::isNotBlank) ?: song.album?.name?.takeIf(String::isNotBlank),
        lyricsFetched = true,
    )
}

internal fun TrackInfo.toNcmSelectionEntry(): SelectionEntry = SelectionEntry(
    selectionId = id,
    title = title,
    artists = artists,
    durationMs = durationMs,
    sourceId = sourceId,
    album = album,
    unavailableReason = unavailableReason,
    kind = SelectionEntryKind.TRACK,
)

private fun TrackInfo.ncmSongIdOrNull(): String? =
    (parseNcmTrackKey(id) as? NCMTrackKey.Song)?.songId

private fun TrackInfo.hasUsableNcmMetadata(): Boolean =
    id.isNotBlank() && title.isNotBlank()

/**
 * MoeMusic [org.lolicode.moemusic.api.MusicSource] implementation for NCM.
 */
class NCMSource(client: NeteaseClient, config: NCMConfig = NCMConfig()) : IdentifierResolvableMusicSource, SearchableMusicSource {

    private data class RuntimeState(
        val client: NeteaseClient,
        val config: NCMConfig,
    )

    @Volatile
    private var state: RuntimeState = RuntimeState(client, config)

    companion object {
        const val SOURCE_ID = "ncmlite"
        private const val SEARCH_LIMIT = 20
        private const val SONG_DETAIL_BATCH_SIZE = 500
        private const val UPSTREAM_RETRY_ATTEMPTS = 3
        private const val UPSTREAM_RETRY_DELAY_MS = 250L
        private val logger = LoggerFactory.getLogger(NCMSource::class.java)

        private fun invalidSongIdMessage(): LocalizedText =
            LocalizedText.key("error.moemusic.ncmlite.identifier.invalid_song_id")

        private fun programMissingSongMessage(): LocalizedText =
            LocalizedText.key("error.moemusic.ncmlite.identifier.program_missing_song")

        private fun playlistEmptyMessage(): LocalizedText =
            LocalizedText.key("error.moemusic.ncmlite.identifier.playlist_empty")

        private fun albumEmptyMessage(): LocalizedText =
            LocalizedText.key("error.moemusic.ncmlite.identifier.album_empty")

        private fun channelEmptyMessage(): LocalizedText =
            LocalizedText.key("error.moemusic.ncmlite.identifier.channel_empty")

        private fun unsupportedLinkMessage(): LocalizedText =
            LocalizedText.key("error.moemusic.ncmlite.identifier.unsupported_link")
    }

    override val id: String = SOURCE_ID
    override val displayName: LocalizedText = LocalizedText.key("source.moemusic.ncmlite")

    fun update(client: NeteaseClient, config: NCMConfig) {
        state = RuntimeState(client, config)
    }

    override suspend fun resolveIdentifier(identifier: String, submitter: MoeMusicUser?): IdentifierResolutionResult {
        val input = identifier.trim()
        val currentState = state
        val client = currentState.client

        parseNcmNumericId(input)?.let { songId ->
            return when (val result = fetchTrackInfoBySongId(client, songId)) {
                is UserResult.Success -> result.value
                    ?.takeIf(TrackInfo::hasUsableNcmMetadata)
                    ?.let(IdentifierResolutionResult::Resolved)
                    ?: IdentifierResolutionResult.Pass
                is UserResult.Error -> IdentifierResolutionResult.Pass
            }
        }

        parseNcmTrackKey(input)?.let { key ->
            return when (val result = fetchTrackInfoByKey(client, key)) {
                is UserResult.Success -> result.value?.let(IdentifierResolutionResult::Resolved)
                    ?: IdentifierResolutionResult.Blocked(
                        LocalizedText.key(
                            "error.moemusic.ncmlite.identifier.track_not_found",
                            when (key) {
                                is NCMTrackKey.Song -> key.songId
                                is NCMTrackKey.DjProgram -> key.programId
                            },
                        )
                    )
                is UserResult.Error -> IdentifierResolutionResult.Blocked(result.message)
            }
        }

        val url = extractNeteaseUrl(input) ?: return IdentifierResolutionResult.Pass
        val directMediaUrl = isNeteaseDirectMediaUrl(url)

        return when (val result = callUpstream("resolve identifier", client::resolveNeteaseUrl, url)) {
            is ApiResult.Success -> when (val resolved = result.data) {
                is NeteaseUrlResult.SongResult -> {
                    val track = resolved.song.toNcmTrackInfo()
                    if (track.id.isBlank()) {
                        if (directMediaUrl) {
                            IdentifierResolutionResult.Pass
                        } else {
                            IdentifierResolutionResult.Blocked(invalidSongIdMessage())
                        }
                    } else if (directMediaUrl && !track.hasUsableNcmMetadata()) {
                        IdentifierResolutionResult.Pass
                    } else {
                        IdentifierResolutionResult.Resolved(track)
                    }
                }
                is NeteaseUrlResult.DjProgramResult -> {
                    resolved.program.toNcmTrackInfo()?.let(IdentifierResolutionResult::Resolved)
                        ?: IdentifierResolutionResult.Blocked(programMissingSongMessage())
                }
                is NeteaseUrlResult.PlaylistResult -> {
                    val trackIds = resolved.playlist.trackIds?.mapNotNull { it.id } ?: emptyList()
                    val entries = fetchSongsByIds(currentState, trackIds)
                        .map { it.toNcmSelectionEntry() }
                    if (entries.isEmpty()) {
                        IdentifierResolutionResult.Blocked(playlistEmptyMessage())
                    } else {
                        IdentifierResolutionResult.Choices(entries)
                    }
                }
                is NeteaseUrlResult.AlbumResult -> {
                    val entries = resolved.songs
                        .map { it.toNcmTrackInfo() }
                        .filter { it.id.isNotBlank() }
                        .map { it.toNcmSelectionEntry() }
                    if (entries.isEmpty()) {
                        IdentifierResolutionResult.Blocked(albumEmptyMessage())
                    } else {
                        IdentifierResolutionResult.Choices(entries)
                    }
                }
                is NeteaseUrlResult.DjChannelResult -> {
                    val entries = fetchChannelPrograms(currentState, resolved.channel)
                        .mapNotNull { it.toNcmTrackInfo() }
                        .map { it.toNcmSelectionEntry() }
                    if (entries.isEmpty()) {
                        IdentifierResolutionResult.Blocked(channelEmptyMessage())
                    } else {
                        IdentifierResolutionResult.Choices(entries)
                    }
                }
                is NeteaseUrlResult.Unknown ->
                    if (directMediaUrl) IdentifierResolutionResult.Pass else IdentifierResolutionResult.Blocked(unsupportedLinkMessage())
            }
            is ApiResult.Error ->
                if (directMediaUrl) IdentifierResolutionResult.Pass else IdentifierResolutionResult.Blocked(apiErrorToText(result))
        }
    }

    override suspend fun search(query: SearchQuery, submitter: MoeMusicUser?): UserResult<SearchResult> {
        val client = state.client
        return when (
            val result = callUpstream(
                action = "search",
                call = { text, limit, offset -> client.searchSongs(text, limit = limit, offset = offset) },
                query.query,
                query.limit.takeIf { it > 0 } ?: SEARCH_LIMIT,
                query.offset,
            )
        ) {
            is ApiResult.Success -> UserResult.Success(
                SearchResult(
                    entries = result.data.items.map { it.toNcmTrackInfo().toNcmSelectionEntry() },
                    sourceId = SOURCE_ID,
                    total = result.data.totalCount,
                )
            )
            is ApiResult.Error -> {
                logger.warn("NCM search failed: {}", result.message)
                UserResult.Error(apiErrorToText(result))
            }
        }
    }

    override suspend fun resolve(track: TrackInfo, submitter: MoeMusicUser?): PlaybackResource {
        val currentState = state
        val client = currentState.client
        val key = parseNcmTrackKey(track.id) ?: throw SourceFormatException()
        val playbackSongId = resolvePlaybackSongId(client, key)
        val result = callUpstream("resolve playback url", client::getSongUrl, listOf(playbackSongId), currentState.config.maxSoundQuality)

        return when (result) {
            is ApiResult.Success -> {
                val urlInfo = result.data.firstOrNull()
                    ?: throw SourceException(LocalizedText.key("error.moemusic.ncmlite.bad_response"))
                if (urlInfo.freeTrialInfo != null) {
                    throw TrackUnavailableException(LocalizedText.key("error.moemusic.ncmlite.resolve.vip_required"))
                }
                val url = urlInfo.url
                if (url.isNullOrBlank()) {
                    throw TrackUnavailableException(
                        track.unavailableReason?.let { track.unavailabilityMessage() }
                            ?: LocalizedText.key("error.moemusic.ncmlite.resolve.no_stream_url")
                    )
                }
                PlaybackResource(url)
            }
            is ApiResult.Error -> throw apiErrorToException(result)
        }
    }

    override suspend fun getTrackInfo(trackId: String, submitter: MoeMusicUser?): UserResult<TrackInfo?> {
        val client = state.client
        val key = parseNcmTrackKey(trackId) ?: return UserResult.Error(invalidSongIdMessage())
        return when (val result = fetchTrackInfoByKey(client, key)) {
            is UserResult.Success -> UserResult.Success(
                result.value?.let { track ->
                    when (key) {
                        is NCMTrackKey.Song -> enrichLyrics(client, key.songId, track)
                        is NCMTrackKey.DjProgram -> track
                    }
                }
            )
            is UserResult.Error -> result
        }
    }

    override suspend fun getAutoplayTracks(): List<TrackInfo> {
        val currentState = state
        val base = if (currentState.config.playlistId.isBlank()) emptyList() else fetchFixedPlaylist(currentState)
        return applyOverrides(currentState, base)
    }

    private suspend fun fetchTrackInfoByKey(client: NeteaseClient, key: NCMTrackKey): UserResult<TrackInfo?> =
        when (key) {
            is NCMTrackKey.Song -> fetchTrackInfoBySongId(client, key.songId)
            is NCMTrackKey.DjProgram -> fetchTrackInfoByDjProgramId(client, key.programId)
        }

    private suspend fun resolvePlaybackSongId(client: NeteaseClient, key: NCMTrackKey): String =
        when (key) {
            is NCMTrackKey.Song -> key.songId
            is NCMTrackKey.DjProgram -> key.mainSongId ?: fetchMainSongIdByDjProgramId(client, key.programId)
        }

    private suspend fun fetchMainSongIdByDjProgramId(client: NeteaseClient, programId: String): String =
        when (val result = callUpstream("get dj program detail", client::getDjProgramDetail, programId)) {
            is ApiResult.Success -> result.data
                ?.mainSong
                ?.id
                ?.let(::parseNcmNumericId)
                ?: throw TrackUnavailableException(programMissingSongMessage())
            is ApiResult.Error -> throw apiErrorToException(result)
        }

    private suspend fun fetchTrackInfoBySongId(client: NeteaseClient, songId: String): UserResult<TrackInfo?> =
        when (val result = callUpstream("get song detail", client::getSongDetail, listOf(songId))) {
            is ApiResult.Success -> {
                val track = result.data.firstOrNull()?.toNcmTrackInfo()?.takeIf { it.id.isNotBlank() }
                UserResult.Success(track)
            }
            is ApiResult.Error -> {
                logger.warn("NCM getSongDetail failed for {}: {}", songId, result.message)
                UserResult.Error(apiErrorToText(result))
            }
        }

    private suspend fun fetchTrackInfoByDjProgramId(client: NeteaseClient, programId: String): UserResult<TrackInfo?> =
        when (val result = callUpstream("get dj program detail", client::getDjProgramDetail, programId)) {
            is ApiResult.Success -> result.data
                ?.toNcmTrackInfo()
                ?.let { UserResult.Success(it) }
                ?: UserResult.Success(null)
            is ApiResult.Error -> {
                logger.warn("NCM getDjProgram failed for {}: {}", programId, result.message)
                UserResult.Error(apiErrorToText(result))
            }
        }

    private suspend fun enrichLyrics(client: NeteaseClient, songId: String, track: TrackInfo): TrackInfo {
        return when (val result = callUpstream("get song lyric", client::getSongLyric, songId)) {
            is ApiResult.Success -> track.copy(
                lyricLrc = result.data.lrc?.lyric?.takeIf { it.isNotBlank() },
                secondaryLyricLrc = result.data.tlyric?.lyric?.takeIf { it.isNotBlank() }
                    ?: result.data.romalrc?.lyric?.takeIf { it.isNotBlank() },
                lyricsFetched = true,
            )
            is ApiResult.Error -> {
                logger.warn("NCM getSongLyric failed for {}: {}", songId, result.message)
                track.copy(lyricsFetched = true)
            }
        }
    }

    private suspend fun fetchFixedPlaylist(state: RuntimeState): List<TrackInfo> {
        val config = state.config
        if (config.playlistId.isBlank()) {
            return emptyList()
        }
        val trackIds = when (val result = callUpstream("get playlist tracks", state.client::getPlaylistTracks, config.playlistId)) {
            is ApiResult.Success -> result.data.trackIds?.mapNotNull { it.id } ?: emptyList()
            is ApiResult.Error -> {
                logger.warn("NCM getPlaylistTracks failed: {}", result.message)
                return emptyList()
            }
        }
        return fetchSongsByIds(state, trackIds).filter(TrackInfo::isAvailable)
    }

    private suspend fun fetchSongsByIds(state: RuntimeState, ids: List<String>): List<TrackInfo> {
        if (ids.isEmpty()) return emptyList()
        val tracksById = LinkedHashMap<String, TrackInfo>()
        ids.chunked(SONG_DETAIL_BATCH_SIZE).forEach { chunk ->
            when (val result = callUpstream("get song detail batch", state.client::getSongDetail, chunk)) {
                is ApiResult.Success -> result.data.forEach { song ->
                    val songId = parseNcmNumericId(song.id.orEmpty()) ?: return@forEach
                    val track = song.toNcmTrackInfo().takeIf { it.id.isNotBlank() } ?: return@forEach
                    tracksById.putIfAbsent(songId, track)
                }
                is ApiResult.Error -> {
                    logger.warn("NCM getSongDetail batch failed: {}", result.message)
                }
            }
        }
        return ids.mapNotNull(tracksById::get)
    }

    private suspend fun fetchChannelPrograms(state: RuntimeState, channel: DjChannel): List<DjProgram> {
        val channelId = parseNcmNumericId(channel.id.orEmpty()) ?: return emptyList()
        val expectedCount = channel.programCount.coerceAtLeast(0)
        val pageSize = 100
        val programs = mutableListOf<DjProgram>()
        var offset = 0

        while (expectedCount == 0 || programs.size < expectedCount) {
            when (val result = callUpstream(
                action = "get dj channel programs",
                call = { id, limit, pageOffset -> state.client.getDjChannelPrograms(id, limit = limit, offset = pageOffset) },
                channelId,
                pageSize,
                offset,
            )) {
                is ApiResult.Success -> {
                    val page = result.data
                    if (page.isEmpty()) break
                    programs += page
                    if (page.size < pageSize) break
                    offset += page.size
                }
                is ApiResult.Error -> {
                    logger.warn("NCM getDjChannelPrograms failed for {}: {}", channelId, result.message)
                    return emptyList()
                }
            }
        }

        return programs
    }

    private suspend fun applyOverrides(state: RuntimeState, tracks: List<TrackInfo>): List<TrackInfo> {
        val config = state.config
        val removeSet = config.removeTrackIds.toSet()
        val result = tracks.filterNot { it.ncmSongIdOrNull() in removeSet }.toMutableList()

        if (config.addTrackIds.isNotEmpty()) {
            val addTracks = fetchSongsByIds(state, config.addTrackIds)
            val existingSongIds = result.mapNotNullTo(HashSet()) { it.ncmSongIdOrNull() }
            result.addAll(addTracks.filterNot { it.ncmSongIdOrNull() in existingSongIds })
        }

        return result
    }

    private fun apiErrorToText(error: ApiResult.Error): LocalizedText = when (error) {
        is ApiResult.Error.Transport -> when (error.cause) {
            is HttpTimeoutException, is InterruptedIOException -> LocalizedText.key("error.moemusic.source.timeout")
            is UnknownHostException, is ConnectException, is SocketException, is SSLException ->
                LocalizedText.key("error.moemusic.source.network")
            else -> LocalizedText.key("error.moemusic.source.network")
        }
        is ApiResult.Error.Parse -> LocalizedText.key("error.moemusic.ncmlite.bad_response")
        is ApiResult.Error.Business -> when {
            error.code in setOf(301, 302, 401, 403) -> LocalizedText.key("error.moemusic.source.auth")
            error.code == 429 -> LocalizedText.key("error.moemusic.source.rate_limit")
            else -> LocalizedText.key("error.moemusic.ncmlite.request_failed", error.code)
        }
    }

    private fun apiErrorToException(error: ApiResult.Error): SourceException = when (error) {
        is ApiResult.Error.Transport -> when (error.cause) {
            is HttpTimeoutException, is InterruptedIOException -> SourceTimeoutException(error.cause)
            is UnknownHostException, is ConnectException, is SocketException, is SSLException -> SourceNetworkException(error.cause)
            else -> SourceNetworkException(error.cause)
        }
        is ApiResult.Error.Parse -> SourceException(LocalizedText.key("error.moemusic.ncmlite.bad_response"), error.cause)
        is ApiResult.Error.Business -> when {
            error.code in setOf(301, 302, 401, 403) -> SourceAuthException()
            error.code == 429 -> SourceRateLimitException()
            else -> SourceException(LocalizedText.key("error.moemusic.ncmlite.request_failed", error.code))
        }
    }

    private fun ApiResult.Error.isRetryable(): Boolean = when (this) {
        is ApiResult.Error.Transport -> when (cause) {
            is HttpTimeoutException, is InterruptedIOException, is UnknownHostException,
            is ConnectException, is SocketException, is SSLException -> true
            else -> false
        }
        is ApiResult.Error.Parse -> false
        is ApiResult.Error.Business -> code == 429 || code >= 500
    }

    private suspend fun <A, T> callUpstream(
        action: String,
        call: suspend (A) -> ApiResult<T>,
        arg: A,
    ): ApiResult<T> {
        repeat(UPSTREAM_RETRY_ATTEMPTS - 1) { attempt ->
            when (val result = call(arg)) {
                is ApiResult.Success -> return result
                is ApiResult.Error -> {
                    if (!result.isRetryable()) return result
                    logger.warn(
                        "NCM {} transient failure on attempt {}/{}: {}",
                        action,
                        attempt + 1,
                        UPSTREAM_RETRY_ATTEMPTS,
                        result.message,
                    )
                    delay((UPSTREAM_RETRY_DELAY_MS * (attempt + 1)).milliseconds)
                }
            }
        }
        return call(arg)
    }

    private suspend fun <A, B, T> callUpstream(
        action: String,
        call: suspend (A, B) -> ApiResult<T>,
        arg1: A,
        arg2: B,
    ): ApiResult<T> {
        repeat(UPSTREAM_RETRY_ATTEMPTS - 1) { attempt ->
            when (val result = call(arg1, arg2)) {
                is ApiResult.Success -> return result
                is ApiResult.Error -> {
                    if (!result.isRetryable()) return result
                    logger.warn(
                        "NCM {} transient failure on attempt {}/{}: {}",
                        action,
                        attempt + 1,
                        UPSTREAM_RETRY_ATTEMPTS,
                        result.message,
                    )
                    delay((UPSTREAM_RETRY_DELAY_MS * (attempt + 1)).milliseconds)
                }
            }
        }
        return call(arg1, arg2)
    }

    private suspend fun <A, B, C, T> callUpstream(
        action: String,
        call: suspend (A, B, C) -> ApiResult<T>,
        arg1: A,
        arg2: B,
        arg3: C,
    ): ApiResult<T> {
        repeat(UPSTREAM_RETRY_ATTEMPTS - 1) { attempt ->
            when (val result = call(arg1, arg2, arg3)) {
                is ApiResult.Success -> return result
                is ApiResult.Error -> {
                    if (!result.isRetryable()) return result
                    logger.warn(
                        "NCM {} transient failure on attempt {}/{}: {}",
                        action,
                        attempt + 1,
                        UPSTREAM_RETRY_ATTEMPTS,
                        result.message,
                    )
                    delay((UPSTREAM_RETRY_DELAY_MS * (attempt + 1)).milliseconds)
                }
            }
        }
        return call(arg1, arg2, arg3)
    }

}
