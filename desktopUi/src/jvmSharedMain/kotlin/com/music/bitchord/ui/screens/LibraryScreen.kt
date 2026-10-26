package com.music.bitchord.ui.screens

import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import com.music.bitchord.ui.components.ShelfRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.LibraryCatalogResult
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.libraryRecentTitleKey
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PageScrollbar
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.SearchField
import com.music.bitchord.ui.components.ControlTooltip
import com.music.bitchord.ui.components.libraryGrid
import com.music.bitchord.ui.components.librarySkeleton
import kotlinx.coroutines.delay

/**
 * The signed-in library: the saved collections, as shelves of cards.
 *
 * Deliberately only the collections. This page used to end with two runs of
 * track rows — "Liked Music" and "Songs" — which are two overlapping answers
 * to the same question and read as one list that couldn't make up its mind: a
 * track that stopped being liked didn't leave the page, it moved down it, into
 * a section most people had taken for more of the same. Liked Music is a
 * playlist, and it is reached the way every other playlist here is, by opening
 * its card.
 *
 * The liked list is still fetched — it is what the rest of the app reads a
 * track's rating off (see MainViewModel's `likeStatuses`); it just isn't a
 * second place to browse it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    signedIn: Boolean,
    state: UiState<LibraryPage>,
    listState: LazyListState,
    onShelfItemClick: (ShelfItem) -> Unit,
    onShelfItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportSpotifyPlaylist: (() -> Unit)? = null,
    /**
     * Opens the rest of a shelf when its responsive preview row is full.
     */
    onShowAll: (HomeShelf) -> Unit,
    /**
     * The way in to Replay at the head of the page — the row of Replay cards,
     * their placeholders while the history is read, or nothing at all.
     */
    replay: @Composable () -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    /**
     * The device's folders — downloads, local files, the remote libraries this
     * build supports — drawn as a list under the Replay cards. Built by the
     * app, since which of those exist is the platform's business.
     */
    links: List<LibraryLink>,
    /**
     * The "On device" shelf under that list: the releases kept on this device
     * whole. Left off the page entirely while there are none.
     */
    deviceItems: List<ShelfItem>,
    /** The big "Library" heading; the desktop's pages carry none. */
    showTitle: Boolean = true,
    /**
     * Shelves drawn when signed out. The phone passes nothing, so signing out
     * there still ends on the sign-in message.
     */
    signedOutShelves: List<HomeShelf> = emptyList(),
    searchState: LibrarySearchState = rememberLibrarySearchState(),
    searchCatalog: (suspend (String, SearchFilter) -> LibraryCatalogResult)? = null,
    searchRevision: Any? = null,
    sourceError: String? = null,
) {
    val pinnedPlaylists by AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    val onDevice = stringResource(Res.string.on_device)
    var query by searchState::query
    var filter by searchState::filter
    val catalogFilter = when (filter) {
        LibraryFilter.ALL -> SearchFilter.ALL
        LibraryFilter.PLAYLISTS -> SearchFilter.PLAYLISTS
        LibraryFilter.ALBUMS -> SearchFilter.ALBUMS
        LibraryFilter.ARTISTS -> SearchFilter.ARTISTS
        LibraryFilter.ON_DEVICE, LibraryFilter.PODCASTS -> null
    }
    val currentSearch by rememberUpdatedState(searchCatalog)
    var catalog by remember(query, filter, searchRevision) { mutableStateOf<LibraryCatalogResult?>(null) }
    val searchOnline = searchCatalog != null && query.isNotBlank() && catalogFilter != null
    LaunchedEffect(query, filter, searchRevision) {
        if (searchOnline) {
            delay(350)
            catalog = currentSearch?.invoke(query.trim(), catalogFilter)
        }
    }
    val filtering = query.isNotBlank() || filter != LibraryFilter.ALL
    val sourceShelves = if (signedIn) (state as? UiState.Success)?.data?.shelves.orEmpty() else signedOutShelves
    val shelves = remember(sourceShelves, query, filter) { sourceShelves.filterLibrary(query, filter) }
    val catalogShelves = remember(catalog, shelves) {
        val shown = shelves.flatMap { it.items }.map { it.videoId ?: it.browseId }.toSet()
        catalog?.shelves.orEmpty().map { shelf ->
            shelf.copy(items = shelf.items.filter { (it.videoId ?: it.browseId) !in shown })
        }.filter { it.items.isNotEmpty() }
    }
    val matchingDeviceItems = remember(deviceItems, query, filter) {
        if (filter == LibraryFilter.ALL || filter == LibraryFilter.ON_DEVICE) deviceItems.matchingLibraryQuery(query)
        else emptyList()
    }
    val matchingLinks = remember(links, query, filter) {
        val matchingItems = links.map { it.item }.matchingLibraryQuery(query).toSet()
        links.filter { link ->
            link.item in matchingItems && (filter == LibraryFilter.ALL ||
                filter == LibraryFilter.ON_DEVICE && link.item.browseId?.startsWith("local:") == true)
        }
    }
    val showPlaylistActions = query.isBlank() && (filter == LibraryFilter.ALL || filter == LibraryFilter.PLAYLISTS)
    fun openShelf(shelf: HomeShelf) {
        // The grid starts with the current query but retains every item, so clearing
        // its search restores the full collection rather than a filtered snapshot.
        searchState.collectionQuery = query
        onShowAll(sourceShelves.firstOrNull { it.title == shelf.title } ?: shelf.copy(items = deviceItems))
    }
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (showTitle) {
                item {
                    Text(
                        text = stringResource(Res.string.library),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                    )
                }
            }
            item(key = "search") {
                LibrarySearchControls(
                    query = query,
                    onQueryChange = { query = it },
                    filter = filter,
                    onFilterChange = { filter = it },
                    refreshing = refreshing,
                    onRefresh = onRefresh,
                )
            }
            if (!filtering) {
                item(key = "replay") { replay() }
            }
            if (matchingLinks.isNotEmpty()) {
                item(key = "links") { LibraryLinkList(links = matchingLinks, onClick = onShelfItemClick) }
            }
            if (matchingDeviceItems.isNotEmpty()) {
                item(key = "shelf:$onDevice") {
                    val onDeviceShelf = HomeShelf(title = onDevice, items = matchingDeviceItems)
                    LibraryGridShelf(
                        shelf = onDeviceShelf,
                        onItemClick = onShelfItemClick,
                        onItemLongPress = onShelfItemLongPress,
                        onShowAll = { openShelf(onDeviceShelf) },
                    )
                }
            }
            if (filtering && shelves.isEmpty() && matchingDeviceItems.isEmpty() && matchingLinks.isEmpty() &&
                (!signedIn || state is UiState.Success || filter == LibraryFilter.ON_DEVICE) &&
                (!searchOnline || catalog != null && catalog!!.shelves.isEmpty() && catalog!!.failedSources.isEmpty())
            ) {
                item(key = "no_matches") {
                    LibraryNoMatches(onClear = { query = ""; filter = LibraryFilter.ALL })
                }
            }
            if (showPlaylistActions && shelves.none { it.title == PLAYLISTS } && (!signedIn || state is UiState.Success)) {
                item(key = "shelf:$PLAYLISTS") {
                    val emptyPlaylists = HomeShelf(PLAYLISTS, emptyList())
                    PlaylistShelf(
                        shelf = emptyPlaylists,
                        onItemClick = onShelfItemClick,
                        onItemLongPress = onShelfItemLongPress,
                        onNewPlaylist = onNewPlaylist,
                        onImportSpotifyPlaylist = onImportSpotifyPlaylist,
                        onShowAll = { searchState.collectionQuery = ""; onShowAll(emptyPlaylists) },
                    )
                }
            }
            shelves.forEach { shelf ->
                item(key = "shelf:${shelf.title}") {
                    val pinnedFirst = shelf.pinnedFirst(pinnedPlaylists)
                    if (shelf.title == PLAYLISTS && showPlaylistActions) {
                        PlaylistShelf(
                            shelf = pinnedFirst,
                            onItemClick = onShelfItemClick,
                            onItemLongPress = onShelfItemLongPress,
                            onNewPlaylist = onNewPlaylist,
                            onImportSpotifyPlaylist = onImportSpotifyPlaylist,
                            onShowAll = { openShelf(shelf) },
                            pinnedPlaylists = pinnedPlaylists,
                        )
                    } else {
                        LibraryGridShelf(
                            shelf = pinnedFirst,
                            onItemClick = onShelfItemClick,
                            onItemLongPress = onShelfItemLongPress,
                            onShowAll = { openShelf(shelf) },
                            pinnedPlaylists = pinnedPlaylists,
                        )
                    }
                }
            }
            if (searchOnline) {
                if (catalog == null) {
                    item(key = "catalog_loading") {
                        Row(Modifier.fillMaxWidth().padding(PAGE_GUTTER), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                catalogShelves.forEachIndexed { index, shelf ->
                    item(key = "catalog:$index:${shelf.title}") {
                        LibraryGridShelf(
                            shelf = shelf,
                            onItemClick = onShelfItemClick,
                            onItemLongPress = onShelfItemLongPress,
                            onShowAll = { searchState.collectionQuery = ""; onShowAll(shelf) },
                        )
                    }
                }
                if (!catalog?.failedSources.isNullOrEmpty()) {
                    item(key = "catalog_error") {
                        MessageState(stringResource(Res.string.library_source_search_failed, catalog!!.failedSources.joinToString(", ")))
                    }
                }
            }
            if (filter == LibraryFilter.ON_DEVICE) return@LazyColumn
            if (sourceError != null) {
                item(key = "source_error") {
                    MessageState(sourceError, actionLabel = stringResource(Res.string.retry), onAction = onRefresh)
                }
            }
            if (!signedIn) {
                item(key = "sign_in") {
                    MessageState(
                        message = stringResource(Res.string.library_sign_in_description),
                        actionLabel = stringResource(Res.string.sign_in),
                        onAction = onSignIn,
                    )
                }
            } else when (state) {
                is UiState.Loading -> librarySkeleton()
                is UiState.Error -> item(key = "library_error") {
                    MessageState(state.message, actionLabel = stringResource(Res.string.retry), onAction = onRetry)
                }
                is UiState.Success -> Unit
            }
        }
        PageScrollbar(listState, insets = contentPadding)
    }
}

@Composable
private fun LibrarySearchControls(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: LibraryFilter? = null,
    onFilterChange: (LibraryFilter) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(horizontal = PAGE_GUTTER),
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(contentPadding), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSubmit = {},
                placeholder = stringResource(Res.string.library_search_hint),
                modifier = Modifier.weight(1f),
            )
            if (onRefresh != null) {
                ControlTooltip(stringResource(Res.string.library_refresh_tooltip)) {
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, contentDescription = stringResource(Res.string.library_refresh))
                    }
                }
            }
        }
        if (filter != null) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryFilter.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == filter,
                        onClick = { onFilterChange(entry) },
                        label = { Text(libraryFilterLabel(entry), maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun libraryFilterLabel(filter: LibraryFilter): String = stringResource(
    when (filter) {
        LibraryFilter.ALL -> Res.string.library_filter_all
        LibraryFilter.PLAYLISTS -> Res.string.playlists
        LibraryFilter.ALBUMS -> Res.string.albums
        LibraryFilter.ARTISTS -> Res.string.artists
        LibraryFilter.PODCASTS -> Res.string.library_filter_podcasts
        LibraryFilter.ON_DEVICE -> Res.string.on_device
    },
)

@Composable
private fun LibraryNoMatches(onClear: () -> Unit) {
    MessageState(
        message = stringResource(Res.string.library_no_matches),
        actionLabel = stringResource(Res.string.library_clear_filters),
        onAction = onClear,
    )
}

/** One of the Library's folder rows: the page [item] opens, behind [icon]. */
/** [logo], when set, is drawn in place of [icon] — a service's own mark. */
data class LibraryLink(val item: ShelfItem, val icon: ImageVector, val logo: DrawableResource? = null)

/**
 * The folders as a plain list — icon, name, chevron, hairlines between — the
 * way a music app's library has always opened, rather than as cards that all
 * look alike because none of them has artwork.
 */
@Composable
private fun LibraryLinkList(links: List<LibraryLink>, onClick: (ShelfItem) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 22.dp)) {
        links.forEachIndexed { index, link ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable { onClick(link.item) }
                    .padding(horizontal = PAGE_GUTTER),
            ) {
                if (link.logo != null) {
                    // A mark, not a glyph: white on dark, black on light, with
                    // its cut-outs left clear.
                    Icon(
                        painter = painterResource(link.logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(LINK_ICON_SIZE),
                    )
                } else {
                    Icon(
                        imageVector = link.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(LINK_ICON_SIZE),
                    )
                }
                Spacer(Modifier.width(LINK_ICON_GAP))
                Text(
                    text = link.item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = BitChordIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (index < links.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = PAGE_GUTTER + LINK_ICON_SIZE + LINK_ICON_GAP),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private val LINK_ICON_SIZE = 24.dp
private val LINK_ICON_GAP = 16.dp

/**
 * The one shelf on this page that can be written to: it leads with the tile
 * that creates a playlist, and holding a card gets rename and delete on top of
 * the queue actions every other shelf's menu offers.
 */
@Composable
private fun PlaylistShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportSpotifyPlaylist: (() -> Unit)? = null,
    onShowAll: () -> Unit,
    pinnedPlaylists: List<String> = emptyList(),
) {
    LibraryGridShelf(
        shelf = shelf,
        onItemClick = onItemClick,
        onItemLongPress = onItemLongPress,
        onShowAll = onShowAll,
        pinnedPlaylists = pinnedPlaylists,
        leadingCardCount = if (onImportSpotifyPlaylist != null) 2 else 1,
        leadingCard = { cardWidth ->
            Row(horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING)) {
                NewShelfCard(
                    icon = BitChordIcons.Plus,
                    label = stringResource(Res.string.new_playlist),
                    subtitle = stringResource(Res.string.saved_to_youtube_music),
                    onClick = onNewPlaylist,
                    modifier = Modifier.width(cardWidth),
                )
                if (onImportSpotifyPlaylist != null) {
                    NewShelfCard(
                        icon = BitChordIcons.Download,
                        label = stringResource(Res.string.import_spotify),
                        subtitle = stringResource(Res.string.import_spotify_subtitle),
                        onClick = onImportSpotifyPlaylist,
                        modifier = Modifier.width(cardWidth),
                        logo = Res.drawable.spotify_logo,
                    )
                }
            }
        },
    )
}

/**
 * A full-width Library preview, with as many cards as fit in the window.
 * Extra items remain accessible through Show all. Playlist action tiles take
 * the same width and count as collection cards.
 */
@Composable
internal fun LibraryGridShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onShowAll: () -> Unit,
    leadingCard: (@Composable (Dp) -> Unit)? = null,
    leadingCardCount: Int = if (leadingCard != null) 1 else 0,
    pinnedPlaylists: List<String> = emptyList(),
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
        val visibleItems = shelf.items.take((grid.columns - leadingCardCount).coerceAtLeast(0))
        Column(Modifier.padding(bottom = 26.dp)) {
            SectionHeader(
                title = shelf.title,
                subtitle = shelf.subtitle,
                onShowAll = onShowAll.takeIf { shelf.items.size + leadingCardCount > grid.columns },
            )
            ShelfRow(
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
            ) {
                leadingCard?.let { card -> item(key = "leading") { card(grid.cardWidth) } }
                items(visibleItems) { item ->
                    ShelfCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onLongPress = { onItemLongPress(item) },
                        modifier = Modifier.width(grid.cardWidth),
                        isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                    )
                }
            }
        }
    }
}

