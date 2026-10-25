import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { LyricsQuery } from './providers';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('./http', async (importOriginal) => ({ ...await importOriginal<typeof import('./http')>(), lyricsGet: get }));

const query: LyricsQuery = { videoId: 'video', title: 'Wanted Song', artist: 'Wanted Artist', durationMs: 180_000, album: null, isrc: null };
const script = '<script src="/_next/static/chunks/pages/_app-test.js"></script>';
// A fixture-only signing value discovered through the same runtime path.
const secretScript = `Buffer.from("${[...btoa('fixture-signing-value')].reverse().join('')}".split`;
const response = (body: unknown, status = 200) => JSON.stringify({ message: { header: { status_code: status }, body } });
const track = (overrides = {}) => ({ track_id: 1, track_name: query.title, artist_name: query.artist, track_length: 180, has_subtitles: 1, ...overrides });

beforeEach(() => {
  vi.resetModules();
  get.mockReset();
});

async function withTracks(tracks: unknown[]) {
  get.mockImplementation(async (raw: string) => {
    const url = new URL(raw);
    if (url.pathname === '/search') return script;
    if (url.pathname.endsWith('_app-test.js')) return secretScript;
    if (url.pathname.endsWith('/token.get')) return response({ user_token: 'fixture-token' });
    if (url.pathname.endsWith('/track.search')) return response({ track_list: tracks.map((track) => ({ track })) });
    if (url.pathname.endsWith('/track.subtitle.get')) return response({ subtitle: { subtitle_body: JSON.stringify([{ text: 'Wanted lyrics', time: { total: 1 } }]) } });
    throw new Error(`Unexpected fixture request: ${url.pathname}`);
  });
  return (await import('./providers')).musixmatch;
}

describe('Musixmatch lyrics lookup', () => {
  it('fails closed when runtime signing-value discovery fails', async () => {
    get.mockResolvedValue(null);
    const { musixmatch } = await import('./providers');
    expect(await musixmatch(query)).toBeNull();
    expect(get).toHaveBeenCalledTimes(1);
    expect(get.mock.calls[0]?.[0]).toBe('https://www.musixmatch.com/search');
  });

  it('fails closed when the discovered value cannot be decoded', async () => {
    get.mockResolvedValueOnce(script).mockResolvedValueOnce('Buffer.from("%%%".split');
    const { musixmatch } = await import('./providers');
    expect(await musixmatch(query)).toBeNull();
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('rejects unrelated hits even when their duration matches exactly', async () => {
    const musixmatch = await withTracks([track({ track_name: 'Unrelated', artist_name: 'Someone Else' })]);
    expect(await musixmatch(query)).toBeNull();
    expect(get.mock.calls.some(([url]) => String(url).includes('/track.subtitle.get'))).toBe(false);
  });

  it('does not treat blank metadata as title or artist agreement', async () => {
    const musixmatch = await withTracks([track({ track_name: '', artist_name: '' })]);
    expect(await musixmatch({ ...query, title: '', artist: '' })).toBeNull();
  });

  it('uses the strongest matching result and returns its timed lyrics', async () => {
    const musixmatch = await withTracks([track({ track_id: 2, track_name: 'Unrelated', artist_name: 'Someone Else' }), track()]);
    const result = await musixmatch(query);
    expect(result?.lines).toEqual(expect.arrayContaining([expect.objectContaining({ text: 'Wanted lyrics', timeMs: 1000 })]));
    const subtitleUrl = get.mock.calls.find(([url]) => String(url).includes('/track.subtitle.get'))?.[0];
    expect(new URL(subtitleUrl).searchParams.get('track_id')).toBe('1');
  });
});
