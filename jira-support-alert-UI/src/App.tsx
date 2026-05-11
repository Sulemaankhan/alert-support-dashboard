import { useCallback, useEffect, useMemo, useState } from "react";
import {
  createIssue,
  fetchIssues,
  fetchMonitoring,
  fetchSummary,
  patchIssue,
  type Issue,
  type IssueSeverity,
  type IssueStatus,
  type MonitoringSnapshot,
  type IssueSummary,
} from "./api";
import { useAlertHistory, useAlertStream } from "./useAlertStream";

const SAMPLE_JAVA = `import java.util.ArrayList;
import java.util.List;

/**
 * A sample class for a coding project containing intentional bugs.
 * Goals: Fix NullPointerExceptions, logic errors, and boundary issues.
 */
public class UserManager {

    private List<String> users;

    public UserManager() {
        // BUG 1: List is declared but never initialized (NullPointerException).
        // To fix: users = new ArrayList<>();
    }

    public void addUser(String name) {
        // BUG 2: No check for null input; might cause issues later.
        users.add(name);
    }

    public String getUser(int index) {
        // BUG 3: IndexOutOfBoundsException. No check if index is valid.
        return users.get(index);
    }

    public boolean findUser(String name) {
        // BUG 4: Comparison error. Using '==' instead of '.equals()' for Strings.
        // BUG 5: Potential NullPointerException if 'name' is null.
        for (String u : users) {
            if (u == name) { 
                return true;
            }
        }
        return false;
    }

    public void clearList() {
        // BUG 6: Memory Leak/Logic error. Simply setting to null instead of clearing.
        users = null;
    }

    public static void main(String[] args) {
        UserManager manager = new UserManager();
        
        // This will crash immediately due to BUG 1
        System.out.println("Adding user...");
        manager.addUser("Alice");
        
        // This logic check will fail due to BUG 4
        String searchName = new String("Alice");
        System.out.println("Found Alice? " + manager.findUser(searchName));
    }
}`;

function severityClass(s: IssueSeverity): string {
  switch (s) {
    case "CRITICAL":
      return "sev-critical";
    case "HIGH":
      return "sev-high";
    case "MEDIUM":
      return "sev-medium";
    default:
      return "sev-low";
  }
}

