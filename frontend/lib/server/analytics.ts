// Server-side dashboard analytics. All reads are staff/admin-only, so they go through
// serverGet (cookie session). Customer analytics is ADMIN-only — managers get null.
import { serverGet } from "./session";
import type {
  CatalogAnalyticsResponse,
  CustomerAnalyticsResponse,
  RevenueTrendPoint,
  SalesAnalyticsResponse,
  TopCustomerResponse,
  TopProductResponse,
} from "@/lib/api";

export interface DashboardData {
  sales: SalesAnalyticsResponse | null;
  catalog: CatalogAnalyticsResponse | null;
  trend: RevenueTrendPoint[];
  topProducts: TopProductResponse[];
  topCustomers: TopCustomerResponse[];
  customers: CustomerAnalyticsResponse | null;
}

export async function getDashboardData(isAdmin: boolean): Promise<DashboardData> {
  const [sales, catalog, trend, topProducts, topCustomers, customers] = await Promise.all([
    serverGet<SalesAnalyticsResponse>("/api/analytics/sales"),
    serverGet<CatalogAnalyticsResponse>("/api/analytics/catalog"),
    serverGet<RevenueTrendPoint[]>("/api/analytics/sales/revenue-trend?months=12"),
    serverGet<TopProductResponse[]>("/api/analytics/sales/top-products?limit=5"),
    serverGet<TopCustomerResponse[]>("/api/analytics/sales/top-customers?limit=5"),
    isAdmin ? serverGet<CustomerAnalyticsResponse>("/api/analytics/customers") : Promise.resolve(null),
  ]);

  return {
    sales,
    catalog,
    trend: trend ?? [],
    topProducts: topProducts ?? [],
    topCustomers: topCustomers ?? [],
    customers,
  };
}
