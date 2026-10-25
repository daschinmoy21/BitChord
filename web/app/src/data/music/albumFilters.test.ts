import { describe, expect, it } from 'vitest';
import { albumArtists, DEFAULT_ALBUM_FILTERS, filterAlbums } from './albumFilters';
import type { MusicItem } from './types';

const album = (title: string, names: string[], subtitle = 'Album'): MusicItem => ({
  kind: 'album', id: title, title, subtitle, artists: names.map((name) => ({ name, id: null })),
  album: null, thumbnail: null, wide: false, durationSec: null, explicit: false,
});
const releases = [
  album('Renaissance', ['Beyoncé'], 'Album • Beyoncé • 2022'),
  album('A Collaboration', ['Other Artist', 'Beyoncé'], 'Single • Other Artist & Beyoncé'),
  album('An EP', ['Another Artist'], 'EP • Another Artist'),
  album('Unknown release', [], ''),
];

describe('album library filters', () => {
  it('searches titles, all credited artists and metadata without case or accents', () => {
    expect(filterAlbums(releases, { ...DEFAULT_ALBUM_FILTERS, query: '  BEYONCE renaissance ' })).toEqual([releases[0]]);
    expect(filterAlbums(releases, { ...DEFAULT_ALBUM_FILTERS, query: 'beyonce' })).toEqual(releases.slice(0, 2));
    expect(filterAlbums(releases, { ...DEFAULT_ALBUM_FILTERS, query: '2022' })).toEqual([releases[0]]);
    expect(filterAlbums(releases, { ...DEFAULT_ALBUM_FILTERS, query: 'missing' })).toEqual([]);
  });

  it('combines an exact artist filter with search and release type', () => {
    expect(filterAlbums(releases, { ...DEFAULT_ALBUM_FILTERS, artist: 'beyonce', releaseType: 'single', query: 'collaboration' })).toEqual([releases[1]]);
    expect(filterAlbums(releases, { ...DEFAULT_ALBUM_FILTERS, artist: 'artist' })).toEqual([]);
    expect(filterAlbums(releases, { ...DEFAULT_ALBUM_FILTERS, releaseType: 'ep' })).toEqual([releases[2]]);
    expect(filterAlbums(releases, { ...DEFAULT_ALBUM_FILTERS, releaseType: 'album' })).toEqual([releases[0]]);
  });

  it('deduplicates artist options and omits blank names', () => {
    const artists = albumArtists([...releases, album('Duplicate', [' BEYONCÉ ', '', '  '])]);
    expect(artists.map((a) => a.label)).toEqual(['Another Artist', 'Beyoncé', 'Other Artist']);
    expect(artists.find((a) => a.label === 'Beyoncé')?.value).toBe('beyonce');
  });

  it('preserves library order by default and sorts a copy by title or artist', () => {
    const source = [album('Zebra', ['A']), album('Apple', ['Z']), album('Another', ['A'])];
    expect(filterAlbums(source, DEFAULT_ALBUM_FILTERS)).toEqual(source);
    expect(filterAlbums(source, { ...DEFAULT_ALBUM_FILTERS, sort: 'title' }).map((a) => a.title)).toEqual(['Another', 'Apple', 'Zebra']);
    expect(filterAlbums(source, { ...DEFAULT_ALBUM_FILTERS, sort: 'artist' }).map((a) => a.title)).toEqual(['Another', 'Zebra', 'Apple']);
    expect(source.map((a) => a.title)).toEqual(['Zebra', 'Apple', 'Another']);
  });
});
