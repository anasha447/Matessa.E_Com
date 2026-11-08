import { redirect } from "next/navigation";
import "./admin.css";
import { Sidebar } from "@/components/admin/Sidebar";
import { AdminTopbar } from "@/components/admin/Topbar";
import { getCurrentUser } from "@/lib/server/session";

export const dynamic = "force-dynamic";

/** Admin console shell. Guards the whole /admin tree: signed-out → login,
 *  customers → storefront. MANAGER sees Operate + Sell; ADMIN also sees People. */
export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const user = await getCurrentUser();
  if (!user) redirect("/login?next=/admin");

  const isAdmin = user.roles.includes("ADMIN");
  const isStaff = isAdmin || user.roles.includes("MANAGER");
  if (!isStaff) redirect("/");

  return (
    <div className="admin">
      <div className="adm-shell">
        <Sidebar isAdmin={isAdmin} />
        <div className="adm-main">
          <AdminTopbar firstName={user.firstName} lastName={user.lastName} isAdmin={isAdmin} />
          {children}
        </div>
      </div>
    </div>
  );
}
