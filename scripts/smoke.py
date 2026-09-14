"""Exercise a running DEMO instance. Creates labelled test records; use an isolated database.
Set ARO_ADMIN_PASSWORD (required); optionally ARO_URL. Python standard library only.
"""
import os, json, urllib.request, urllib.error, urllib.parse, http.cookiejar, uuid
from concurrent.futures import ThreadPoolExecutor

BASE=os.environ.get('ARO_URL','http://127.0.0.1:8080')+'/api/v1'
class Client:
    def __init__(self):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=None
    def call(self,path,method='GET',body=None,status=200,csrf=True):
        headers={'Accept-Language':'zh-CN'}
        if method!='GET' and csrf:
            if self.csrf is None:self.csrf=self.call('/auth/csrf')
            headers[self.csrf['headerName']]=self.csrf['token']
        data=None
        if body is not None: data=json.dumps(body).encode();headers['Content-Type']='application/json'
        try:
            r=self.opener.open(urllib.request.Request(BASE+path,data=data,headers=headers,method=method),timeout=100)
            actual=r.status;payload=r.read()
        except urllib.error.HTTPError as e:actual=e.code;payload=e.read()
        assert actual==status,(path,actual,payload.decode()[:1000])
        parsed=json.loads(payload)
        return parsed.get('data')
    def login(self,name,password):
        self.csrf=self.call('/auth/csrf')
        self.opener.open(urllib.request.Request(BASE+'/auth/login',method='POST',data=urllib.parse.urlencode({'username':name,'password':password}).encode(),headers={self.csrf['headerName']:self.csrf['token']})).read()
        self.csrf=self.call('/auth/csrf')

