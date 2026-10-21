import { useEffect, useState } from "react";
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { api, money } from "../api";
import RecurringTable from "./RecurringTable";

const COLORS = ["#2563eb", "#16a34a", "#ea580c", "#9333ea", "#0891b2", "#dc2626", "#ca8a04", "#4f46e5",
  "#db2777", "#65a30d", "#0d9488", "#78716c", "#7c3aed", "#b45309"];

export default function Dashboard({ version }) {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [summary, setSummary] = useState(null);
  const [recurring, setRecurring] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    setError(null);
    api.summary(from, to).then(setSummary).catch((e) => setError(e.message));
  }, [from, to, version]);

  useEffect(() => {
    api.recurring().then(setRecurring).catch((e) => setError(e.message));
  }, [version]);

  if (error) return <p className="error">{error}</p>;
  if (!summary) return <p>Loading...</p>;
  if (summary.transactionCount === 0 && !from && !to) {
    return <p className="empty">No transactions yet. Import a bank CSV to get started.</p>;
  }

  return (
    <>
      <section className="filters">
        <label>From <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label>To <input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        {(from || to) && <button onClick={() => { setFrom(""); setTo(""); }}>All time</button>}
      </section>

      <section className="tiles">
        <Tile label="Income" value={money(summary.income)} className="pos" />
        <Tile label="Spending" value={money(summary.spending)} className="neg" />
        <Tile label="Net" value={money(summary.net)} className={summary.net >= 0 ? "pos" : "neg"} />
        <Tile label="Subscriptions / month" value={recurring ? money(recurring.monthlySubscriptionCost) : "--"} />
      </section>

      <section className="grid">
        <div className="card wide">
          <h2>Income vs. spending by month</h2>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={summary.monthly}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="month" />
              <YAxis tickFormatter={(v) => `$${(v / 1000).toFixed(1)}k`} />
              <Tooltip formatter={(v) => money(v)} />
              <Legend />
              <Bar dataKey="income" name="Income" fill="#16a34a" />
              <Bar dataKey="spending" name="Spending" fill="#dc2626" />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="card">
          <h2>Spending by category</h2>
          <ResponsiveContainer width="100%" height={280}>
            <PieChart>
              <Pie data={summary.spendingByCategory} dataKey="amount" nameKey="label" innerRadius={55} outerRadius={100}>
                {summary.spendingByCategory.map((c, i) => <Cell key={c.category} fill={COLORS[i % COLORS.length]} />)}
              </Pie>
              <Tooltip formatter={(v) => money(v)} />
            </PieChart>
          </ResponsiveContainer>
          <ul className="legend">
            {summary.spendingByCategory.map((c, i) => (
              <li key={c.category}>
                <span className="swatch" style={{ background: COLORS[i % COLORS.length] }} />
                {c.label}<span className="amt">{money(c.amount)}</span>
              </li>
            ))}
          </ul>
        </div>

        <div className="card">
          <h2>Top merchants</h2>
          <table>
            <tbody>
              {summary.topMerchants.map((m) => (
                <tr key={m.merchant}>
                  <td>{m.merchant}</td>
                  <td className="muted">{m.count}x</td>
                  <td className="num">{money(m.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {recurring && <RecurringTable report={recurring} />}
    </>
  );
}

function Tile({ label, value, className = "" }) {
  return (
    <div className="tile">
      <span className="label">{label}</span>
      <span className={`value ${className}`}>{value}</span>
    </div>
  );
}
