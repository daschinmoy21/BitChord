package com.music.bitchord.data.innertube

import com.music.bitchord.data.LocaleTags
import com.music.bitchord.data.model.SearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchRegionRegressionTest {
    @Test fun searchUsesConnectionRegionWhilePreservingChineseCatalogues() {
        listOf("en", "en-IN", "hi", "pt-BR", "ja", "ko").forEach { assertNull(LocaleTags.ytSearchGl(it)) }
        assertEquals("TW", LocaleTags.ytSearchGl("zh-Hant"))
        assertEquals("CN", LocaleTags.ytSearchGl("zh-Hans"))
        assertEquals("US", LocaleTags.ytGl("en")) // Non-search requests keep their existing policy.
    }

    @Test
    fun `an uncatalogued episode card does not take the top result`() {
        val json = """
        {
          "contents": [
            {
              "musicCardShelfRenderer": {
                "title": { "runs": [{ "text": "Tum Hi Ho" }] },
                "subtitle": { "runs": [
                  { "text": "Episode" }, { "text": " • " }, { "text": "Mar 23, 2013" },
                  { "text": " • " }, { "text": "Arijit Singh" }
                ] },
                "onTap": { "browseEndpoint": {
                  "browseId": "MPEDNUo8CKI34o4",
                  "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                      "pageType": "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE"
                    }
                  }
                } }
              }
            },
            {
              "musicResponsiveListItemRenderer": {
                "playlistItemData": { "videoId": "tumhiho" },
                "flexColumns": [
                  { "musicResponsiveListItemFlexColumnRenderer": {
                    "text": { "runs": [{ "text": "Tum Hi Ho" }] }
                  } },
                  { "musicResponsiveListItemFlexColumnRenderer": {
                    "text": { "runs": [
                      { "text": "Song" }, { "text": " • " }, { "text": "Arijit Singh" }
                    ] }
                  } }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val page = InnertubeParser.parseSearchPage(Json.parseToJsonElement(json).jsonObject)

        assertTrue(page.rows.none { it is SearchResult.Browse })
        assertEquals("tumhiho", (page.rows.first() as SearchResult.Track).song.videoId)
    }

}
