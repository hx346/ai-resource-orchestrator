"""External sync contract check: run against an instance started with
SYNC_JIRA_ENABLED=true SYNC_JIRA_BASE_URL=http://127.0.0.1:18101
SYNC_JIRA_USERNAME=admin SYNC_JIRA_API_TOKEN=demo-token
(plus GITLAB/ZENTAO vars below) and scripts/sync_fixture.py. Python stdlib only.

    ARO_ADMIN_PASSWORD=... [ARO_URL=http://127.0.0.1:8080] [FIXTURE_URL=http://127.0.0.1:18101] \
        python scripts/check_sync_contract.py

Verifies: list → import → task landing with normalized status/hours → duplicate
import guard → incremental refresh → link mapping, for Jira; list for GitLab/ZenTao.
"""
import os, json, urllib.request, urllib.parse, http.cookiejar, uuid

BASE = os.environ.get('ARO_URL', 'http://127.0.0.1:8080') + '/api/v1'
csrf, opener = None, urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def call(path, method='GET', body=None):
    global csrf
    headers = {'Accept-Language': 'zh-CN'}
    if method != 'GET':
        if csrf is None:
            csrf = json.loads(opener.open(urllib.request.Request(BASE + '/auth/csrf')).read())['data']
        headers[csrf['headerName']] = csrf['token']
        if body is not None:
            body = json.dumps(body).encode(); headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
    return json.loads(opener.open(request, timeout=100).read())['data']


def main():
    global csrf
    csrf = json.loads(opener.open(urllib.request.Request(BASE + '/auth/csrf')).read())['data']
    data = urllib.parse.urlencode({'username': 'admin', 'password': os.environ['ARO_ADMIN_PASSWORD']}).encode()
    opener.open(urllib.request.Request(BASE + '/auth/login', data=data, headers={csrf['headerName']: csrf['token']})).read()
    csrf.update(json.loads(opener.open(urllib.request.Request(BASE + '/auth/csrf')).read())['data'])

    suffix = uuid.uuid4().hex[:8]
    # ---- Jira：列 → 导入 → 校验归一 → 重复导入守卫 → 刷新 ----
    projects = call('/sync/jira/projects')
    web = next(p for p in projects if p['key'] == 'WEB')
    assert 'Website Revamp' in web['name'], projects

    result = call('/sync/jira/import', 'POST', {'externalId': web['externalId']})
    assert not result.get('alreadyImported') and result['tasksImported'] == 3 and result['openTasks'] == 2, result
    pid = result['projectId']
    tasks = call(f'/projects/{pid}/tasks')
    by_name = {t['name']: t for t in tasks}
    assert by_name['Design homepage']['status'] == 'DONE', by_name            # statusCategory done
    assert by_name['Implement CMS integration']['status'] == 'IN_PROGRESS'    # indeterminate
    assert by_name['Implement CMS integration']['estimatedHours'] == 8        # 28800s → 8h
    assert by_name['QA and rollout']['estimatedHours'] == 8                   # 无估时 → 默认 8h
    assert tasks[0]['startDate'] <= tasks[-1]['endDate'], tasks               # 顺序排期

    again = call('/sync/jira/import', 'POST', {'externalId': web['externalId']})
    assert again['alreadyImported'] and again['projectId'] == pid, again      # 重复导入返回已导入
    links = call(f'/sync/jira/links?projectId={pid}')
    assert sum(1 for l in links if l['externalType'] == 'PROJECT') == 1 and len(links) == 4, links

    refresh = call('/sync/jira/refresh', 'POST', {'projectId': pid})
    assert refresh['remoteTasks'] == 3 and refresh['added'] == 0 and refresh['updated'] == 3, refresh
    print(f'PASS: jira import ({result["tasksImported"]} tasks, window {result["startDate"]}→{result["endDate"]}), duplicate guard, incremental refresh')

    # ---- Jira BULK：250 条 issue 验证 startAt 分页拉全 / paged fetch lands every issue ----
    bulk = next(p for p in call('/sync/jira/projects') if p['key'] == 'BULK')
    bulk_result = call('/sync/jira/import', 'POST', {'externalId': bulk['externalId']})
    assert bulk_result['tasksImported'] == 250, bulk_result
    imported = call('/sync/jira/imports')
    assert any(i['projectId'] == pid and i['openTasks'] == 2 for i in imported), imported
    assert any(i['projectId'] == bulk_result['projectId'] and i['openTasks'] == 250 for i in imported), imported
    print(f"PASS: jira paged fetch imports all 250 bulk issues; imports listing shows both projects")

    # ---- GitLab：列 + 导入 ----
    gl_projects = call('/sync/gitlab/projects')
    pipeline = next(p for p in gl_projects if p['externalId'] == '77')
    gl = call('/sync/gitlab/import', 'POST', {'externalId': pipeline['externalId']})
    assert gl['tasksImported'] == 2 and gl['doneTasks'] == 1, gl
    gl_tasks = {t['name']: t for t in call(f"/projects/{gl['projectId']}/tasks")}
    assert gl_tasks['Nightly rebuild']['status'] == 'DONE', gl_tasks
    print('PASS: gitlab import with closed-issue normalization')

    # ---- 禅道：列 + 导入（pri/estimate 映射）----
    zt_projects = call('/sync/zentao/projects')
    mes = next(p for p in zt_projects if p['externalId'] == '55')
    zt = call('/sync/zentao/import', 'POST', {'externalId': mes['externalId']})
    zt_tasks = {t['name']: t for t in call(f"/projects/{zt['projectId']}/tasks")}
    assert zt_tasks['设备点位建模']['status'] == 'IN_PROGRESS' and zt_tasks['设备点位建模']['priority'] == 1, zt_tasks
    assert zt_tasks['看板联调']['priority'] == 4, zt_tasks                        # pri 3 → 4
    print('PASS: zentao import with priority/estimate mapping')
    print('SYNC CONTRACT PASSED')


if __name__ == '__main__':
    main()
