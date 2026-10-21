import { useEffect, useState } from "react";
import { api, money } from "../api";

const PAGE_SIZE = 50;

export default function Transactions({ categories, version, onChange }) {
  const [filters, setFilters] = useState({ q: "", category: "", from: "", to: "" });
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [applyToMerchant, setApplyToMerchant] = useState(true);
  const [message, setMessage] = useState(null);

  useEffect(() => {
    const t = setTimeout(() => {
      api.transactions({ ...filters, page, size: PAGE_SIZE }).then(setData).catch((e) => setMessage(e.message));
    }, 200); // debounce typing in the search box
    return () => clearTimeout(t);
  }, [filters, page, version]);

  const update = (key) => (e) => {
    setPage(0);
    setFilters({ ...filters, [key]: e.target.value });
  };

  const recategorize = async (tx, category) => {
    try {
      const res = await api.recategorize(tx.id, category, applyToMerchant);
      setMessage(
        res.otherTransactionsUpdated > 0
          ? `${tx.merchant} set to ${category}; ${res.otherTransactionsUpdated} other transactions updated`
          : `Set to ${category}`
      );
      onChange();
    } catch (e) {
      setMessage(e.message);
    }
  };

  const pages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

  return (
    <section className="card">
      <div className="filters">
        <input placeholder="Search description or merchant" value={filters.q} onChange={update("q")} />
        <select value={filters.category} onChange={update("category")}>
          <option value="">All categories</option>
          {categories.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
        <label>From <input type="date" value={filters.from} onChange={update("from")} /></label>
        <label>To <input type="date" value={filters.to} onChange={update("to")} /></label>
        <label className="check">
          <input type="checkbox" checked={applyToMerchant} onChange={(e) => setApplyToMerchant(e.target.checked)} />
          Apply category changes to the whole merchant
        </label>
      </div>

      {message && <p className="notice" onClick={() => setMessage(null)}>{message}</p>}

      {!data ? <p>Loading...</p> : (
        <>
          <table>
            <thead>
              <tr><th>Date</th><th>Description</th><th>Merchant</th><th className="num">Amount</th><th>Category</th></tr>
            </thead>
            <tbody>
              {data.items.map((tx) => (
                <tr key={tx.id}>
                  <td>{tx.date}</td>
                  <td className="desc">{tx.description}</td>
                  <td>{tx.merchant}</td>
                  <td className={`num ${tx.amount < 0 ? "neg" : "pos"}`}>{money(tx.amount)}</td>
                  <td>
                    <select value={tx.category} onChange={(e) => recategorize(tx, e.target.value)}
                            title={`categorized by ${tx.categorySource.toLowerCase().replace("_", " ")}`}>
                      {categories.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
                    </select>
                    {tx.categorySource !== "RULE" && <span className="dot" title={tx.categorySource} />}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="pager">
            <button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
            <span>Page {page + 1} of {pages} ({data.total} transactions)</span>
            <button disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</button>
          </div>
        </>
      )}
    </section>
  );
}
