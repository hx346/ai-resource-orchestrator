"""Local stub for the external sync contract test (Python stdlib only).

Serves minimal Jira REST v2 / GitLab v4 / ZenTao OpenAPI v1 endpoints with
fixed data and token checks. Run: python scripts/sync_fixture.py [port]
(default 18101). Recorded requests are exposed at GET /requests.

Then start the backend with, e.g.:
    SYNC_JIRA_ENABLED=true SYNC_JIRA_BASE_URL=http://127.0.0.1:18101 \
    SYNC_JIRA_USERNAME=admin SYNC_JIRA_API_TOKEN=demo-token
and run scripts/check_sync_contract.py against it.
"""
import base64, json, sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

JIRA_TOKEN = "demo-token"
GITLAB_TOKEN = "glpat-demo"
ZENTAO_TOKEN = "zt-demo-token"

# 固定数据：一个 Jira 项目（含已完成 / 进行中 issue）、一个 GitLab 项目、一个禅道项目
JIRA_PROJECTS = [{"id": "10001", "key": "WEB", "name": "Website Revamp", "archived": False},
                 {"id": "10002", "key": "BULK", "name": "Bulk Migration", "archived": False}]
JIRA_ISSUES = [
    {"id": "101", "key": "WEB-1", "fields": {"summary": "Design homepage", "status": {"statusCategory": {"key": "done"}},
     "priority": {"id": "2"}, "timeoriginalestimate": 14400}},
    {"id": "102", "key": "WEB-2", "fields": {"summary": "Implement CMS integration", "status": {"statusCategory": {"key": "indeterminate"}},
     "priority": {"id": "1"}, "timeoriginalestimate": 28800}},
    {"id": "103", "key": "WEB-3", "fields": {"summary": "QA and rollout", "status": {"statusCategory": {"key": "new"}},
     "priority": {"id": "3"}, "timeoriginalestimate": None}},
]
# 250 条 issue 用于验证 startAt 分页拉全 / 250 issues to prove startAt pagination
BULK_ISSUES = [{"id": str(200 + i), "key": f"BULK-{i + 1}", "fields": {"summary": f"Batch item {i + 1}",
                "status": {"statusCategory": {"key": "new"}}, "priority": {"id": "3"},
                "timeoriginalestimate": 3600}} for i in range(250)]
GITLAB_PROJECTS = [{"id": 77, "path": "data-pipeline", "name": "Data Pipeline", "archived": False, "web_url": "http://fixture/data-pipeline"}]
GITLAB_ISSUES = [
    {"iid": 11, "title": "Ingest CDC stream", "state": "opened", "weight": 2, "web_url": "http://fixture/issues/11"},
    {"iid": 12, "title": "Nightly rebuild", "state": "closed", "weight": 3, "web_url": "http://fixture/issues/12"},
]
ZENTAO_PROJECTS = [{"id": 55, "code": "MES", "name": "MES 对接", "status": "doing"}]
ZENTAO_TASKS = [
    {"id": 501, "name": "设备点位建模", "status": "doing", "pri": 1, "estimate": 16},
    {"id": 502, "name": "看板联调", "status": "wait", "pri": 3, "estimate": 24},
]
REQUESTS = []


class Handler(BaseHTTPRequestHandler):

    def _send(self, payload, status=200):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self, kind):
        if kind == "jira":
            header = self.headers.get("Authorization", "")
            basic = "Basic " + base64.b64encode(f"admin:{JIRA_TOKEN}".encode()).decode()
            return header in (basic, f"Bearer {JIRA_TOKEN}")
        if kind == "gitlab":
            return self.headers.get("PRIVATE-TOKEN") == GITLAB_TOKEN
        return self.headers.get("Token") == ZENTAO_TOKEN

    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        REQUESTS.append({"path": self.path, "auth": dict(self.headers)})
        if parsed.path == "/requests":
            return self._send(REQUESTS)
        # ---- Jira REST v2 ----
        if parsed.path == "/rest/api/2/project":
            return self._send(JIRA_PROJECTS if self._authorized("jira") else {"errors": []}, 200 if self._authorized("jira") else 401)
        if parsed.path.startswith("/rest/api/2/project/"):
            key = parsed.path.rsplit("/", 1)[1]
            project = next((p for p in JIRA_PROJECTS if p["id"] == key or p["key"] == key), None)
            return self._send(project, 200) if self._authorized("jira") and project else self._send({"errors": []}, 404)
        if parsed.path == "/rest/api/2/search":
            if not self._authorized("jira"):
                return self._send({"errors": []}, 401)
            jql = query.get("jql", [""])[0]
            items = BULK_ISSUES if "BULK" in jql or "10002" in jql else JIRA_ISSUES
            max_results = int(query.get("maxResults", ["100"])[0])
            start_at = int(query.get("startAt", ["0"])[0])
            return self._send({"startAt": start_at, "maxResults": max_results,
                               "total": len(items), "issues": items[start_at:start_at + max_results]})
        # ---- GitLab v4 ----
        if parsed.path == "/api/v4/projects":
            return self._send(GITLAB_PROJECTS if self._authorized("gitlab") else [], 200 if self._authorized("gitlab") else 401)
        if parsed.path.startswith("/api/v4/projects/") and parsed.path.endswith("/issues"):
            return self._send(GITLAB_ISSUES if self._authorized("gitlab") else [], 200 if self._authorized("gitlab") else 401)
        if parsed.path.startswith("/api/v4/projects/"):
            pid = parsed.path.rsplit("/", 1)[1]
            project = next((p for p in GITLAB_PROJECTS if str(p["id"]) == pid), None)
            return self._send(project, 200) if self._authorized("gitlab") and project else self._send({"message": "404"}, 404)
        # ---- 禅道 OpenAPI v1 ----
        if parsed.path == "/api.php/v1/projects":
            return self._send({"projects": ZENTAO_PROJECTS}, 200 if self._authorized("zentao") else 401)
        if parsed.path.endswith("/tasks"):
            return self._send({"tasks": ZENTAO_TASKS}, 200 if self._authorized("zentao") else 401)
        self._send({"message": "not found"}, 404)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18101
    print(f"sync fixture on http://127.0.0.1:{port} (jira/gitlab/zentao stubs; GET /requests)")
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
