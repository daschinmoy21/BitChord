package com.music.bitchord.data.settings

/**
 * Stable persisted ordering for a Library "Show all" grid — playlists or
 * albums. A card there only ever carries a title, so the one date-based order
 * comes from the app's own listening — see [RECENTLY_PLAYED].
 */
enum class LibrarySort {
    /** Whatever order the shelf itself arrived in — YouTube Music's own. */
    DEFAULT,
    TITLE_ASC,
    TITLE_DESC,
    /**
     * Most recently played first, from the host's own record of plays; cards
     * never played keep their default order behind them. Only offered where
     * the host keeps that record.
     */
    RECENTLY_PLAYED,
}

/** Display mode for music lists: compact rows or grid cards. */
enum class LibraryViewType {
    LIST,
    GRID,
}
