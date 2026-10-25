import { normalizedArtistName } from '@/lib/artistCredits';
import type { MusicItem } from './types';

export type AlbumSort = 'library' | 'title' | 'artist';
export type ReleaseType = 'all' | 'album' | 'single' | 'ep';

export interface AlbumFilters {
  query: string;
  artist: string;
  releaseType: ReleaseType;
  sort: AlbumSort;
}

export const DEFAULT_ALBUM_FILTERS: AlbumFilters = { query: '', artist: '', releaseType: 'all', sort: 'library' };

export function albumArtists(albums: readonly MusicItem[]) {
  const names = new Map<string, string>();
  for (const album of albums) {
    for (const artist of album.artists) {
      const value = normalizedArtistName(artist.name);
      if (value && !names.has(value)) names.set(value, artist.name.trim());
    }
  }
  return [...names].map(([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label));
}

export function filterAlbums(albums: readonly MusicItem[], filters: AlbumFilters): MusicItem[] {
  const terms = normalizedArtistName(filters.query).split(' ').filter(Boolean);
  const filtered = albums.filter((album) => {
    if (filters.artist && !album.artists.some((a) => normalizedArtistName(a.name) === filters.artist)) return false;
    // Release type is supplied in YouTube's subtitle, e.g. "EP • Artist • 2024".
    // Leave releases with no stated type out of a specific type filter.
    const releaseType = album.subtitle.split(/[•·]/)[0]?.trim().toLowerCase();
    if (filters.releaseType !== 'all' && releaseType !== filters.releaseType) return false;
    const text = normalizedArtistName([album.title, ...album.artists.map((a) => a.name), album.subtitle].join(' '));
    return terms.every((term) => text.includes(term));
  });
  const compareTitle = (a: MusicItem, b: MusicItem) => a.title.localeCompare(b.title, undefined, { sensitivity: 'base', numeric: true });
  if (filters.sort === 'title') filtered.sort(compareTitle);
  if (filters.sort === 'artist') {
    filtered.sort((a, b) =>
      (a.artists[0]?.name ?? '').localeCompare(b.artists[0]?.name ?? '', undefined, { sensitivity: 'base' }) || compareTitle(a, b),
    );
  }
  return filtered;
}
