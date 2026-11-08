import Link from "next/link";
import { Shell } from "@/components/layout/Shell";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { ContactForm } from "@/components/contact/ContactForm";

export const metadata = { title: "Contact — Adventure Dose" };

const Chevron = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="m6 9 6 6 6-6" />
  </svg>
);

const faqs = [
  {
    group: "Orders & shipping",
    open: true,
    items: [
      ["Where is my order?", "Open My orders and tap any order — you'll see its live status timeline and tracking number the moment it ships."],
      ["How long does shipping take?", "Standard is 3–5 business days and free over $99. Express is 1–2 business days for a flat $15."],
      ["Can I cancel an order?", "Yes — any time before it ships, from the order's detail page. Once it's shipped, use return instead."],
    ],
  },
  {
    group: "Returns, exchange & refund",
    open: false,
    items: [
      ["What's the return window?", "30 days from delivery, no questions asked. Start it from the order's detail page."],
      ["When do I get my refund?", "Within 5–7 business days of the return passing inspection, to the original payment method."],
      ["Can I exchange one-of-one artwork?", "Returns yes, exchanges no — there's literally only one. When it's gone, it's gone."],
    ],
  },
  {
    group: "Helmets & sizing",
    open: false,
    items: [
      ["How do I find my helmet size?", "Measure around your head just above the eyebrows and check the size chart on any helmet page. Between sizes? Go smaller — liners break in."],
      ["Are the painted helmets street-legal?", "Yes. Every shell is DOT & ECE 22.06 certified first; the art goes on after. Safety before story — always."],
    ],
  },
];

export default function ContactPage() {
  const content = (
    <>
      <section className="orders-banner">
        <h1>Manage your orders easily</h1>
        <p>Track, return, exchange or cancel your orders — hassle-free.</p>
        <Link className="btn btn-primary" href="/orders">
          My orders
        </Link>
      </section>

      <section className="query-band">
        <div className="wing" />
        <h2>
          What can we help <span className="hl">you with?</span>
        </h2>
        <ContactForm />
      </section>

      <div className="section-heading">
        <h2>FAQs</h2>
      </div>
      {faqs.map((g) => (
        <details className="faq-group" key={g.group} open={g.open}>
          <summary>
            {g.group}
            <Chevron />
          </summary>
          {g.items.map(([q, a]) => (
            <details className="faq-q" key={q}>
              <summary>{q}</summary>
              <p className="a">{a}</p>
            </details>
          ))}
        </details>
      ))}

      <div className="section-heading channels-head">
        <h2>Reach us</h2>
      </div>
      <div className="row-group channels">
        <a className="row-item" href="mailto:ride@adventuredose.com">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M3 5h18v14H3z" />
            <path d="m3 7 9 6 9-6" />
          </svg>
          <span className="grow">ride@adventuredose.com</span>
          <span className="meta">24h reply</span>
        </a>
        <a className="row-item" href="#">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 8.6 8.6 0 0 1-3.7-.8L3 20l1-5a8.4 8.4 0 1 1 17-3.5Z" />
          </svg>
          <span className="grow">Live chat</span>
          <span className="meta">9am – 6pm MT</span>
        </a>
      </div>
    </>
  );

  return (
    <>
      <div className="lg:hidden">
        <Shell>{content}</Shell>
      </div>
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap" style={{ maxWidth: 760 }}>
          {content}
        </div>
        <DesktopFooter />
      </div>
    </>
  );
}
