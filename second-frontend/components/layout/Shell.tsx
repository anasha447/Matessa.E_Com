import type { ReactNode } from "react";
import { TopBar } from "./TopBar";
import { TabBar } from "./TabBar";
import { Footer } from "./Footer";

/** Mobile app shell: 430px-max column with sticky top bar and fixed bottom tab bar.
 *  `data-quickadd="off"` matches the design (quick-add buttons hidden by default). */
export function Shell({ children, footer = true }: { children: ReactNode; footer?: boolean }) {
  return (
    <div className="shell" data-quickadd="off">
      <TopBar />
      {children}
      {footer && <Footer />}
      <TabBar />
    </div>
  );
}
