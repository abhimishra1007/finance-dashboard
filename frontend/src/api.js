// Thin wrapper around the Spring Boot REST API.
async function request(path, options = {}) {
  const res = await fetch(`/api${path}`, options);
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const body = await res.json();
      detail = body.detail || detail;
    } catch {
      // not JSON
    }
    throw new Error(detail);
  }
  return res.json();
}

function query(params) {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") qs.set(k, v);
  });
  const s = qs.toString();
  return s ? `?${s}` : "";
}

export const api = {
  summary: (from, to) => request(`/analytics/summary${query({ from, to })}`),
  recurring: () => request("/analytics/recurring"),
  categories: () => request("/categories"),
  transactions: (filters) => request(`/transactions${query(filters)}`),
  recategorize: (id, category, applyToMerchant) =>
    request(`/transactions/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ category, applyToMerchant }),
    }),
  importCsv: (file) => {
    const form = new FormData();
    form.append("file", file);
    return request("/transactions/import", { method: "POST", body: form });
  },
};

export const money = (n) =>
  Number(n).toLocaleString("en-US", { style: "currency", currency: "USD" });
