import Link from "next/link";
import { getCurrentUser } from "@/lib/server/session";
import { getDashboardData } from "@/lib/server/analytics";
import { formatMoney } from "@/lib/utils/money";
import { Donut, HBars, KpiCard, StatusBadge, type BarRow } from "@/components/admin/ui";
import { TrendChart } from "@/components/admin/TrendChart";
import { icons } from "@/components/admin/icons";

export const dynamic = "force-dynamic";

const PM_LABEL: Record<string, string> = { CARD: "Card", PAYPAL: "PayPal", COD: "Cash on delivery" };
const STATUS_COLOR: Record<string, string> = {
  PENDING: "#FF6B1A",
  PAID: "#E0A92E",
  SHIPPED: "#2E9CC4",
  DELIVERED: "#3F8F5B",
  CANCELLED: "#D24B3E",
};
const cap = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

export default async function AdminDashboardPage() {
  const user = await getCurrentUser();
  const isAdmin = user?.roles.includes("ADMIN") ?? false;
  const { sales, catalog, trend, topProducts, topCustomers, customers } = await getDashboardData(isAdmin);

  if (!sales || !catalog) {
    return (
      <div className="adm-content">
        <div className="empty-block">
          <div className="wing" />
          <h4>Couldn&apos;t load analytics</h4>
          <p>The analytics service didn&apos;t respond. Refresh, or try again shortly.</p>
        </div>
      </div>
    );
  }

  const statusRows: BarRow[] = Object.entries(sales.ordersByStatus).map(([k, v]) => ({
    label: cap(k),
    value: v ?? 0,
    color: STATUS_COLOR[k],
  }));
  const payRows: BarRow[] = sales.revenueByPaymentMethod.map((p, i) => ({
    label: PM_LABEL[p.paymentMethod] ?? p.paymentMethod,
    value: p.revenue,
    sub: `${p.orders} orders`,
    color: ["#FF6B1A", "#2E9CC4", "#33414F"][i % 3],
  }));

  return (
    <div className="adm-content">
      <div className="kpi-grid">
        <KpiCard accent label="Revenue · this month" value={sales.revenue.thisMonth} money delta={sales.revenue.monthOverMonthPct} sub="vs last month" />
        <KpiCard label="Revenue · this year" value={sales.revenue.thisYear} money />
        <KpiCard label="Revenue · all time" value={sales.revenue.allTime} money />
        <KpiCard label="Total orders" value={sales.totalOrders} sub={`AOV ${formatMoney(sales.averageOrderValue)}`} />
        <KpiCard label="Refunds" value={sales.refunds.amount} money sub={`${sales.refunds.count} refunds`} />
      </div>

      <div className="card">
        <div className="card-head">
          <h3>Ops queue</h3>
          <span className="sub">avg {sales.operations.avgHoursToShip}h to ship</span>
        </div>
        <div className="card-body">
          <div className="ops-queue">
            <Link className="ops-item" href="/admin/orders?status=PENDING">
              <span className="ops-n">{sales.operations.awaitingPayment}</span>
              <span className="ops-t">
                <b>Awaiting payment</b>
                <small>Pending orders to chase or cancel</small>
              </span>
              {icons.arrowR}
            </Link>
            <Link className="ops-item is-cool" href="/admin/orders?status=PAID">
              <span className="ops-n">{sales.operations.awaitingShipment}</span>
              <span className="ops-t">
                <b>Awaiting shipment</b>
                <small>Paid orders ready to pack and ship</small>
              </span>
              {icons.arrowR}
            </Link>
          </div>
        </div>
      </div>

      <div className="dash-row cols-3-2">
        <div className="card">
          <div className="card-head">
            <h3>Revenue trend</h3>
            <span className="sub">last 12 months</span>
          </div>
          <div className="card-body">
            <TrendChart data={trend} />
          </div>
        </div>
        <div className="card">
          <div className="card-head">
            <h3>Orders by status</h3>
            <span className="sub">{sales.totalOrders.toLocaleString()} total</span>
          </div>
          <div className="card-body">
            <HBars rows={statusRows} />
          </div>
        </div>
      </div>

      <div className="dash-row cols-1-1">
        <div className="card">
          <div className="card-head">
            <h3>Revenue by payment method</h3>
          </div>
          <div className="card-body">
            <HBars rows={payRows} money />
          </div>
        </div>
        <div className="card">
          <div className="card-head">
            <h3>Catalog health</h3>
            <Link href="/admin/catalog" style={{ fontSize: 13, fontWeight: 600 }}>
              Manage catalog
            </Link>
          </div>
          <div className="card-body">
            <div className="kpi-grid" style={{ gridTemplateColumns: "1fr 1fr", gap: 10 }}>
              <KpiCard label="Inventory value" value={catalog.inventory.totalValue} money />
              <KpiCard
                label="Active promos"
                value={catalog.promotions.activeCoupons + catalog.promotions.activeDiscounts}
                sub={`${catalog.promotions.activeCoupons} coupons · ${catalog.promotions.activeDiscounts} discounts`}
              />
            </div>
            <div className="stat-rows" style={{ marginTop: 12 }}>
              <div className="stat-row">
                <span className="lbl">Low stock variants</span>
                <StatusBadge value="LOW" label={`${catalog.inventory.lowStock} low`} />
              </div>
              <div className="stat-row">
                <span className="lbl">Out of stock</span>
                <StatusBadge value="OUT" label={`${catalog.inventory.outOfStock} out`} />
              </div>
              <div className="stat-row">
                <span className="lbl">Products · variants · categories</span>
                <span className="val mono" style={{ fontSize: 13 }}>
                  {catalog.counts.products} · {catalog.counts.variants} · {catalog.counts.categories}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className={`dash-row ${isAdmin ? "cols-1-1-1" : "cols-1-1"}`}>
        <div className="card">
          <div className="card-head">
            <h3>Top products</h3>
            <span className="sub">by revenue</span>
          </div>
          <div className="card-body">
            <div className="stat-rows">
              {topProducts.length === 0 ? (
                <p style={{ color: "var(--text-secondary)", fontSize: 13 }}>No sales yet.</p>
              ) : (
                topProducts.map((p, i) => (
                  <div className="stat-row" key={p.productVariantId}>
                    <span className="rank">{String(i + 1).padStart(2, "0")}</span>
                    <span className="lbl">
                      {p.productName}
                      <span className="mono" style={{ fontSize: 10.5, color: "var(--text-secondary)", marginLeft: 8 }}>
                        {p.unitsSold} sold
                      </span>
                    </span>
                    <span className="val">{formatMoney(p.revenue)}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
        <div className="card">
          <div className="card-head">
            <h3>Top customers</h3>
            {isAdmin && (
              <Link href="/admin/customers" style={{ fontSize: 13, fontWeight: 600 }}>
                Customer analytics
              </Link>
            )}
          </div>
          <div className="card-body">
            <div className="stat-rows">
              {topCustomers.length === 0 ? (
                <p style={{ color: "var(--text-secondary)", fontSize: 13 }}>No customers yet.</p>
              ) : (
                topCustomers.map((c, i) => (
                  <div className="stat-row" key={c.userId}>
                    <span className="rank">{String(i + 1).padStart(2, "0")}</span>
                    <span className="lbl">
                      Customer #{c.userId}
                      <span className="mono" style={{ fontSize: 10.5, color: "var(--text-secondary)", marginLeft: 8 }}>
                        {c.orders} orders
                      </span>
                    </span>
                    <span className="val">{formatMoney(c.totalSpent)}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
        {isAdmin && customers && (
          <div className="card">
            <div className="card-head">
              <h3>Customers</h3>
              <span className="sub">admin only</span>
            </div>
            <div className="card-body">
              <Donut
                size={148}
                centerValue={customers.totalUsers.toLocaleString()}
                centerLabel="users"
                segments={[
                  { label: "Customers", value: customers.usersByRole.CUSTOMER ?? 0, color: "#33414F" },
                  { label: "Managers", value: customers.usersByRole.MANAGER ?? 0, color: "#2E9CC4" },
                  { label: "Admins", value: customers.usersByRole.ADMIN ?? 0, color: "#FF6B1A" },
                ]}
              />
              <div className="stat-rows" style={{ marginTop: 8 }}>
                <div className="stat-row">
                  <span className="lbl">New this month</span>
                  <span className="val">{customers.newThisMonth}</span>
                </div>
                <div className="stat-row">
                  <span className="lbl">New this year</span>
                  <span className="val">{customers.newThisYear}</span>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
