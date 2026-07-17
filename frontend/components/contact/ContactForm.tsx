"use client";

// Contact form (design: Contact Us.dc.html) — dark-green pattern-topped card. Email +
// enquiry-type dropdown + message → api.contact.submit; success state replaces the form.
import { useState } from "react";
import { api, ApiError } from "@/lib/api";

const ENQUIRY_TYPES = [
  "Order & Shipping",
  "Product Question",
  "Brewing Help",
  "Wholesale & Partnerships",
  "Feedback",
  "Something Else",
];

export function ContactForm() {
  const [email, setEmail] = useState("");
  const [queryType, setQueryType] = useState(ENQUIRY_TYPES[0]);
  const [message, setMessage] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    if (!/^\S+@\S+\.\S+$/.test(email)) return setError("Please enter a valid email so we can reply.");
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

  const field = "w-full rounded-xl border border-gray-300 bg-white px-4 py-3 font-body outline-none focus:border-green";

  return (
    <div className="mx-auto w-full max-w-[620px] overflow-hidden rounded-[2rem] bg-white shadow-lg">
      {/* pattern header */}
      <div className="relative bg-darkgreen px-8 py-10 text-center">
        <div
          aria-hidden="true"
          className="absolute inset-0 opacity-15"
          style={{ backgroundImage: "url('/brand/matessa-pattern.webp')", backgroundSize: "300px", mixBlendMode: "screen" }}
        />
        <h1 className="relative font-heading text-2xl font-bold text-craemy md:text-3xl">Get in Touch with the Matessa Team</h1>
      </div>

      <div className="p-8">
        {sent ? (
          <div className="py-8 text-center">
            <div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-full bg-green text-2xl text-white">✓</div>
            <h2 className="font-heading text-xl font-bold text-darkgreen">Message Sent</h2>
            <p className="mt-2 font-body text-gray-600">
              Thank you for reaching out. We&apos;ll get back to you at <strong>{email}</strong> within 1–2 days.
            </p>
            <button
              type="button"
              onClick={() => {
                setSent(false);
                setMessage("");
              }}
              className="mt-6 rounded-full border-2 border-darkgreen px-6 py-2.5 font-body font-bold text-darkgreen transition-colors hover:border-orange hover:text-orange"
            >
              Send another message
            </button>
          </div>
        ) : (
          <div className="grid gap-4">
            <div>
              <label htmlFor="cemail" className="mb-1.5 block font-body text-sm font-bold text-darkgreen">
                Email
              </label>
              <input id="cemail" className={field} type="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div>
              <label htmlFor="ctype" className="mb-1.5 block font-body text-sm font-bold text-darkgreen">
                Enquiry type
              </label>
              <select id="ctype" className={field} value={queryType} onChange={(e) => setQueryType(e.target.value)}>
                {ENQUIRY_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="cmsg" className="mb-1.5 block font-body text-sm font-bold text-darkgreen">
                Message
              </label>
              <textarea id="cmsg" className={field} rows={5} placeholder="Tell us how we can help…" value={message} onChange={(e) => setMessage(e.target.value)} />
            </div>
            {error && <p className="font-body text-sm text-orange">{error}</p>}
            <button
              type="button"
              disabled={busy}
              onClick={submit}
              className="rounded-full bg-green py-3.5 font-body font-bold text-white transition-colors duration-300 hover:bg-orange disabled:opacity-40"
            >
              {busy ? "Sending…" : "Send Message"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
