"use client";

// Contact form (design: Contact Us.dc.html) — dark-green card, pattern header, cream
// labels + translucent fields, and a fully custom Matessa dropdown (native <select>
// options can't be re-themed off the OS blue highlight). Submits to api.contact.submit.
import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { ChevronRightIcon } from "@/components/ui/icons";

const ENQUIRY_TYPES = [
  "Order & Shipping",
  "Product Question",
  "Brewing Help",
  "Wholesale & Partnerships",
  "Feedback",
  "Something Else",
];

/** Matessa-branded dropdown (replaces native <select> so the option hover is our green,
 *  not the OS blue). Closes on outside click / Escape. */
function MatessaSelect({
  value,
  onChange,
  options,
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  options: string[];
  placeholder: string;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className="flex w-full items-center justify-between rounded-xl border border-craemy/25 bg-black/15 px-4 py-3 text-left font-body text-craemy outline-none transition-colors focus:border-yellow"
      >
        <span className={value ? "" : "text-craemy/50"}>{value || placeholder}</span>
        <span className={`text-craemy/70 transition-transform duration-200 ${open ? "rotate-90" : ""}`}>
          <ChevronRightIcon size={16} />
        </span>
      </button>
      {open && (
        <ul
          role="listbox"
          className="absolute z-20 mt-2 w-full overflow-hidden rounded-xl border border-craemy/20 bg-darkgreen shadow-2xl"
          style={{ boxShadow: "0 20px 40px rgba(0,0,0,0.4)" }}
        >
          {options.map((opt) => (
            <li key={opt}>
              <button
                type="button"
                role="option"
                aria-selected={opt === value}
                onClick={() => {
                  onChange(opt);
                  setOpen(false);
                }}
                className={`block w-full px-4 py-2.5 text-left font-body text-[15px] transition-colors ${
                  opt === value ? "bg-green text-white" : "text-craemy hover:bg-[#3E5F2D] hover:text-white"
                }`}
              >
                {opt}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function ContactForm() {
  const [email, setEmail] = useState("");
  const [queryType, setQueryType] = useState("");
  const [message, setMessage] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    if (!/^\S+@\S+\.\S+$/.test(email)) return setError("Please enter a valid email so we can reply.");
    if (!queryType) return setError("Please choose an enquiry type.");
    if (!message.trim()) return setError("Tell us how we can help.");
    setBusy(true);
    try {
      await api.contact.submit({ email: email.trim(), queryType, message: message.trim() });
      setSent(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Couldn't send your message. Please try again.");
    } finally {
      setBusy(false);
    }
  };

  const label = "mb-1.5 block font-body text-xs font-bold uppercase tracking-[0.14em] text-yellow";
  const field =
    "w-full rounded-xl border border-craemy/25 bg-black/15 px-4 py-3 font-body text-craemy outline-none transition-colors placeholder:text-craemy/50 focus:border-yellow";

  return (
    <div className="mx-auto w-full max-w-[620px] overflow-hidden rounded-[2rem] bg-darkgreen shadow-2xl">
      {/* pattern header */}
      <div className="relative px-8 py-12 text-center">
        <div
          aria-hidden="true"
          className="absolute inset-0"
          style={{
            backgroundImage: "url('/brand/matessa-pattern.webp')",
            backgroundSize: "260px",
            opacity: 0.22,
            mixBlendMode: "screen",
          }}
        />
        <h1 className="relative font-heading text-3xl font-bold text-craemy md:text-4xl">
          Get in Touch with the Matessa Team
        </h1>
      </div>

      <div className="px-8 pb-9">
        {sent ? (
          <div className="py-8 text-center">
            <div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-full bg-yellow text-2xl text-darkgreen">✓</div>
            <h2 className="font-heading text-xl font-bold text-craemy">Message Sent</h2>
            <p className="mt-2 font-body text-craemy/80">
              Thank you for reaching out. We&apos;ll get back to you at <strong className="text-white">{email}</strong> within 1–2 days.
            </p>
            <button
              type="button"
              onClick={() => {
                setSent(false);
                setMessage("");
              }}
              className="mt-6 rounded-full border-2 border-craemy/40 px-6 py-2.5 font-body font-bold text-craemy transition-colors hover:border-orange hover:text-orange"
            >
              Send another message
            </button>
          </div>
        ) : (
          <div className="grid gap-5">
            <div>
              <label htmlFor="cemail" className={label}>
                Email
              </label>
              <input id="cemail" className={field} type="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div>
              <span className={label}>Enquiry type</span>
              <MatessaSelect value={queryType} onChange={setQueryType} options={ENQUIRY_TYPES} placeholder="Choose a topic…" />
            </div>
            <div>
              <label htmlFor="cmsg" className={label}>
                Message
              </label>
              <textarea id="cmsg" className={field} rows={5} placeholder="Tell us how we can help…" value={message} onChange={(e) => setMessage(e.target.value)} />
            </div>
            {error && <p className="font-body text-sm text-orange">{error}</p>}
            <button
              type="button"
              disabled={busy}
              onClick={submit}
              className="rounded-full bg-craemy py-3.5 font-body font-bold text-darkgreen transition-colors duration-300 hover:bg-yellow disabled:opacity-50"
            >
              {busy ? "Sending…" : "Send Message"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