/**
 * Everything a Library shelf's "Show all" opens onto — the same cards, at the
 * same [libraryGrid] width, run down the screen instead of stopping at one row.
 */
@Composable
fun LibraryGridPage(
    shelf: HomeShelf,
    gridState: LazyGridState,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNewPlaylist: (() -> Unit)? = null,
    initialQuery: String = "",
) {
    // Re-read live rather than trusting [shelf] to already be sorted: this page
    // is opened from a snapshot (see `AppRoute.LibraryShowAll`), and a
    // pin toggled from this page's own long-press menu must move the card
    // immediately rather than waiting for the row underneath to be revisited.
    val pinnedPlaylists by AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    val librarySort by AppUi.host.librarySort.collectAsStateWithLifecycle()
    val recentlyPlayed by AppUi.host.libraryRecentlyPlayed.collectAsStateWithLifecycle()
    var query by rememberSaveable(shelf.title) { mutableStateOf(initialQuery) }
    // Pinning wins over the default order, but an explicit sort is a stronger,
    // more deliberate signal than a pin and is left to reorder the whole grid,
    // pinned cards included.
    val sortedShelf = shelf.pinnedFirst(pinnedPlaylists).sortedForLibrary(librarySort, recentlyPlayed)
    val matchingItems = remember(sortedShelf, query) { sortedShelf.items.matchingLibraryQuery(query) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(grid.columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = PAGE_GUTTER),
        ) {
            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                LibrarySearchControls(
                    query = query,
                    onQueryChange = { query = it },
                    contentPadding = PaddingValues(0.dp),
                )
            }
            if (matchingItems.isEmpty() && query.isNotBlank()) {
                item(key = "no_matches", span = { GridItemSpan(maxLineSpan) }) {
                    LibraryNoMatches(onClear = { query = "" })
                }
            }
            if (onNewPlaylist != null && query.isBlank()) {
                item(key = "leading") {
                    NewShelfCard(
                        icon = BitChordIcons.Plus,
                        label = stringResource(Res.string.new_playlist),
                        subtitle = stringResource(Res.string.saved_to_youtube_music),
                        onClick = onNewPlaylist,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(matchingItems, key = { it.browseId ?: it.title }) { item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                    modifier = Modifier.fillMaxWidth(),
                    isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                )
            }
        }
        PageScrollbar(gridState, insets = contentPadding)
    }
}

