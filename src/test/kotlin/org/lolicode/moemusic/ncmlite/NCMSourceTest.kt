package org.lolicode.moemusic.ncmlite

import kotlinx.coroutines.runBlocking
import org.lolicode.moemusic.api.IdentifierResolutionResult
import org.lolicode.moemusic.api.LocalizedText
import org.lolicode.moemusic.api.SourceFormatException
import org.lolicode.moemusic.api.UserResult
import org.lolicode.moemusic.api.model.TrackInfo
import org.lolicode.moemusic.api.model.toArtistInfos
import org.lolicode.ncmapilitekt.NeteaseClient
import org.lolicode.ncmapilitekt.model.Album
import org.lolicode.ncmapilitekt.model.Artist
import org.lolicode.ncmapilitekt.model.DjChannel
import org.lolicode.ncmapilitekt.model.DjProgram
import org.lolicode.ncmapilitekt.model.Song
import org.lolicode.ncmapilitekt.model.UserInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NCMSourceTest {

    private val source = NCMSource(NeteaseClient())

    @Test
    fun `unsupported non-netease urls pass identifier resolution`() = runBlocking {
        val result = source.resolveIdentifier("https://example.com/song?id=1", null)

        assertEquals(IdentifierResolutionResult.Pass, result)
    }

    @Test
    fun `extracts netease urls from dirty share text`() {
        val url = extractNeteaseUrl("分享歌曲：广告文案 https://y.music.163.com/m/song?id=123456&userid=789 来自***音乐")

        assertEquals("https://y.music.163.com/m/song?id=123456&userid=789", url)
    }

    @Test
    fun `trims punctuation around extracted netease url`() {
        val url = extractNeteaseUrl("check this (https://music.163.com/song?id=42).")

        assertEquals("https://music.163.com/song?id=42", url)
    }

    @Test
    fun `ignores dirty text without supported netease url`() {
        assertNull(extractNeteaseUrl("share text https://example.com/song?id=1"))
    }

    @Test
    fun `recognizes netease share hosts`() {
        assertTrue(isSupportedNeteaseUrl("https://music.163.com/song?id=1"))
        assertTrue(isSupportedNeteaseUrl("https://y.music.163.com/m/song?id=1"))
        assertTrue(isSupportedNeteaseUrl("https://163cn.tv/abc"))
        assertTrue(isSupportedNeteaseUrl("https://www.163.fm/abc"))
    }

    @Test
    fun `detects direct media urls`() {
        assertTrue(isNeteaseDirectMediaUrl("https://music.163.com/song/media/outer/url?id=123456.mp3"))
        assertEquals(false, isNeteaseDirectMediaUrl("https://music.163.com/song?id=123456"))
    }

    @Test
    fun `invalid track id returns localized getTrackInfo error`() = runBlocking {
        val result = source.getTrackInfo("abc")

        val error = assertIs<UserResult.Error>(result)
        assertEquals(LocalizedText.key("error.moemusic.ncmlite.identifier.invalid_song_id"), error.message)
    }

    @Test
    fun `malformed typed track ids return localized getTrackInfo error`() = runBlocking {
        val songResult = source.getTrackInfo("song:abc")
        val programResult = source.getTrackInfo("dj-program:abc")
        val compositeProgramResult = source.getTrackInfo("dj-program:9988:song:abc")

        assertEquals(
            LocalizedText.key("error.moemusic.ncmlite.identifier.invalid_song_id"),
            assertIs<UserResult.Error>(songResult).message,
        )
        assertEquals(
            LocalizedText.key("error.moemusic.ncmlite.identifier.invalid_song_id"),
            assertIs<UserResult.Error>(programResult).message,
        )
        assertEquals(
            LocalizedText.key("error.moemusic.ncmlite.identifier.invalid_song_id"),
            assertIs<UserResult.Error>(compositeProgramResult).message,
        )
    }

    @Test
    fun `resolve rejects malformed ids before upstream call`() {
        val track = TrackInfo(
            id = "abc",
            title = "Test",
            artists = listOf("Artist").toArtistInfos(),
            durationMs = 1000,
            sourceId = NCMSource.SOURCE_ID,
        )

        val error = kotlin.runCatching {
            runBlocking { source.resolve(track) }
        }.exceptionOrNull()

        val formatError = assertIs<SourceFormatException>(error)
        assertEquals(LocalizedText.key("error.moemusic.source.bad_format"), formatError.userMessage)
    }

    @Test
    fun `ncm track key parser accepts canonical typed ids and legacy song ids`() {
        assertEquals(NCMTrackKey.Song("123"), parseNcmTrackKey("123"))
        assertEquals(NCMTrackKey.Song("123"), parseNcmTrackKey("song:123"))
        assertEquals(NCMTrackKey.DjProgram(programId = "9988", mainSongId = null), parseNcmTrackKey("dj-program:9988"))
        assertEquals(NCMTrackKey.DjProgram(programId = "9988", mainSongId = "123"), parseNcmTrackKey("dj-program:9988:song:123"))
        assertEquals(null, parseNcmTrackKey("song:abc"))
        assertEquals(null, parseNcmTrackKey("dj-program:"))
        assertEquals(null, parseNcmTrackKey("dj-program:9988:song:abc"))
        assertEquals(null, parseNcmTrackKey("dj-program:9988:track:123"))
    }

    @Test
    fun `song conversion uses canonical source local id`() {
        val track = Song(
            id = "123",
            name = "Song",
            duration = 180_000,
            artists = listOf(Artist(id = "456", name = "Artist")),
            album = Album(name = "Album", picUrl = "https://example.com/cover.jpg"),
        ).toNcmTrackInfo()

        assertEquals("song:123", track.id)
        assertEquals("Song", track.title)
        assertEquals(NCMSource.SOURCE_ID, track.sourceId)
        assertEquals("Album", track.album)
    }

    @Test
    fun `dj program conversion uses program id and program metadata`() {
        val track = DjProgram(
            id = "9988",
            name = "Program Title",
            coverUrl = "https://example.com/program.jpg",
            dj = UserInfo(userId = "42", nickname = "Uploader"),
            channel = DjChannel(id = "7", name = "Channel"),
            mainSong = DjProgram.MainSong(
                id = "123",
                name = "Main Song Title",
                duration = 60_000,
                album = Album(name = "Song Album", picUrl = "https://example.com/song.jpg"),
                artists = emptyList(),
            ),
        ).toNcmTrackInfo()

        val resolved = assertIs<TrackInfo>(track)
        assertEquals("dj-program:9988:song:123", resolved.id)
        assertEquals("Program Title", resolved.title)
        assertEquals("Channel", resolved.album)
        assertEquals("https://example.com/program.jpg", resolved.coverUrl)
        assertEquals("Uploader", resolved.artists.single().name)
        assertTrue(resolved.lyricsFetched)
    }

    @Test
    fun `dj program conversion requires main song id`() {
        val missingSong = DjProgram(
            id = "9988",
            name = "Program Title",
            mainSong = null,
        ).toNcmTrackInfo()
        val missingSongId = DjProgram(
            id = "9988",
            name = "Program Title",
            mainSong = DjProgram.MainSong(
                id = null,
                name = "Main Song Title",
                duration = 60_000,
            ),
        ).toNcmTrackInfo()

        assertNull(missingSong)
        assertNull(missingSongId)
    }
}

class NCMPluginConfigTest {

    @Test
    fun `playlist validator allows blank value to disable autoplay`() {
        val entry = NCMPlugin.configSpec.entries.single { it.key == "playlist_id" }

        val message = entry.validate(NCMConfig(), "")

        assertEquals(null, message)
    }

    @Test
    fun `playlist validator returns localized key for non numeric values`() {
        val entry = NCMPlugin.configSpec.entries.single { it.key == "playlist_id" }

        val message = entry.validate(NCMConfig(), "abc")

        assertEquals(LocalizedText.key("config.moemusic.ncmlite.source.validation.playlist_numeric"), message)
    }

    @Test
    fun `track id validator names the offending config field`() {
        val entry = NCMPlugin.configSpec.entries.single { it.key == "add_track_ids" }

        val message = entry.validate(NCMConfig(), listOf("12a"))

        assertEquals(
            LocalizedText.key(
                "config.moemusic.ncmlite.source.validation.track_ids_numeric",
                LocalizedText.key("config.moemusic.ncmlite.source.add_track_ids"),
            ),
            message,
        )
    }
}
