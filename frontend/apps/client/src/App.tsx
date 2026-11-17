import { BellRing, FileSearch, Gauge, Wallet } from 'lucide-react';
import { Alert, Login, apiBase, useHashRoute, useSession } from '@meterflow/shared';
import { Layout, NAV } from './Layout';
import { HomePage } from './pages/Home';
import { UsagePage } from './pages/Usage';
import { InvoicesPage } from './pages/Invoices';
import { BudgetPage } from './pages/Budget';
import { AccountPage } from './pages/Account';
import { NotificationsPage } from './pages/Notifications';

const ADMIN_URL = import.meta.env.VITE_ADMIN_URL || 'http://localhost:5173';
const LOCAL_DEMO = !apiBase();

export default function App() {
  const { session, login, logout, expired } = useSession('client');
  const { page, param, navigate } = useHashRoute('home');

  if (!session) {
    return (
      <>
        {expired && (
          <div className="session-banner">
            <Alert tone="warn">Your session expired. Please sign in again.</Alert>
          </div>
        )}
        <Login
          expectedRole="CUSTOMER"
          appTitle="Know what you’ll pay before the invoice arrives."
          appSubtitle="Follow your usage and spend live, set a budget, and see exactly how every invoice line was calculated."
          highlights={[
            { icon: <Gauge size={18} />, title: 'Live spend', text: 'Your month-to-date bill, refreshed every 30 seconds.' },
            { icon: <BellRing size={18} />, title: 'Budget alerts', text: 'Get notified at 50%, 80% and 100% of your budget.' },
            { icon: <FileSearch size={18} />, title: 'Explainable invoices', text: 'Trace each line to the usage events behind it.' },
            { icon: <Wallet size={18} />, title: 'Pay in one click', text: 'Settle open invoices and see your credit balance.' },
          ]}
          otherApp={{ label: 'admin console', url: ADMIN_URL }}
          demoAccount={LOCAL_DEMO ? { username: 'customer', password: 'meterflow-customer' } : undefined}
          onSignedIn={(s) => {
            login(s);
            navigate('home');
          }}
        />
      </>
    );
  }

  const customerId = session.customerId || session.username;
  const content = (() => {
    switch (page) {
      case 'usage':
        return <UsagePage customerId={customerId} />;
      case 'invoices':
        return <InvoicesPage customerId={customerId} selected={param} navigate={navigate} />;
      case 'budget':
        return <BudgetPage customerId={customerId} />;
      case 'account':
        return <AccountPage customerId={customerId} />;
      case 'notifications':
        return <NotificationsPage customerId={customerId} />;
      default:
        return <HomePage customerId={customerId} navigate={navigate} />;
    }
  })();

  return (
    <Layout session={session} customerId={customerId} page={NAV.some((n) => n.key === page) || page === 'notifications' ? page : 'home'} navigate={navigate} onLogout={logout}>
      {content}
    </Layout>
  );
}
