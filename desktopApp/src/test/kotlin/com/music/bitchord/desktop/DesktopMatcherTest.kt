package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The case that made addons useless outside search. */
class DesktopMatcherTest {

    private val youtubeRow = Song(
        videoId = "kb_6Vz-k7Wc",
        title = "Tera Mera Rishta -  New Version (From \"Awarapan 2\")",
        artist = "Mithoon, Saaj Bhatt, Sayeed Quadri & Mustafa Zahid",
        thumbnailUrl = null,
    )

    @Test
    fun aCatalogueIsAskedForTheTitleWithoutItsPackaging() {
        val queries = DesktopTrackMatcher.queries(youtubeRow)

        // The film, and the four-way credit, are not part of the question.
        assertEquals(listOf("tera mera rishta version mithoon", "tera mera rishta version"), queries)
    }

    @Test
    fun theCatalogueListingOfTheSameTakeMatchesADecoratedYouTubeTitle() {
        // What JioSaavn actually files this under, film and extra credits and upload decoration all
        // absent.
        val catalogueRow = Song(
            videoId = "jiosaavn:wfTj8NYC",
            title = "Tera Mera Rishta - New Version",
            artist = "Mithoon",
            thumbnailUrl = null,
            durationText = "6:05",
        )

        assertNotNull(DesktopTrackMatcher.best(listOf(catalogueRow), youtubeRow))
    }

    @Test
    fun aDifferentTakeOfTheSameSongIsStillRefused() {
        // "New Version" is a take, so the plain listing is a different recording and must not stand
        // in for it.
        val plainListing = Song("jiosaavn:other", "Tera Mera Rishta", "Mithoon", null)

        assertNull(DesktopTrackMatcher.best(listOf(plainListing), youtubeRow))
    }

    @Test
    fun theUploadConventionOfArtistFirstIsUnderstood() {
        // "Artist - Title" hands the title over to the tail rather than eating it, so this is the
        // same recording as a catalogue's plain listing.
        val upload = Song("v", "Mithoon - Tera Mera Rishta (Official Video)", "Mithoon", null)

        assertEquals("tera mera rishta", DesktopTrackMatcher.searchableTitle(upload.title, upload.artist))
    }

    @Test
    fun aTakeIsIdentityAndHasToAgree() {
        val target = Song("v", "Song Title", "An Artist", null)
        val live = Song("a", "Song Title (Live)", "An Artist", null)
        val album = Song("b", "Song Title", "An Artist", null)

        // Asking for the album cut must not land on the live take…
        assertNull(DesktopTrackMatcher.best(listOf(live), target))
        // …and asking for the live take must not land on the album cut.
        assertNull(DesktopTrackMatcher.best(listOf(album), live))
        assertNotNull(DesktopTrackMatcher.best(listOf(album), target))
    }

    @Test
    fun packagingThatOnlyLooksLikeATakeIsIgnored() {
        val target = Song("v", "Song Title (Album Version)", "An Artist", null)
        val plain = Song("a", "Song Title", "An Artist", null)

        // "Album Version" describes the ordinary release; treating it as a take would stop a source
        // ever matching the plain listing.
        assertNotNull(DesktopTrackMatcher.best(listOf(plain), target))
    }

    @Test
    fun anUploadsTrailingLabelIsNotPartOfTheTitle() {
        assertEquals("paniyon sa", DesktopTrackMatcher.searchableTitle("Paniyon Sa Full Song", ""))
        // Never stripped to nothing: a track really called "Song" keeps its name.
        assertEquals("song", DesktopTrackMatcher.searchableTitle("Song", ""))
    }

    @Test
    fun onlyTheFirstCreditedArtistGoesIntoTheQuery() {
        assertEquals("mithoon", DesktopTrackMatcher.primaryArtist("Mithoon, Saaj Bhatt & Sayeed Quadri"))
        assertEquals("arijit singh", DesktopTrackMatcher.primaryArtist("Arijit Singh feat. Someone"))
    }
}

/** A native-script title written twice by YouTube, against a library that tags only one half. */
class DesktopBilingualTitleTest {

    // From a real YouTube Music row and a real Navidrome library: Japanese, a romaji copy after the
    // dash, the same English subtitle in full-width and ordinary brackets.
    private val youtube = Song(
        videoId = "bY5AqVorG_8",
        title = "彼女が冷たく笑ったら（prologue to the nine stages of change at the deceased remains） - " +
            "kanojo ga tsumetaku warattara (prologue to the nine stages of change at the deceased remains)",
        artist = "My Dead Girlfriend",
        thumbnailUrl = null,
        durationText = "4:34",
        albumName = "Hades (The Nine Stages Of Change At The Deceased Remains)",
    )

    private val library = Song(
        videoId = "navidrome:src/1",
        title = "彼女が冷たく笑ったら (Prologue To The Nine Stages Of Change At The Deceased Remains)",
        artist = "My Dead Girlfriend",
        thumbnailUrl = null,
        durationText = "4:34",
    )

    @Test
    fun theNativeHalfOfATwiceWrittenTitleIsTheSameTrack() {
        assertEquals(library, DesktopTrackMatcher.best(listOf(library), youtube))
    }

    @Test
    fun aDifferentTrackOnTheSameAlbumIsNot() {
        val other = library.copy(videoId = "navidrome:src/2", title = "手を振って", durationText = "3:48")
        assertNull(DesktopTrackMatcher.best(listOf(other), youtube))
    }

    @Test
    fun aSongAndItsFilmAreNotTwoSpellingsOfOneTitle() {
        // Latin on both sides of the dash: a song and the film it is from, not a transliteration pair.
        val row = Song("yt", "Paniyon Sa - Satyamev Jayate", "Atif Aslam", null, durationText = "4:10")
        val film = Song("jio", "Satyamev Jayate", "Atif Aslam", null, durationText = "4:10")
        assertNull(DesktopTrackMatcher.best(listOf(film), row))
    }

    @Test
    fun anExactTitleStillWinsWhenBothHalvesAreOffered() {
        val plainRomaji = library.copy(videoId = "navidrome:src/3", title = "kanojo ga tsumetaku warattara")
        assertNotNull(DesktopTrackMatcher.best(listOf(library, plainRomaji), youtube))
    }

    @Test
    fun theLibraryIsAskedForTheAlbumAndTheArtistWhenTheTitleFindsNothing() {
        assertEquals(
            listOf("hades my dead girlfriend", "my dead girlfriend"),
            DesktopNavidromeSource.fallbackQueries(youtube),
        )
        // No album known: the artist alone.
        assertEquals(listOf("my dead girlfriend"), DesktopNavidromeSource.fallbackQueries(youtube.copy(albumName = null)))
        // Nothing to go on.
        assertEquals(emptyList(), DesktopNavidromeSource.fallbackQueries(youtube.copy(albumName = null, artist = "")))
    }
}
