import { useTransitionLog } from '../state/TransitionLogContext';
import { clockTime, humanDuration } from '../util/format';

export function TransitionLog() {
  const { entries, clear } = useTransitionLog();

  return (
    <section className="card">
      <div className="card-head">
        <h2>Observed transitions</h2>
        <button className="ghost small-btn" type="button" onClick={clear}>
          Clear
        </button>
      </div>

      <p className="muted small">
        What this browser saw change, and how long after the order was accepted. This is the
        eventual consistency window, measured. It keeps running while you are on the other page.
      </p>

      {entries.length === 0 ? (
        <p className="muted">Nothing observed yet.</p>
      ) : (
        <ol className="event-log">
          {entries.map((entry) => (
            <li key={entry.id} className={`ev-${entry.kind}`}>
              <span className="t">{clockTime(entry.at)}</span>
              <span className="msg">{entry.message}</span>
              {entry.elapsedMs != null && (
                <span className="elapsed">+{humanDuration(entry.elapsedMs)}</span>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
