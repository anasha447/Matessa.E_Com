const assurances = [
  { strong: "Free shipping", text: "orders over $99" },
  { strong: "30-day returns", text: "no questions" },
  { strong: "DOT & ECE", text: "certified shells" },
];

export function AssuranceStrip() {
  return (
    <section className="assure" aria-label="Buying assurances">
      {assurances.map(({ strong, text }) => (
        <div key={strong}>
          <strong>{strong}</strong>
          {text}
        </div>
      ))}
    </section>
  );
}
