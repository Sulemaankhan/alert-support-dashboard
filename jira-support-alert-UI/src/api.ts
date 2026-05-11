export type IssueSeverity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
export type IssueStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";

export interface Issue {
  id: number;
  codeRef: string;
  title: string;
  description: string;
  component: string;
  filePath: string;
  lineStart: number | null;
  lineEnd: number | null;
  severity: IssueSeverity;
  status: IssueStatus;
  createdAt: string;
  updatedAt: string;
}

export interface IssueSummary {
  total: number;
  openOrInProgress: number;
  bySeverity: Record<string, number>;
}

export interface MonitoringSnapshot {
  appStatus: string;
  openIssues: number;
  totalIssues: number;
  checkIntervalMs: number;
  timestamp: string;
}

export interface AlertPayload {
  type: string;
  message: string;
  issueId: number | null;
  detail: string | null;
  timestamp: string;
}

const jsonHeaders = { "Content-Type": "application/json" };

export async function fetchIssues(): Promise<Issue[]> {
  const r = await fetch("/api/issues");
  if (!r.ok) throw new Error("Failed to load issues");
  return r.json();
}

export async function fetchSummary(): Promise<IssueSummary> {
  const r = await fetch("/api/issues/stats/summary");
  if (!r.ok) throw new Error("Failed to load summary");
  return r.json();
}

export async function fetchMonitoring(): Promise<MonitoringSnapshot> {
  const r = await fetch("/api/monitoring/snapshot");
  if (!r.ok) throw new Error("Failed to load monitoring");
  return r.json();
}

export async function createIssue(body: {
  title: string;
  description?: string;
  severity: IssueSeverity;
  component?: string;
  filePath?: string;
  lineStart?: number;
  lineEnd?: number;
}): Promise<Issue> {
  const r = await fetch("/api/issues", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify(body),
  });
  if (!r.ok) {
    const t = await r.text();
    throw new Error(t || "Create failed");
  }
  return r.json();
}

export async function patchIssue(
  id: number,
  body: Partial<{ status: IssueStatus; severity: IssueSeverity; title: string; description: string }>
): Promise<Issue> {
  const r = await fetch(`/api/issues/${id}`, {
    method: "PATCH",
    headers: jsonHeaders,
    body: JSON.stringify(body),
  });
  if (!r.ok) throw new Error("Update failed");
  return r.json();
}
