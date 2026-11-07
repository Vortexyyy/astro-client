import { useEffect, useState } from 'react';
import type { ModSearchHit, ModSource } from '@shared/types';
import type { AppState } from '../App';
import { astro, errorText, formatCount } from '../api';
import ProfilePicker from '../components/ProfilePicker';
import Icon from '../components/Icon';

const PAGE_SIZE = 20;

export default function Browse({ app }: { app: AppState }) {
  const profile = app.profiles.find((p) => p.id === app.selectedId) ?? null;
  const mcVersion = profile?.mcVersion ?? '1.21.1';
  const [source, setSource] = useState<ModSource>('modrinth');
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<ModSearchHit[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [installing, setInstalling] = useState<Set<string>>(new Set());

  async function search(offset: number) {
    setLoading(true);
    setError(null);
    try {
      if (!profile) return;
      const result = await astro.searchMods({ profileId: profile.id, source, query, mcVersion, offset });
      setHits((h) => (offset === 0 ? result : [...h, ...result]));
      setHasMore(result.length === PAGE_SIZE);
    } catch (err) {
      if (offset === 0) setHits([]);
      setError(errorText(err));
    } finally {
      setLoading(false);
    }
  }

  // Debounced search whenever the query, source or target version changes.
  useEffect(() => {
    const t = window.setTimeout(() => void search(0), 300);
    return () => window.clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, source, mcVersion, profile?.id, profile?.loader]);

  async function install(hit: ModSearchHit) {
    if (!profile) return app.notify('Create a profile first.', 'error');
    setInstalling((s) => new Set(s).add(hit.id));
    try {
      const updated = await astro.installMod(profile.id, hit.source, hit.id);
      app.upsert(updated);
      app.notify(`Installed ${hit.title} into ${profile.name}`);
    } catch (err) {
      app.notify(errorText(err), 'error');
    } finally {
      setInstalling((s) => {
        const next = new Set(s);
        next.delete(hit.id);
        return next;
      });
    }
  }

  const isInstalled = (hit: ModSearchHit) =>
    profile?.mods.some((m) => m.source === hit.source && m.projectId === hit.id) ?? false;

  return (
    <section className="page">
      <header className="page-header">
        <div>
          <h2>Browse mods</h2>
          <p className="muted">
            {profile?.loader === 'quilt' ? 'Quilt and Fabric' : 'Fabric'} mods for Minecraft {mcVersion}
          </p>
        </div>
        <ProfilePicker profiles={app.profiles} value={app.selectedId} onChange={app.select} />
      </header>

      <div className="toolbar">
        <div className="segmented" role="tablist">
          {(['modrinth', 'curseforge'] as const).map((s) => (
            <button key={s} role="tab" aria-selected={source === s} className={source === s ? 'on' : ''} onClick={() => setSource(s)}>
              {s === 'modrinth' ? 'Modrinth' : 'CurseForge'}
            </button>
          ))}
        </div>
        <label className="search-field grow">
          <Icon name="search" />
          <input
            className="input"
            type="search"
            placeholder="Search mods…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search mods"
          />
        </label>
      </div>

      {profile?.loader === 'vanilla' && (
        <div className="callout">This is a vanilla profile. Pick a Fabric or Quilt profile to install mods.</div>
      )}
      {error && profile?.loader !== 'vanilla' && <div className="callout error">{error}</div>}

      <div className="mod-grid">
        {hits.map((hit) => (
          <article key={`${hit.source}:${hit.id}`} className="card mod-card">
            <div className="mod-head">
              {hit.iconUrl ? <img src={hit.iconUrl} alt="" loading="lazy" /> : <div className="mod-icon-fallback">{hit.title[0]}</div>}
              <div className="mod-title">
                <h3 title={hit.title}>{hit.title}</h3>
                <p className="mod-meta">by {hit.author}</p>
              </div>
            </div>
            <p className="mod-desc">{hit.description}</p>
            <footer className="mod-foot">
              <span className="mod-downloads" title={`${hit.downloads.toLocaleString()} downloads`}>
                <Icon name="download" size={13} />
                {formatCount(hit.downloads)}
              </span>
              {isInstalled(hit) && (
                <span className="pill pill-ok">
                  <Icon name="check" size={12} /> Installed
                </span>
              )}
              <button
                className={isInstalled(hit) ? 'btn-ghost small' : 'btn-primary small'}
                disabled={installing.has(hit.id) || !profile}
                onClick={() => install(hit)}
              >
                {installing.has(hit.id) ? 'Installing…' : isInstalled(hit) ? 'Reinstall' : 'Install'}
              </button>
            </footer>
          </article>
        ))}
      </div>

      {!loading && hits.length === 0 && !error && <p className="muted center">No mods found.</p>}
      {loading && <p className="muted center">Scanning the galaxy…</p>}
      {!loading && hasMore && (
        <button className="btn-ghost load-more" onClick={() => search(hits.length)}>
          Load more
        </button>
      )}
    </section>
  );
}