def main():
    a=Client(); a.call('/employees',status=401)
    a.login('admin',os.environ['ARO_ADMIN_PASSWORD'])
    assert a.call('/ai/status')['mode']=='demo','Smoke suite is restricted to demo mode'
    a.call('/projects','POST',{},status=403,csrf=False)
    suffix=uuid.uuid4().hex[:8]
    role_password='Smoke-Password-2026!'
    for role in ['EMPLOYEE','PROJECT_MANAGER','DEPARTMENT_MANAGER']:
        name=role.lower()+'_'+suffix
        a.call('/auth/users','POST',{'username':name,'password':role_password,'role':role})
        u=Client();u.login(name,role_password);u.call('/employees')
        if role=='EMPLOYEE': u.call('/projects','POST',{},status=403)
        if role=='PROJECT_MANAGER': u.call('/employees','POST',{},status=403)
        if role=='DEPARTMENT_MANAGER': u.call('/projects','POST',{},status=403)
        u.call('/auth/users',status=403)
    print('PASS: session login, CSRF and all four role boundaries')
    if a.call('/employees')['total']==0:a.call('/system/demo-data','POST')
    project={'name':'Smoke '+suffix,'description':'建设员工能力与项目资源编排平台','startDate':'2026-10-05','endDate':'2026-10-30','priority':3}
    pid=a.call('/projects','POST',project)
    draft=a.call(f'/projects/{pid}/ai-plan','POST')
    assert len(draft['tasks'])==3 and '演示' in draft['summary']
    invalid=json.loads(json.dumps(draft));invalid['tasks'][0]['skills'][0]['skillId']=9999999
    a.call(f'/projects/{pid}/ai-plan/accept','POST',invalid,status=404)
    assert a.call(f'/projects/{pid}/tasks')==[]
    ids=a.call(f'/projects/{pid}/ai-plan/accept','POST',draft)
    a.call(f'/projects/{pid}/ai-plan/accept','POST',draft,status=400)
    a.call(f'/tasks/{ids[0]}/dependencies','POST',{'predecessorTaskId':ids[2],'dependencyType':'FS'},status=422)
    other=a.call('/projects','POST',{**project,'name':'Other '+suffix})
    other_task=a.call(f'/projects/{other}/tasks','POST',{'name':'Other','startDate':'2026-10-05','endDate':'2026-10-05','estimatedHours':8})
    a.call(f'/tasks/{ids[0]}/dependencies','POST',{'predecessorTaskId':other_task},status=422)
    print('PASS: editable AI draft, atomic acceptance, duplicate import and dependency cycles rejected')
    candidates=a.call(f'/projects/{pid}/candidates')
    assert all(c['candidates'] for c in candidates)
    plan_id=a.call(f'/projects/{pid}/solve','POST',{'strategy':'BALANCED'})
    plan=a.call(f'/resource-plans/{plan_id}')
    assert not plan['gaps'] and len(plan['items'])==3,plan
    assert '0hard/' in plan['score_text']
    selections=[{'taskId':c['taskId'],'employeeId':c['candidates'][-1]['employeeId']} for c in candidates]
    a.call(f'/resource-plans/{plan_id}/items','PUT',selections)
    review=a.call(f'/resource-plans/{plan_id}/review','POST')
    assert review['mode']=='demo' and review['text']
    # Two drafts share the same baseline. Only one may become active.
    second=a.call(f'/projects/{pid}/solve','POST',{'strategy':'BALANCED'})
    a.call(f'/resource-plans/{plan_id}/confirm','POST')
    a.call(f'/resource-plans/{plan_id}/confirm','POST')
    a.call(f'/resource-plans/{second}/confirm','POST',status=409)
    assert a.call(f'/resource-plans/{plan_id}')['status']=='CONFIRMED'
    a.call(f'/resource-plans/{plan_id}/cancel','POST')
    assert a.call(f'/resource-plans/{plan_id}')['status']=='ARCHIVED'
    # A changed planning input invalidates old drafts even after allocations are released.
    a.call(f'/resource-plans/{second}/confirm','POST',status=409)
    a.call(f'/resource-plans/{second}/cancel','POST')
    print('PASS: real Timefold matching, manual edit, review, idempotent confirmation, stale draft rejection and cancellation')
    gap_project=a.call('/projects','POST',{**project,'name':'Gap '+suffix})
    a.call(f'/projects/{gap_project}/tasks','POST',{'name':'Impossible workload','startDate':'2026-10-05','endDate':'2026-10-05','estimatedHours':10000})
    gap_id=a.call(f'/projects/{gap_project}/solve','POST',{'strategy':'BALANCED'})
    assert len(a.call(f'/resource-plans/{gap_id}')['gaps'])==1
    a.call(f'/resource-plans/{gap_id}/confirm','POST',status=400)
    a.call(f'/resource-plans/{gap_id}/cancel','POST')
    print('PASS: infeasible demand produces an explicit gap and cannot be confirmed')
    # A task nobody can staff must report which skill is missing. / 无人可承担的任务须报告缺失技能。
    rare=a.call('/skills','POST',{'name':'Smoke稀有技能 '+suffix,'categoryId':a.call('/skill-categories')[0]['id']})
    skill_gap_project=a.call('/projects','POST',{**project,'name':'SkillGap '+suffix})
    rare_task=a.call(f'/projects/{skill_gap_project}/tasks','POST',{'name':'无人可承担','startDate':'2026-10-05','endDate':'2026-10-05','estimatedHours':8})
    a.call(f'/tasks/{rare_task}/skill-requirements','POST',{'skillId':rare,'minLevel':5,'weight':1,'requirementType':'REQUIRED'})
    skill_gap_id=a.call(f'/projects/{skill_gap_project}/solve','POST',{'strategy':'BALANCED'})
    detailed=a.call(f'/resource-plans/{skill_gap_id}')
    assert detailed['gaps'][0]['skillGap'] and detailed['gaps'][0]['missingSkills'][0]['skillId']==rare,detailed['gaps']
    assert detailed['gapSummary'][0]['taskCount']==1 and detailed['gapSummary'][0]['totalHours']==8,detailed['gapSummary']
    a.call(f'/resource-plans/{skill_gap_id}/cancel','POST')
    print('PASS: skill-level gap analysis reports missing skills and aggregation')
    # Exercise concurrent confirmation using independent cookie sessions.
    fresh=a.call(f'/projects/{pid}/solve','POST',{'strategy':'BALANCED'})
    peers=[Client(),Client()]
    for c in peers:c.login('admin',os.environ['ARO_ADMIN_PASSWORD'])
    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(lambda c:c.call(f'/resource-plans/{fresh}/confirm','POST'),peers))
    a.call(f'/resource-plans/{fresh}/cancel','POST')
    print('PASS: concurrent duplicate confirmation is safe')
    print('ALL API SMOKE CHECKS PASSED; record suffix:',suffix)
if __name__=='__main__':main()
