import { money } from "../api";

const CADENCE = { WEEKLY: "Weekly", BIWEEKLY: "Every 2 weeks", MONTHLY: "Monthly", QUARTERLY: "Quarterly", ANNUAL: "Yearly" };

export default function RecurringTable({ report }) {
  const subs = report.payments.filter((p) => p.subscription);
  return (
    <section className="card">
      <h2>
        Recurring payments
        <span className="muted small"> {report.payments.length} found, {subs.length} subscriptions (as of {report.asOf})</span>
      </h2>
      {report.payments.length === 0 ? (
        <p className="muted">Nothing recurring detected yet. It takes at least three payments to spot a pattern.</p>
      ) : (
        <table>
          <thead>
            <tr><th>Merchant</th><th>Category</th><th>Cadence</th><th className="num">Amount</th><th>Payments</th><th>Next expected</th><th /></tr>
          </thead>
          <tbody>
            {report.payments.map((p) => (
              <tr key={p.merchant} className={p.active ? "" : "inactive"}>
                <td>{p.merchant}</td>
                <td>{p.category}</td>
                <td>{CADENCE[p.cadence]}</td>
                <td className="num">{money(p.typicalAmount)}</td>
                <td>{p.occurrences}</td>
                <td>{p.active ? p.nextExpected : <span className="muted">stopped?</span>}</td>
                <td>{p.subscription && <span className="badge">subscription</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
