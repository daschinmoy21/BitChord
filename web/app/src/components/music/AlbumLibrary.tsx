import { Search, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Select } from '@/components/Select';
import { albumArtists, DEFAULT_ALBUM_FILTERS, filterAlbums, type AlbumFilters } from '@/data/music/albumFilters';
import type { MusicItem } from '@/data/music/types';
import { seeAllGrid } from '@/pages/pageStyles';
import { SquareLockup } from './Lockups';
import { EmptyState } from './States';

interface AlbumLibraryProps {
  albums: MusicItem[];
  hasNextPage: boolean;
  fetching: boolean;
  loadMoreFailed: boolean;
  onLoadMore: () => void;
}

export function AlbumLibrary({ albums, hasNextPage, fetching, loadMoreFailed, onLoadMore }: AlbumLibraryProps) {
  const [filters, setFilters] = useState(DEFAULT_ALBUM_FILTERS);
  const input = useRef<HTMLInputElement>(null);
  const artists = useMemo(() => albumArtists(albums), [albums]);
  const visible = useMemo(() => filterAlbums(albums, filters), [albums, filters]);
  const filtering = Boolean(filters.query.trim() || filters.artist || filters.releaseType !== 'all');
  const needsWholeLibrary = filtering || filters.sort !== 'library';
  const searching = needsWholeLibrary && hasNextPage && !loadMoreFailed;
  const update = <K extends keyof AlbumFilters>(key: K, value: AlbumFilters[K]) => setFilters((current) => ({ ...current, [key]: value }));

  // A filtered grid can hide the scroll sentinel. Read every remaining page
  // sequentially so matches and alphabetical sorting cover the whole library.
  useEffect(() => {
    if (needsWholeLibrary && hasNextPage && !fetching && !loadMoreFailed) onLoadMore();
  }, [needsWholeLibrary, hasNextPage, fetching, loadMoreFailed, onLoadMore]);

  return (
    <>
      <div className="mx-(--body-gutter) mt-5 flex flex-col gap-3">
        <div role="search" aria-label="Search library albums" className="relative flex w-full max-w-[492px] items-center">
          <Search size={15} strokeWidth={2.4} className="pointer-events-none absolute left-3 text-secondary" aria-hidden="true" />
          <input
            ref={input}
            type="search"
            aria-label="Search albums and artists"
            placeholder="Search albums and artists"
            value={filters.query}
            onChange={(event) => update('query', event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Escape') update('query', '');
            }}
            className="h-[38px] w-full rounded-pill border border-search-border bg-page pr-9 pl-9 type-body text-primary placeholder:text-tertiary [&::-webkit-search-cancel-button]:hidden"
          />
          {filters.query && (
            <button
              type="button"
              aria-label="Clear album search"
              className="absolute right-1 grid size-8 place-items-center rounded-full text-secondary hover:text-primary"
              onClick={() => {
                update('query', '');
                input.current?.focus();
              }}
            >
              <X size={16} aria-hidden="true" />
            </button>
          )}
        </div>
        <div role="group" aria-label="Album filters and sorting" className="flex flex-wrap items-center gap-x-4 gap-y-3">
          <div className="flex min-w-0 max-w-full items-center gap-2 [&>button]:flex-initial">
            <span className="flex-none type-callout text-secondary">Artist</span>
            <Select label="Filter by artist" value={filters.artist} onChange={(value) => update('artist', value)} options={[{ value: '', label: 'All artists' }, ...artists]} />
          </div>
          <div className="flex items-center gap-2">
            <span className="type-callout text-secondary">Release</span>
            <Select
              label="Filter by release type"
              value={filters.releaseType}
              onChange={(value) => update('releaseType', value)}
              options={[{ value: 'all', label: 'All releases' }, { value: 'album', label: 'Albums' }, { value: 'single', label: 'Singles' }, { value: 'ep', label: 'EPs' }]}
            />
          </div>
          <div className="flex items-center gap-2">
            <span className="type-callout text-secondary">Sort</span>
            <Select
              label="Sort albums"
              value={filters.sort}
              onChange={(value) => update('sort', value)}
              options={[{ value: 'library', label: 'Library order' }, { value: 'title', label: 'Album title' }, { value: 'artist', label: 'Artist name' }]}
            />
          </div>
          {(filtering || filters.sort !== 'library') && (
            <button type="button" className="type-callout-emphasized text-key" onClick={() => setFilters(DEFAULT_ALBUM_FILTERS)}>
              Reset
            </button>
          )}
        </div>
        <p role="status" className="type-callout text-secondary">
          {visible.length} {visible.length === 1 ? 'album' : 'albums'}
          {searching ? ' · Searching your library…' : fetching ? ' · Loading more albums…' : ''}
        </p>
      </div>
      {visible.length > 0 ? (
        <ul className={seeAllGrid} aria-label="Library albums" aria-busy={searching || fetching}>
          {visible.map((item, i) => <li key={`${item.id}-${i}`}><SquareLockup item={item} /></li>)}
        </ul>
      ) : searching ? null : filtering ? (
        <EmptyState title={hasNextPage ? 'No matching albums yet' : 'No matching albums'} detail="Try another search or reset the filters." />
      ) : (
        <EmptyState title="Nothing here yet" />
      )}
    </>
  );
}
