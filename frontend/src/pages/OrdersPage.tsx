import { useNavigate, useParams } from 'react-router-dom';
import { describeError } from '../api/client';
import { POLL_MS, useOrders, useStock } from '../api/queries';
import { OrderDetail } from '../components/OrderDetail';
import { OrdersTable } from '../components/OrdersTable';
import { TransitionLog } from '../components/TransitionLog';
import { useSettings } from '../state/SettingsContext';

export function OrdersPage() {
  const { orderId } = useParams<{ orderId?: string }>();
  const navigate = useNavigate();
  const { settings } = useSettings();

  // Same query key as the watcher's, so this reads the shared cache rather than
  // issuing a second poll of its own.
  const { data, error } = useOrders(settings.polling);
  const { data: stock } = useStock();

  const orders = data?.content ?? [];

  return (
    <div className="page page-orders">
      <section className="card">
        <div className="card-head">
          <h2>Orders</h2>
          <span className="muted small">
            {error
              ? describeError(error)
              : `${data?.page?.totalElements ?? orders.length} total · ${
                  settings.polling ? `polling every ${POLL_MS}ms` : 'polling paused'
                }`}
          </span>
        </div>
        <OrdersTable
          orders={orders}
          selectedId={orderId}
          onSelect={(id) => navigate(`/orders/${id}`)}
        />
      </section>

      <section className="card">
        <div className="card-head">
          <h2>Selected order</h2>
        </div>
        {orderId ? (
          <OrderDetail orderId={orderId} />
        ) : (
          <p className="muted">Pick an order from the table to see its lines and payment attempt.</p>
        )}
      </section>

      <section className="card">
        <div className="card-head">
          <h2>Stock</h2>
          <span className="muted small">available = on hand − allocated, never stored</span>
        </div>
        {stock ? (
          <div className="table-scroll">
            <table className="orders-table">
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Description</th>
                  <th className="num">On hand</th>
                  <th className="num">Allocated</th>
                  <th className="num">Available</th>
                </tr>
              </thead>
              <tbody>
                {stock.map((item) => (
                  <tr key={item.sku}>
                    <td className="mono">{item.sku}</td>
                    <td>{item.description}</td>
                    <td className="num">{item.quantityOnHand}</td>
                    <td className="num">{item.quantityAllocated}</td>
                    <td className={`num${item.quantityAvailable <= 0 ? ' out-of-stock' : ''}`}>
                      {item.quantityAvailable}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="muted">Loading stock…</p>
        )}
      </section>

      <TransitionLog />
    </div>
  );
}
