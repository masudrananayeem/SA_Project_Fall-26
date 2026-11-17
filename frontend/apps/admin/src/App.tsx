import { Activity, Boxes, CreditCard, Receipt } from 'lucide-react';
import { Alert, Login, apiBase, useHashRoute, useSession } from '@meterflow/shared';
import { Layout, NAV } from './Layout';
import { OverviewPage } from './pages/Overview';
import { CustomersPage } from './pages/Customers';
import { PlansPage } from './pages/Plans';
import { SubscriptionsPage } from './pages/Subscriptions';
import { UsagePage } from './pages/Usage';
import { BillingPage } from './pages/Billing';
import { InvoicesPage } from './pages/Invoices';
import { CreditsPage } from './pages/Credits';
import { DunningPage } from './pages/Dunning';
import { WebhooksPage } from './pages/Webhooks';

const CLIENT_URL = import.meta.env.VITE_CLIENT_URL || 'http://localhost:5174';
// The demo credentials are only offered when the app talks to a local backend (no VITE_API_URL).
const LOCAL_DEMO = !apiBase();

export default function App() {
  const { session, login, logout, expired } = useSession('admin');
  const { page, param, navigate } = useHashRoute('overview');

  if (!session) {
    return (
      <>
        {expired && (
          <div className="session-banner">
            <Alert tone="warn">Your session expired. Please sign in again.</Alert>
          </div>
        )}
        <Login
          expectedRole="ADMIN"
          appTitle="Run usage-based billing with confidence."
          appSubtitle="Publish versioned pricing, meter usage, run idempotent billing and recover failed payments — from one console."
          highlights={[
            { icon: <Boxes size={18} />, title: 'Versioned pricing', text: 'Six charge types, tiers and plan versions with safe migration.' },
            { icon: <Activity size={18} />, title: 'Metering at scale', text: 'Idempotent batch ingestion with late-usage handling.' },
            { icon: <Receipt size={18} />, title: 'Explainable invoices', text: 'Trace every line back to its events and price rule.' },
            { icon: <CreditCard size={18} />, title: 'Dunning built in', text: 'Day 1 / 3 / 7 retries, past-due grace and reactivation.' },
          ]}
          otherApp={{ label: 'customer portal', url: CLIENT_URL }}
          demoAccount={LOCAL_DEMO ? { username: 'admin', password: 'meterflow-admin' } : undefined}
          onSignedIn={(s) => {
            login(s);
            navigate('overview');
          }}
        />
      </>
    );
  }

  const content = (() => {
    switch (page) {
      case 'customers':
        return <CustomersPage selected={param} navigate={navigate} />;
      case 'plans':
        return <PlansPage />;
      case 'subscriptions':
        return <SubscriptionsPage selected={param} navigate={navigate} />;
      case 'usage':
        return <UsagePage />;
      case 'billing':
        return <BillingPage navigate={navigate} />;
      case 'invoices':
        return <InvoicesPage selected={param} navigate={navigate} />;
      case 'credits':
        return <CreditsPage />;
      case 'dunning':
        return <DunningPage navigate={navigate} />;
      case 'webhooks':
        return <WebhooksPage />;
      default:
        return <OverviewPage navigate={navigate} />;
    }
  })();

  return (
    <Layout session={session} page={NAV.some((n) => n.key === page) ? page : 'overview'} navigate={navigate} onLogout={logout} clientUrl={CLIENT_URL}>
      {content}
    </Layout>
  );
}

