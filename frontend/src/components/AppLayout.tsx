import { NavLink, Outlet } from 'react-router-dom';
import { useOrders } from '../api/queries';
import { useSettings } from '../state/SettingsContext';

function ApiStatusPill() {
  const { settings } = useSettings();
  const { isPending, isError, isSuccess } = useOrders(settings.polling);

  if (!settings.polling) return <span className="pill pill-unknown">paused</span>;
  if (isError) return <span className="pill pill-down">api unreachable</span>;
  if (isSuccess) return <span className="pill pill-ok">api up</span>;
  if (isPending) return <span className="pill pill-unknown">connecting…</span>;
  return <span className="pill pill-unknown">idle</span>;
}

export function AppLayout() {
  const { settings, update } = useSettings();

  return (
    <>
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">📦</span>
          <div>
            <h1>orders-api</h1>
            <p className="brand-sub">local dev console</p>
          </div>
        </div>

        <nav className="nav">
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
            Place order
          </NavLink>
          <NavLink to="/orders" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
            Orders
          </NavLink>
        </nav>

        <div className="topbar-right">
          <ApiStatusPill />
          <label className="poll-toggle">
            <input
              type="checkbox"
              checked={settings.polling}
              onChange={(event) => update({ polling: event.target.checked })}
            />
            <span>live poll</span>
          </label>
        </div>
      </header>

      <main>
        <Outlet />
      </main>
    </>
  );
}
