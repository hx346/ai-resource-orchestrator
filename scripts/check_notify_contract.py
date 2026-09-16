"""Notify contract check: run against a DEMO instance started with
NOTIFY_MODE=live NOTIFY_PROVIDER=generic NOTIFY_WEBHOOK_URL=http://127.0.0.1:18099/hook
plus scripts/notify_fixture.py. Python stdlib only.

    ARO_ADMIN_PASSWORD=... [ARO_URL=http://127.0.0.1:8080] [FIXTURE_URL=http://127.0.0.1:18099] python scripts/check_notify_contract.py

Verifies PLAN_CONFIRMED, AVAILABILITY_CONFLICT, PLAN_CANCELLED and
PROJECT_COMPLETED webhooks reach the fixture with the generic envelope
(event/message/data) after the transaction commits.
"""
import os, json, time, urllib.request, urllib.parse, http.cookiejar, uuid

BASE = os.environ.get('ARO_URL', 'http://127.0.0.1:8080') + '/api/v1'
FIXTURE = os.environ.get('FIXTURE_URL', 'http://127.0.0.1:18099')
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
    # login first (form-encoded) — notify endpoints are admin-only
    global csrf
    csrf = json.loads(opener.open(urllib.request.Request(BASE + '/auth/csrf')).read())['data']
    data = urllib.parse.urlencode({'username': 'admin', 'password': os.environ['ARO_ADMIN_PASSWORD']}).encode()
    opener.open(urllib.request.Request(BASE + '/auth/login', data=data, headers={csrf['headerName']: csrf['token']})).read()
    csrf.update(json.loads(opener.open(urllib.request.Request(BASE + '/auth/csrf')).read())['data'])  # token rotates with the session
    assert call('/system/notify/status')['mode'] == 'live', 'start the backend with NOTIFY_MODE=live'

    suffix = uuid.uuid4().hex[:8]
    pid = call('/projects', 'POST', {'name': 'Notify ' + suffix, 'description': '契约测试', 'startDate': '2026-10-05', 'endDate': '2026-10-30', 'priority': 3})
    draft = call(f'/projects/{pid}/ai-plan', 'POST')
    call(f'/projects/{pid}/ai-plan/accept', 'POST', draft)
    plan_id = call(f'/projects/{pid}/solve', 'POST', {'strategy': 'BALANCED'})
    plan = call(f'/resource-plans/{plan_id}')
    call(f'/resource-plans/{plan_id}/confirm', 'POST')

    item = plan['items'][0]
    call(f"/employees/{item['employee_id']}/availability", 'POST',
         {'startDate': item['start_date'], 'endDate': item['end_date'], 'type': 'LEAVE', 'capacity': 0, 'remark': 'notify contract'})
    time.sleep(3)  # after-commit dispatch is asynchronous / 提交后异步发送

    events = json.loads(urllib.request.urlopen(FIXTURE + '/events', timeout=10).read())
    bodies = [e['body'] for e in events if isinstance(e.get('body'), dict)]
    confirmed = [b for b in bodies if b.get('event') == 'PLAN_CONFIRMED']
    conflict = [b for b in bodies if b.get('event') == 'AVAILABILITY_CONFLICT']
    assert confirmed and confirmed[0]['data']['planVersion'] == 1 and 'Notify ' + suffix in confirmed[0]['message'], bodies
    assert conflict and 'Notify ' + suffix in conflict[0]['message'] and 'LEAVE' in conflict[0]['message'], bodies
    log = call('/system/notify/log')
    assert any(r['type'] == 'PLAN_CONFIRMED' and r['status'] == 'SUCCESS' for r in log), log
    assert any(r['type'] == 'AVAILABILITY_CONFLICT' and r['status'] == 'SUCCESS' for r in log), log
    # 方案撤销与项目完结事件 / plan cancellation and project completion events
    call(f'/resource-plans/{plan_id}/cancel', 'POST')
    for t in call(f'/projects/{pid}/tasks'):
        call(f"/tasks/{t['id']}/status", 'POST', {'status': 'DONE'})
    call(f'/projects/{pid}/status', 'POST', {'status': 'IN_PROGRESS'})
    call(f'/projects/{pid}/status', 'POST', {'status': 'COMPLETED'})
    time.sleep(3)  # after-commit dispatch is asynchronous / 提交后异步发送
    events = json.loads(urllib.request.urlopen(FIXTURE + '/events', timeout=10).read())
    bodies = [e['body'] for e in events if isinstance(e.get('body'), dict)]
    cancelled = [b for b in bodies if b.get('event') == 'PLAN_CANCELLED']
    completed = [b for b in bodies if b.get('event') == 'PROJECT_COMPLETED']
    assert cancelled and cancelled[0]['data']['planVersion'] == 1 and 'Notify ' + suffix in cancelled[0]['message'], bodies
    assert completed and 'Notify ' + suffix in completed[0]['message'], bodies
    log = call('/system/notify/log')
    assert any(r['type'] == 'PLAN_CANCELLED' and r['status'] == 'SUCCESS' for r in log), log
    assert any(r['type'] == 'PROJECT_COMPLETED' and r['status'] == 'SUCCESS' for r in log), log
    print('NOTIFY CONTRACT PASSED; events:', len(bodies))

if __name__ == '__main__':
    main()
