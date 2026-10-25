import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Track } from '@/data/music/types';
import type { QueueEntry } from '@/playback/queue/timeline';
import type { PartyState } from './listenTogether';
import { EMPTY_PLAYBACK, EMPTY_QUEUE, type PartyTrack } from './models';

const mock = vi.hoisted(() => ({
  hooks: null as { onIntent: () => void } | null,
  player: { queue: [] as QueueEntry[], index: 0, status: 'paused' },
  audio: { currentTime: 5, duration: 180 },
  state: {} as PartyState,
  listeners: new Set<(state: PartyState) => void>(),
  outgoing: { setQueue: vi.fn(), setTrack: vi.fn(), play: vi.fn(), pause: vi.fn(), seek: vi.fn(), queueMove: vi.fn(), queueAdd: vi.fn() },
  incoming: { pause: vi.fn(), play: vi.fn(), seekTo: vi.fn(), setUpcoming: vi.fn(), load: vi.fn() },
}));
vi.mock('@/state/player', () => ({
  getAudio: () => mock.audio,
  usePlayer: { getState: () => mock.player },
  partyPlayer: mock.incoming,
  setPartyHooks: (hooks: typeof mock.hooks) => { mock.hooks = hooks; },
}));
vi.mock('./listenTogether', () => ({
  controlsLocked: () => false,
  msUntilStart: () => 0,
  partyPositionMs: () => mock.state.playback.positionMs,
  party: mock.outgoing,
  useParty: {
    getState: () => mock.state,
    setState: (patch: Partial<PartyState>) => { Object.assign(mock.state, patch); },
    subscribe: (listener: (state: PartyState) => void) => {
      mock.listeners.add(listener);
      return () => mock.listeners.delete(listener);
    },
  },
}));

const track: Track = { videoId: 'local', title: 'Local song', artists: [], album: null, durationSec: 180, explicit: false, thumbnail: null, index: null, plays: null };
const partyTrack: PartyTrack = { videoId: 'old', title: 'Previous song', artist: '', durationMs: 180_000, thumbnailUrl: null, fromAutoplay: false };
let stop: (() => void) | undefined;

beforeEach(() => {
  vi.useFakeTimers();
  vi.resetModules();
  vi.clearAllMocks();
  mock.listeners.clear();
  mock.player = { queue: [{ entryId: 'entry', track, tier: 'user', source: null }], index: 0, status: 'paused' };
  mock.audio = { currentTime: 5, duration: 180 };
  mock.state = {
    code: 'TEST', you: null, members: [], maxMembers: 5, hostOnlyControl: false,
    playback: { ...EMPTY_PLAYBACK, seq: 1, track: partyTrack },
    queue: { ...EMPTY_QUEUE, seq: 1, index: 0, items: [partyTrack] },
    connection: 'live', clockSynced: true, roundTripMs: 0, error: null, activity: [], awaitingStart: false,
  };
});
afterEach(() => {
  stop?.();
  stop = undefined;
  vi.useRealTimers();
});

describe('party sync sequencing', () => {
  it('coalesces rapid local intents and publishes the queue before its new current track', async () => {
    stop = (await import('./partySync')).startPartySync();
    mock.hooks!.onIntent();
    mock.hooks!.onIntent();
    vi.advanceTimersByTime(119);
    expect(mock.outgoing.setTrack).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(mock.outgoing.setQueue).toHaveBeenCalledTimes(1);
    expect(mock.outgoing.setTrack).toHaveBeenCalledTimes(1);
    expect(mock.outgoing.setQueue.mock.invocationCallOrder[0]!).toBeLessThan(mock.outgoing.setTrack.mock.invocationCallOrder[0]!);
    expect(mock.outgoing.setTrack).toHaveBeenCalledWith(expect.objectContaining({ videoId: 'local' }), 5000, false);
  });

  it('applies incoming pause/seek controls without publishing them back to the party', async () => {
    mock.state.playback = { ...mock.state.playback, track: { ...partyTrack, videoId: 'local' }, positionMs: 2000 };
    mock.state.queue.items = [mock.state.playback.track!];
    mock.player.status = 'playing';
    stop = (await import('./partySync')).startPartySync();
    for (const listener of mock.listeners) listener(mock.state);
    expect(mock.incoming.pause).toHaveBeenCalledTimes(1);
    expect(mock.incoming.seekTo).toHaveBeenCalledWith(2);
    for (const call of Object.values(mock.outgoing)) expect(call).not.toHaveBeenCalled();
  });
});