export default function App() {
  const [issues, setIssues] = useState<Issue[]>([]);
  const [summary, setSummary] = useState<IssueSummary | null>(null);
  const [monitor, setMonitor] = useState<MonitoringSnapshot | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [form, setForm] = useState({
    title: "",
    description: "",
    severity: "MEDIUM" as IssueSeverity,
  });

  const { items: alerts, push, clear } = useAlertHistory();

  const refresh = useCallback(async () => {
    setLoadError(null);
    try {
      const [i, s, m] = await Promise.all([fetchIssues(), fetchSummary(), fetchMonitoring()]);
      setIssues(i);
      setSummary(s);
      setMonitor(m);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : "Load failed");
    }
  }, []);

  useEffect(() => {
    void refresh();
    const id = window.setInterval(() => void refresh(), 8000);
    return () => window.clearInterval(id);
  }, [refresh]);

  const { connected } = useAlertStream(push);

  const rows = useMemo(
    () =>
      [...issues].sort((a, b) => {
        const sev = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
        const d = sev[a.severity] - sev[b.severity];
        if (d !== 0) return d;
        return b.id - a.id;
      }),
    [issues]
  );

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!form.title.trim()) return;
    try {
      await createIssue({
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        severity: form.severity,
        component: "UserManager",
        filePath: "UserManager.java",
      });
      setForm({ title: "", description: "", severity: "MEDIUM" });
      await refresh();
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : "Create failed");
    }
  }

  async function onStatusChange(id: number, status: IssueStatus) {
    await patchIssue(id, { status });
    await refresh();
  }

  return (
    <div className="page">
      <header className="hero">
        <div>
          <p className="eyebrow">UserManager sample · static analysis tracker</p>
          <h1>Issue tracking &amp; live alerts</h1>
          <p className="lede">
            Backend seeds six defects from your snippet. New issues POST to the API and broadcast over SSE so this UI
            can toast immediately.
          </p>
        </div>
        <div className="hero-stats">
          <div className="stat">
            <span className="stat-label">Open / in progress</span>
            <span className="stat-value">{summary?.openOrInProgress ?? "—"}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Total issues</span>
            <span className="stat-value">{summary?.total ?? issues.length}</span>
          </div>
          <div className="stat">
            <span className="stat-label">App health</span>
            <span className="stat-value mono">{monitor?.appStatus ?? "—"}</span>
          </div>
        </div>
      </header>

      {loadError && (
        <div className="banner error">
          {loadError}
          <button type="button" className="linkish" onClick={() => void refresh()}>
            Retry
          </button>
        </div>
      )}

      <section className="grid-two">
        <div className="panel">
          <div className="panel-head">
            <h2>Reference snippet</h2>
            <span className="badge">read-only</span>
          </div>
          <pre className="code-block">
            <code>{SAMPLE_JAVA}</code>
          </pre>
        </div>

        <div className="panel">
          <div className="panel-head">
            <h2>Monitoring</h2>
            <span className="badge subtle">{monitor?.timestamp?.slice(11, 19) ?? ""}</span>
          </div>
          <dl className="kv">
            <dt>Spring health</dt>
            <dd className="mono">{monitor?.appStatus ?? "…"}</dd>
            <dt>Open issues</dt>
            <dd>{monitor?.openIssues ?? "…"}</dd>
            <dt>UI poll + SSE</dt>
            <dd>Snapshot every 8s; alerts on create/update</dd>
          </dl>
        </div>
      </section>

      <section className="panel alerts-panel">
        <div className="panel-head">
          <h2>Alert stream</h2>
          <div className="row-actions">
            <span className={`dot ${connected ? "live" : ""}`} title={connected ? "SSE connected" : "SSE disconnected"} />
            <button type="button" className="btn ghost" onClick={clear}>
              Clear
            </button>
          </div>
        </div>
        {alerts.length === 0 ? (
          <p className="muted pad">No alerts yet. Create an issue to see a real-time event.</p>
        ) : (
          <ul className="alert-list">
            {alerts.map((a, idx) => (
              <li key={`${a.timestamp}-${idx}`} className={`alert-item ${a.type === "NEW_ISSUE" ? "new" : ""}`}>
                <span className="alert-type">{a.type}</span>
                <span className="alert-msg">{a.message}</span>
                <span className="alert-meta mono">
                  {a.issueId != null ? `#${a.issueId}` : ""} {a.detail ? `· ${a.detail}` : ""}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Tracked issues</h2>
          <button type="button" className="btn ghost" onClick={() => void refresh()}>
            Refresh
          </button>
        </div>

        <form className="inline-form" onSubmit={onCreate}>
          <input
            placeholder="New issue title"
            value={form.title}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
          />
          <input
            placeholder="Description (optional)"
            value={form.description}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
          />
          <select value={form.severity} onChange={(e) => setForm((f) => ({ ...f, severity: e.target.value as IssueSeverity }))}>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
          <button type="submit" className="btn primary">
            Track issue
          </button>
        </form>

        <div className="table-wrap">
          <table className="issues-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Ref</th>
                <th>Title</th>
                <th>Location</th>
                <th>Severity</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((issue) => (
                <tr key={issue.id}>
                  <td className="mono">{issue.id}</td>
                  <td className="mono">{issue.codeRef}</td>
                  <td>
                    <div className="cell-title">{issue.title}</div>
                    <div className="cell-desc muted">{issue.description}</div>
                  </td>
                  <td className="mono small">
                    {issue.filePath}
                    {issue.lineStart != null ? `:${issue.lineStart}` : ""}
                    {issue.lineEnd != null && issue.lineEnd !== issue.lineStart ? `–${issue.lineEnd}` : ""}
                  </td>
                  <td>
                    <span className={`pill ${severityClass(issue.severity)}`}>{issue.severity}</span>
                  </td>
                  <td>
                    <select
                      className="status-select"
                      value={issue.status}
                      onChange={(e) => void onStatusChange(issue.id, e.target.value as IssueStatus)}
                    >
                      <option value="OPEN">Open</option>
                      <option value="IN_PROGRESS">In progress</option>
                      <option value="RESOLVED">Resolved</option>
                      <option value="CLOSED">Closed</option>
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <footer className="footer muted">
        API: <span className="mono">/api/issues</span>, alerts: <span className="mono">GET /alerts/stream</span> (SSE).
      </footer>
    </div>
  );
}
