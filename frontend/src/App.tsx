import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { OrdersPage } from './pages/OrdersPage';
import { PlaceOrderPage } from './pages/PlaceOrderPage';
import { OrderWatchProvider } from './state/OrderWatchContext';
import { SettingsProvider } from './state/SettingsContext';
import { TransitionLogProvider } from './state/TransitionLogContext';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A dev console wants the truth now, not a cached answer. Refetch on focus is
      // off because the poll already covers it and duplicate requests muddy the log.
      refetchOnWindowFocus: false,
      retry: false,
      staleTime: 0,
    },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SettingsProvider>
        <TransitionLogProvider>
          {/* Above the router: the watcher must keep observing across navigation. */}
          <OrderWatchProvider>
            <BrowserRouter>
              <Routes>
                <Route element={<AppLayout />}>
                  <Route index element={<PlaceOrderPage />} />
                  <Route path="orders" element={<OrdersPage />} />
                  <Route path="orders/:orderId" element={<OrdersPage />} />
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Route>
              </Routes>
            </BrowserRouter>
          </OrderWatchProvider>
        </TransitionLogProvider>
      </SettingsProvider>
    </QueryClientProvider>
  );
}
