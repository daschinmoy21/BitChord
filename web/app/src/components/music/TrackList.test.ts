// @vitest-environment happy-dom
import { act, createElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Track } from '@/data/music/types';
import { TrackList } from './TrackList';

vi.mock('@/state/player', () => ({ usePlayer: (select: (state: unknown) => unknown) => select({ nowPlaying: null, status: 'idle', togglePlay: vi.fn() }) }));
vi.mock('./play', () => ({ playList: vi.fn(), playTrack: vi.fn() }));
vi.mock('./TrackMenu', () => ({ TrackMenu: () => null }));
vi.mock('@/components/PrefetchLink', () => ({ PrefetchLink: ({ children }: { children: string }) => createElement('span', null, children) }));

let container: HTMLDivElement;
let root: Root;
let observer: IntersectionObserverCallback;
let observe: ReturnType<typeof vi.fn>;
let disconnect: ReturnType<typeof vi.fn>;
let constructions: number;
const tracks: Track[] = [{ videoId: 'first', title: 'First', artists: [], album: null, durationSec: 180, explicit: false, thumbnail: null, index: 1, plays: null }];
const render = (onNearEnd: () => void, items = tracks) => act(async () => root.render(createElement(TrackList, { tracks: items, variant: 'album', onNearEnd })));
const intersect = (visible: boolean) => act(async () => observer([{ isIntersecting: visible } as IntersectionObserverEntry], {} as IntersectionObserver));

beforeEach(() => {
  Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
  constructions = 0;
  observe = vi.fn();
  disconnect = vi.fn();
  vi.stubGlobal('IntersectionObserver', class {
    constructor(callback: IntersectionObserverCallback) { observer = callback; constructions++; }
    observe = observe;
    disconnect = disconnect;
  });
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});
afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

describe('short track-list pagination', () => {
  it('waits until the end of the list is visible before fetching', async () => {
    const load = vi.fn();
    await render(load);
    expect(load).not.toHaveBeenCalled();
    expect(observe).toHaveBeenCalledTimes(1);
    await intersect(false);
    expect(load).not.toHaveBeenCalled();
    await intersect(true);
    expect(load).toHaveBeenCalledTimes(1);
  });

  it('uses the latest callback without refetching or restarting observation on parent renders', async () => {
    const first = vi.fn();
    const latest = vi.fn();
    await render(first);
    await intersect(true);
    await render(latest);
    expect(constructions).toBe(1);
    expect(latest).not.toHaveBeenCalled();
    await intersect(true);
    expect(first).toHaveBeenCalledTimes(1);
    expect(latest).toHaveBeenCalledTimes(1);
    await render(latest, [...tracks, { ...tracks[0]!, videoId: 'second' }]);
    expect(disconnect).toHaveBeenCalledTimes(1);
    expect(constructions).toBe(2);
    expect(latest).toHaveBeenCalledTimes(1);
  });
});
