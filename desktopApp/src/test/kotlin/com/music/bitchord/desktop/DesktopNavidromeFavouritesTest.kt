package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNavidromeFavouritesTest {
    @Test fun firstMigrationKeepsLegacyHeartsAndOtherServices() {
        val cached = setOf("navidrome:one/old", "navidrome:two/keep", "youtube-id")
        assertEquals(
            cached + "navidrome:one/server",
            mergeNavidromeFavourites(cached, setOf("navidrome:one/server"), "one", emptySet(), true),
        )
        assertEquals(
            setOf("navidrome:two/keep", "youtube-id", "navidrome:one/server"),
            mergeNavidromeFavourites(cached, setOf("navidrome:one/server"), "one", emptySet(), false),
        )
    }

    @Test fun pendingLikesAndUnlikesAreNotOverwrittenByTheServer() {
        assertEquals(
            setOf("navidrome:one/liked"),
            mergeNavidromeFavourites(
                setOf("navidrome:one/liked"), setOf("navidrome:one/unliked"), "one",
                setOf("navidrome:one/liked", "navidrome:one/unliked"), false,
            ),
        )
    }
}
