import { useState } from "react";
import { api } from "../api";

export default function ImportPanel({ onImported }) {
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const upload = async (e) => {
    e.preventDefault();
    if (!file) return;
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const r = await api.importCsv(file);
      setResult(r);
      onImported();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card narrow">
      <h2>Import bank transactions</h2>
      <p className="muted">
        Upload a CSV export from your bank. It needs a date column, a description column, and either a signed
        amount column or separate debit/credit columns. Re-importing the same file skips rows already saved.
      </p>
      <form onSubmit={upload} className="import">
        <input type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files[0])} />
        <button type="submit" disabled={!file || busy}>{busy ? "Importing..." : "Import"}</button>
      </form>
      {error && <p className="error">{error}</p>}
      {result && (
        <div className="result">
          <p>
            Read {result.rowsRead} rows: <strong>{result.inserted} new</strong>, {result.duplicatesSkipped} already
            imported. Took {result.elapsedMillis} ms.
          </p>
          <table>
            <tbody>
              {Object.entries(result.byCategory).map(([cat, n]) => (
                <tr key={cat}><td>{cat}</td><td className="num">{n}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
