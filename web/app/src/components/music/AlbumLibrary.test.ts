// @vitest-environment happy-dom
import { act, createElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AlbumLibrary } from './AlbumLibrary';
import type { MusicItem } from '@/data/music/types';

vi.mock('./Lockups', () => ({ SquareLockup: ({ item }: { item: MusicItem }) => createElement('span', null, item.title) }));

const album = (title: string): MusicItem => ({
  kind: 'album', id: title, title, subtitle: 'Album • Artist', artists: [{ name: 'Artist', id: null }],
  album: null, thumbnail: null, wide: false, durationSec: null, explicit: false,
});

let container: HTMLDivElement;
let root: Root;
const onLoadMore = vi.fn();
const props = { albums: [album('First page')], hasNextPage: true, fetching: false, loadMoreFailed: false, onLoadMore };
const render = (overrides: Partial<typeof props> = {}) => act(async () => root.render(createElement(AlbumLibrary, { ...props, ...overrides })));
const search = (value: string) => act(async () => {
  const input = container.querySelector('input')!;
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value);
  input.dispatchEvent(new Event('input', { bubbles: true }));
});

beforeEach(() => {
  Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
  onLoadMore.mockClear();
});
afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
});

describe('album library pagination', () => {
  it('finds matches on later pages and reset restores the full grid', async () => {
    await render();
    expect(onLoadMore).not.toHaveBeenCalled();
    await search('Later');
    expect(onLoadMore).toHaveBeenCalledTimes(1);
    expect(container.textContent).toContain('Searching your library');
    expect(container.textContent).not.toContain('No matching albums');
    await render({ fetching: true });
    expect(onLoadMore).toHaveBeenCalledTimes(1);
    await render({ albums: [...props.albums, album('Later page')], hasNextPage: false });
    expect(container.querySelector('ul')?.textContent).toBe('Later page');
    expect(container.textContent).toContain('1 album');
    const reset = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Reset')!;
    await act(async () => reset.click());
    expect(container.querySelectorAll('li')).toHaveLength(2);
    expect(container.querySelector('input')?.value).toBe('');
  });

  it('stops at a continuation error and avoids claiming the whole library has no matches', async () => {
    await render({ loadMoreFailed: true });
    await search('Missing');
    expect(onLoadMore).not.toHaveBeenCalled();
    expect(container.textContent).toContain('No matching albums yet');
    await render({ hasNextPage: false });
    expect(container.textContent).toContain('No matching albums');
    expect(container.textContent).not.toContain('No matching albums yet');
  });

  it('loads the full library for alphabetical sorting and applies menu selections', async () => {
    await render();
    const sort = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Library order')!;
    await act(async () => sort.click());
    const titleSort = [...document.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]')].find((b) => b.textContent === 'Album title')!;
    await act(async () => titleSort.click());
    expect(onLoadMore).toHaveBeenCalledTimes(1);
    await render({ albums: [album('Zebra'), album('Apple')], hasNextPage: false });
    expect([...container.querySelectorAll('li')].map((li) => li.textContent)).toEqual(['Apple', 'Zebra']);
  });
});