/**
 * Moves whichever of this shelf's cards are in [pinned] to the front, in the
 * order they were pinned, leaving everything else in its existing order behind
 * them.
 *
 * A no-op on any shelf that isn't Playlists: [pinned] only ever holds playlist
 * browse ids, so an album or artist shelf never has a card that matches.
 */
private fun HomeShelf.pinnedFirst(pinned: List<String>): HomeShelf {
    if (pinned.isEmpty()) return this
    val byId = items.filter { it.browseId != null }.associateBy { it.browseId }
    val pinnedItems = pinned.mapNotNull { byId[it] }
    if (pinnedItems.isEmpty()) return this
    val pinnedSet = pinnedItems.toSet()
    return copy(items = pinnedItems + items.filter { it !in pinnedSet })
}

/**
 * A card's title is all a Library shelf carries, so the alphabetical options
 * sort on it, and [LibrarySort.RECENTLY_PLAYED] reads [recentlyPlayed] — by
 * browse id, else by title. The sort is stable, so cards never played keep
 * their default order at the end.
 */
private fun HomeShelf.sortedForLibrary(sort: LibrarySort, recentlyPlayed: Map<String, Long>): HomeShelf = when (sort) {
    LibrarySort.DEFAULT -> this
    LibrarySort.RECENTLY_PLAYED -> copy(
        items = items.sortedByDescending { item ->
            item.browseId?.let(recentlyPlayed::get)
                ?: recentlyPlayed[libraryRecentTitleKey(item.title)]
                ?: Long.MIN_VALUE
        },
    )
    LibrarySort.TITLE_ASC -> copy(items = items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }))
    LibrarySort.TITLE_DESC -> copy(
        items = items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }),
    )
}

/** The library feed whose cards are the account's own — see [PlaylistShelf]. */
private const val PLAYLISTS = YtMusicRepository.PLAYLISTS_SHELF
