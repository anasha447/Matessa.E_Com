"use client";

import { useState } from "react";
import { api, ApiError } from "@/lib/api";

const QUERIES = [
  "Where is my order?",
  "I want to return or exchange a product",
  "Amount deducted but no order confirmed",
  "I haven't received my refund",
  "Received incorrect product or size",
  "Received damaged product",
  "Helmet sizing help",
  "Something else",
];

/** Support query form. Submits to the commercial-service contact endpoint so staff can read
 *  and resolve it from the admin Contacts page. The email lets staff reply. */
export function ContactForm() {
  const [email, setEmail] = useState("");
  const [queryType, setQueryType] = useState("");
  const [message, setMessage] = useState("");
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    if (!/^\S+@\S+\.\S+$/.test(email)) {
      setError("Please enter a valid email so we can reply.");
      return;
    }
    if (!queryType) {
      setError("Please select what your query is about.");
      return;
    }
    if (!message.trim()) {
      setError("Please tell us a bit more so we can help.");
      return;
    }
    setSubmitting(true);
    try {
      await api.contact.submit({ email: email.trim(), queryType, message: message.trim() });
      setSent(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Couldn't send your message. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="query-card">
      <input
        className="text-input"
        type="email"
        placeholder="Your email"
        aria-label="Your email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        autoComplete="email"
        disabled={sent}
        style={{ width: "100%", marginBottom: 10 }}
      />
      <div className="select-wrap">
        <select
          aria-label="Select your query"
          value={queryType}
          onChange={(e) => setQueryType(e.target.value)}
          disabled={sent}
        >
          <option value="" disabled>
            Please select your query
          </option>
          {QUERIES.map((q) => (
            <option key={q}>{q}</option>
          ))}
        </select>
      </div>
      <textarea
        placeholder="Tell us a bit more (order number helps)…"
        aria-label="Message"
        value={message}
        onChange={(e) => setMessage(e.target.value)}
        disabled={sent}
      />
      {error && (
        <p role="alert" style={{ color: "#dc2626", fontSize: 13, margin: "0 0 4px" }}>
          {error}
        </p>
      )}
      <button
        className="btn btn-primary btn-block"
        type="button"
        onClick={submit}
        disabled={sent || submitting}
      >
        {sent ? "Sent ✓" : submitting ? "Sending…" : "Send"}
      </button>
      {sent && (
        <p className="ticket-ok">
          {"// Ticket opened — status: OPEN"}
          <br />
          We&apos;ll get back to you within 24h.
        </p>
      )}
    </div>
  );
}
