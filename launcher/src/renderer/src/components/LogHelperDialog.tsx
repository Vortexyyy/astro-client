import { useEffect, useMemo, useRef, useState, type DragEvent } from 'react';
import type { LogAnalysis, LogErrorLine, LogFile, LogLineKind } from '@shared/types';
import { astro, errorText } from '../api';
import Icon from './Icon';

/** Same cap as the main process; anything longer is cut to its end before it is sent. */
const MAX_CHARS = 5 * 1024 * 1024;
const LINES_PAGE = 2000;

const KIND_LABEL: Record<LogLineKind, string> = {
  exception: 'Exception',
  'caused-by': 'Caused by',
  thread: 'Thread crash',
  fatal: 'Fatal',
  error: 'Error',
  description: 'Crash report'
};

const CONFIDENCE_LABEL = { high: 'Likely cause', medium: 'Possible cause', low: 'Best guess' } as const;
const CONFIDENCE_PILL = { high: 'High confidence', medium: 'Medium confidence', low: 'Low confidence' } as const;

/**
 * Log helper: paste or open a game log / crash report and get the cause in plain English.
 * Analysis happens in the main process on this PC; every string shown here comes back redacted.
 */
export default function LogHelperDialog({
  notify,
  onClose
}: {
  notify: (message: string, kind?: 'info' | 'error') => void;
  onClose: () => void;
}) {
  const [text, setText] = useState('');
  const [note, setNote] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [result, setResult] = useState<LogAnalysis | null>(null);
  const [view, setView] = useState<'errors' | 'all'>('errors');
  const [shown, setShown] = useState(LINES_PAGE);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const resultsRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const analyze = (source = text) => {
    if (busy) return;
    setBusy(true);
    astro.analyzeLog(source.length > MAX_CHARS ? source.slice(-MAX_CHARS) : source).then(
      (r) => {
        setResult(r);
        setView('errors');
        setShown(LINES_PAGE);
        setBusy(false);
        requestAnimationFrame(() => resultsRef.current?.scrollIntoView({ block: 'start' }));
      },
      (err) => {
        notify(errorText(err), 'error');
        setBusy(false);
      }
    );
  };

  const load = (task: () => Promise<LogFile | null>) =>
    task().then(
      (file) => {
        if (!file) return;
        setText(file.text);
        setNote(file.name === 'clipboard' ? file.note : [`Loaded ${file.name}.`, file.note].filter(Boolean).join(' '));
        if (file.text.trim()) analyze(file.text);
        else notify(file.name === 'clipboard' ? 'The clipboard has no text in it.' : 'That file is empty.', 'error');
      },
      (err) => notify(errorText(err), 'error')
    );

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer.files[0];
    if (!file) return;
    const path = astro.pathForFile(file);
    if (!/\.(log|txt)$/i.test(path)) return notify('Drop a .log or .txt file (a game log or crash report).', 'error');
    void load(() => astro.readLogFile(path));
  };

  const copy = (value: string, ok: string) =>
    navigator.clipboard.writeText(value).then(
      () => notify(ok),
      () => notify("Couldn't copy to the clipboard.", 'error')
    );

  const errorLines = useMemo(() => new Map((result?.errors ?? []).map((e) => [e.line, e])), [result]);
  const head = result?.findings[0] ?? null;
  const hasProblems = Boolean(result && (result.findings.length || result.errors.length));

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal log-helper" role="dialog" aria-modal="true" aria-labelledby="log-helper-title" aria-describedby="log-helper-desc">
        <header className="modal-header log-helper-head">
          <div className="row">
            <span className="confirm-icon" aria-hidden="true">
              <Icon name="sparkles" size={20} />
            </span>
            <div>
              <h3 id="log-helper-title">Log helper</h3>
              <p id="log-helper-desc" className="muted small-text">
                Paste a game log, launcher output or crash report and see what went wrong.
              </p>
            </div>
          </div>
          <button className="icon-button" aria-label="Close" onClick={onClose}>
            <Icon name="x" />
          </button>
        </header>

        <div className="log-helper-body">
          <div
            className={`log-drop ${dragging ? 'dragging' : ''}`}
            onDragOver={(e) => {
              e.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
          >
            <textarea
              ref={inputRef}
              className={`input code-input mono log-input ${result ? 'compact' : ''}`}
              placeholder={'Paste latest.log, a crash-…txt report or the Console output here, or drop a .log / .txt file.\n\nCtrl+Enter analyzes.'}
              spellCheck={false}
              value={text}
              onChange={(e) => {
                setText(e.target.value);
                setNote(null);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                  e.preventDefault();
                  analyze();
                }
              }}
              aria-label="Log text to analyze"
            />
            {dragging && <div className="log-drop-hint">Drop the log or crash report</div>}
          </div>
          <div className="log-input-actions">
            <button className="btn-ghost small" onClick={() => load(() => astro.readClipboardLog())} disabled={busy}>
              <Icon name="copy" size={13} /> Paste from clipboard
            </button>
            <button className="btn-ghost small" onClick={() => load(() => astro.openLogFile())} disabled={busy}>
              <Icon name="file" size={13} /> Open file…
            </button>
            {text && (
              <button
                className="btn-ghost small"
                onClick={() => {
                  setText('');
                  setNote(null);
                  setResult(null);
                  inputRef.current?.focus();
                }}
                disabled={busy}
              >
                Clear
              </button>
            )}
            <span className="grow muted small-text log-note">{note}</span>
            <button className="btn-primary" onClick={() => analyze()} disabled={busy || !text.trim()}>
              <Icon name="search" size={14} /> {busy ? 'Analyzing…' : 'Analyze'}
            </button>
          </div>

          {!result && (
            <ul className="log-hints" aria-label="Where to find logs">
              <li>
                <Icon name="file" size={15} />
                <span>
                  <strong>latest.log</strong> is in the profile's <em>logs</em> folder (Console → Open logs folder).
                </span>
              </li>
              <li>
                <Icon name="alert" size={15} />
                <span>
                  <strong>Crash reports</strong> are in the profile's <em>crash-reports</em> folder, named crash-date-time.txt.
                </span>
              </li>
              <li>
                <Icon name="terminal" size={15} />
                <span>
                  <strong>Console output</strong>: use Copy on the Console page, then Paste from clipboard here.
                </span>
              </li>
            </ul>
          )}

          {result && (
            <div className="log-results" ref={resultsRef} aria-live="polite">
              {result.truncated && (
                <p className="log-notice">
                  <Icon name="alert" size={14} /> {result.truncated}
                </p>
              )}

              {result.empty && <p className="log-message muted">There's nothing to analyze yet. Paste a log above, or open a file.</p>}

              {!result.empty && !hasProblems && (
                <div className={`card log-summary ${result.looksLikeLog ? 'clean' : 'low'}`}>
                  <p className="card-label">{result.looksLikeLog ? 'No errors found' : 'Nothing recognised'}</p>
                  <h3>{result.looksLikeLog ? 'This log looks healthy' : "This doesn't look like a Minecraft log"}</h3>
                  <p className="muted">
                    {result.looksLikeLog
                      ? `Checked ${result.totalLines.toLocaleString()} line(s) and found no errors, exceptions or known problems. If the game still misbehaves, paste the log from the session where it went wrong.`
                      : 'Paste latest.log (in the profile’s logs folder), a crash report (crash-reports folder) or the Console output.'}
                  </p>
                </div>
              )}

              {head && (
                <div className={`card log-summary ${head.confidence}`}>
                  <div className="log-summary-top">
                    <p className="card-label">{CONFIDENCE_LABEL[head.confidence]}</p>
                    <span className={`confidence-pill ${head.confidence}`}>{CONFIDENCE_PILL[head.confidence]}</span>
                  </div>
                  <h3>{head.title}</h3>
                  <p>{head.summary}</p>
                  {head.tips.length > 0 && (
                    <>
                      <p className="card-label log-fix-label">Suggested fix</p>
                      <ul className="crash-tips">
                        {head.tips.map((t) => (
                          <li key={t}>{t}</li>
                        ))}
                      </ul>
                    </>
                  )}
                  <Involved analysis={result} />
                  {result.topFrames.length > 0 && (
                    <details className="log-frames">
                      <summary>Top of the stack trace</summary>
                      <pre className="crash-evidence mono">{result.topFrames.join('\n')}</pre>
                    </details>
                  )}
                </div>
              )}

              {result.findings.length > 1 && (
                <section className="log-section">
                  <p className="card-label">Also found ({result.findings.length - 1})</p>
                  <ul className="log-findings">
                    {result.findings.slice(1).map((f) => (
                      <li key={f.title + f.summary}>
                        <strong>{f.title}</strong>
                        <span className={`confidence-pill ${f.confidence}`}>{CONFIDENCE_PILL[f.confidence]}</span>
                        <p className="muted">{f.summary}</p>
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              {!result.empty && result.lines.length > 0 && hasProblems && (
                <section className="log-section">
                  <div className="log-lines-head">
                    <p className="card-label">Errors in this log ({result.errors.length})</p>
                    <div className="level-toggles" role="group" aria-label="Lines to show">
                      <button className={`level error ${view === 'errors' ? 'on' : ''}`} aria-pressed={view === 'errors'} onClick={() => setView('errors')}>
                        Errors only <span>{result.errors.length}</span>
                      </button>
                      <button className={`level info ${view === 'all' ? 'on' : ''}`} aria-pressed={view === 'all'} onClick={() => setView('all')}>
                        All lines <span>{result.lines.length}</span>
                      </button>
                    </div>
                  </div>
                  <div className="log-lines mono" role="list">
                    {view === 'errors' && result.errors.length === 0 && (
                      <p className="console-empty muted">No error lines; the finding above came from the text around them.</p>
                    )}
                    {view === 'errors' && result.errors.map((e) => <ErrorRow key={e.line} e={e} onCopy={copy} />)}
                    {view === 'all' &&
                      result.lines.slice(0, shown).map((l) => {
                        const err = errorLines.get(l.n);
                        return err ? (
                          <ErrorRow key={l.n} e={err} onCopy={copy} />
                        ) : (
                          <div key={l.n} className="log-row" role="listitem">
                            <span className="log-num">{l.n}</span>
                            <span className="log-text">{l.text}</span>
                          </div>
                        );
                      })}
                  </div>
                  {view === 'all' && shown < result.lines.length && (
                    <button className="btn-ghost small log-more" onClick={() => setShown((n) => n + LINES_PAGE)}>
                      Show {Math.min(LINES_PAGE, result.lines.length - shown).toLocaleString()} more lines
                    </button>
                  )}
                </section>
              )}
            </div>
          )}
        </div>

        <footer className="modal-footer log-helper-foot">
          <span className="log-privacy muted small-text">
            <Icon name="check" size={13} /> Paths and tokens are hidden. Nothing leaves your PC.
          </span>
          <span className="grow" />
          <button
            className="btn-ghost small"
            disabled={!result || result.empty}
            onClick={() => result && copy(result.copyText, 'Summary copied (paths and tokens hidden)')}
          >
            <Icon name="copy" size={13} /> Copy summary
          </button>
          <button className="btn-ghost small" onClick={onClose}>
            Close
          </button>
        </footer>
      </div>
    </div>
  );
}

function ErrorRow({ e, onCopy }: { e: LogErrorLine; onCopy: (value: string, ok: string) => void }) {
  return (
    <div className={`log-row hit ${e.kind}`} role="listitem">
      <span className="log-num">{e.line}</span>
      <span className="log-kind">{KIND_LABEL[e.kind]}</span>
      <span className="log-text">{e.text}</span>
      <button className="icon-button log-copy" aria-label={`Copy line ${e.line}`} title="Copy line" onClick={() => onCopy(e.text.trim(), `Line ${e.line} copied`)}>
        <Icon name="copy" size={12} />
      </button>
    </div>
  );
}

function Involved({ analysis }: { analysis: LogAnalysis }) {
  const { mods, javaVersion, requiredJava, gpu } = analysis.involved;
  const chips: Array<{ label: string; value: string }> = [
    ...mods.map((m) => ({ label: 'Mod', value: m })),
    ...(requiredJava ? [{ label: 'Needs', value: requiredJava }] : []),
    ...(javaVersion ? [{ label: 'Using', value: javaVersion }] : []),
    ...(gpu ? [{ label: 'Graphics', value: gpu }] : [])
  ];
  if (!chips.length) return null;
  return (
    <div className="log-involved" aria-label="Involved">
      <p className="card-label log-fix-label">Involved</p>
      <div className="log-chips">
        {chips.map((c) => (
          <span key={c.label + c.value} className="log-chip">
            <em>{c.label}</em> {c.value}
          </span>
        ))}
      </div>
    </div>
  );
}
