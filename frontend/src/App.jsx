import { useEffect, useState } from "react";
import { api } from "./api";
import Dashboard from "./components/Dashboard";
import Transactions from "./components/Transactions";
import ImportPanel from "./components/ImportPanel";

const TABS = ["Dashboard", "Transactions", "Import"];

export default function App() {
  const [tab, setTab] = useState("Dashboard");
  const [categories, setCategories] = useState([]);
  // Bumped after an import or a re-categorization so every view reloads.
  const [version, setVersion] = useState(0);

  useEffect(() => {
    api.categories().then(setCategories).catch(console.error);
  }, []);

  const changed = () => setVersion((v) => v + 1);

  return (
    <div className="app">
      <header>
        <h1>Personal Finance Dashboard</h1>
        <nav>
          {TABS.map((t) => (
            <button key={t} className={t === tab ? "active" : ""} onClick={() => setTab(t)}>
              {t}
            </button>
          ))}
        </nav>
      </header>
      <main>
        {tab === "Dashboard" && <Dashboard version={version} />}
        {tab === "Transactions" && (
          <Transactions categories={categories} version={version} onChange={changed} />
        )}
        {tab === "Import" && <ImportPanel onImported={changed} />}
      </main>
    </div>
  );
}
