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
    assert plan['warnings'] and plan['warnings'][0]['load']>=80,plan['warnings']
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
    # Strategies and side-by-side comparison / 多策略求解与方案对比。
    skill_first=a.call(f'/projects/{pid}/solve','POST',{'strategy':'BEST_SKILL_MATCH'})
    low_risk=a.call(f'/projects/{pid}/solve','POST',{'strategy':'LOWEST_RISK'})
    a.call(f'/projects/{pid}/solve','POST',{'strategy':'FASTEST'},status=400)
    diff=a.call(f'/resource-plans/compare?left={skill_first}&right={low_risk}')
    assert len(diff['rows'])==3 and diff['left']['strategy']=='BEST_SKILL_MATCH' and diff['right']['strategy']=='LOWEST_RISK',diff
    assert all(r['left'] and r['right'] and not r['right'].get('gap') for r in diff['rows']),diff['rows']
    a.call(f'/resource-plans/{skill_first}/cancel','POST');a.call(f'/resource-plans/{low_risk}/cancel','POST')
    print('PASS: solving strategies validated and plans compared')
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
    notify=a.call('/system/notify/status')
    assert notify['mode']=='off' and isinstance(a.call('/system/notify/log'),list),notify
    # Phase 6: capability decisions / 组织能力决策
    sd=a.call('/capability/supply-demand')
    assert sd['rows'] and all(set(r)>={'skillId','skillName','requiredLevel','demandHours','qualifiedCount','availableHours','gapHours','gapPeople','advice'} for r in sd['rows']),sd
    rare_rows=[r for r in sd['rows'] if r['skillName'].startswith('Smoke稀有技能')]
    assert rare_rows and rare_rows[0]['qualifiedCount']==0 and rare_rows[0]['gapHours']>0 and '招聘' in rare_rows[0]['advice'],rare_rows
    assert isinstance(a.call('/capability/key-people'),list)
    impact=a.call(f"/capability/leave-impact?employeeId={a.call('/employees')['list'][0]['id']}")
    assert impact['employeeName'] and isinstance(impact['affectedTasks'],list),impact
    scenario=a.call('/capability/scenario')
    assert isinstance(scenario['rows'],list) and isinstance(scenario['includedProjects'],list) and 'baseSummary' in scenario,scenario
    if scenario['rows']:
        assert set(scenario['rows'][0])>={'skillId','skillName','baseGapPeople','scenarioGapPeople','deltaPeople'},scenario['rows'][0]
    trends=a.call('/capability/trends')
    assert set(trends)>={'rows','summary'} and all(set(r)>={'skillName','w8','w12','w26'} for r in trends['rows']),trends
    advise=a.call('/capability/advise?weeks=12','POST')
    assert advise['mode']=='demo' and advise['text'],advise
    print(f"PASS: capability forecast ({sd['summary']['shortageSkills']}/{sd['summary']['skillsTracked']} skills short), key people, leave impact, scenario, trends, advice")
    # Phase 6 remainder: weekly supply model + external market benchmarks / 周级供给模型与市场数据
    weekly=a.call('/capability/supply-weekly?weeks=8')
    assert weekly['weekStarts'] and len(weekly['weekStarts'])==8,weekly
    assert all(set(r)>={'skillId','skillName','weekly','demandHours','gapHours','gapPeople'} for r in weekly['rows']),weekly['rows'][:1]
    sd_weekly=a.call('/capability/supply-demand?weeks=8&model=weekly')
    assert sd_weekly['model']=='weekly',sd_weekly
    a.call('/capability/supply-demand?weeks=8&model=bogus',status=400)
    market_skill=sd['rows'][0]['skillId']
    imported=a.call('/capability/market/import','POST',{'source':'smoke','items':[{'skillId':market_skill,'demandIndex':85,'salaryMin':15000,'salaryMax':30000,'hiringLeadWeeks':6,'note':'smoke'}]})
    assert imported['imported']==1,imported
    market=a.call('/capability/market')
    assert any(m['skillId']==market_skill and float(m['demandIndex'])==85 for m in market),market
    enriched=a.call('/capability/supply-demand?weeks=12')
    target=[r for r in enriched['rows'] if r['skillId']==market_skill]
    assert target and 'market' in target[0],target
    advise_market=a.call('/capability/advise?weeks=12','POST')
    if any(r['skillId']==market_skill and r['gapPeople']>0 for r in enriched['rows']):
        assert '市场参考' in advise_market['text'],advise_market['text'][-200:]
    a.call(f'/capability/market/{market_skill}','DELETE')
    assert not any(m['skillId']==market_skill for m in a.call('/capability/market'))
    a.call(f'/capability/market/{market_skill}','DELETE',status=400)
    print('PASS: weekly supply model, flat/weekly model switch, market import enriching forecast and advice')
    # Phase 3/4/5 remainders: semantic (disabled by default), auto-replan trigger log, sync guards / 余项守卫
    a.call('/skills/semantic?q=java',status=400)
    a.call('/skills/semantic/rebuild','POST',status=400)
    assert isinstance(a.call('/replan/triggers'),list)
    a.call('/sync/jira/projects',status=400)
    a.call('/sync/bogus/projects',status=400)
    print('PASS: optional capabilities stay guarded when disabled (semantic search, external sync)')
    timeline=a.call('/allocations/timeline')
    assert timeline['weeks'] and timeline['rows'] and all('load' in r and 'bookings' in r for r in timeline['rows']),timeline['rows'][:1]
    print(f"PASS: team capacity timeline returns {len(timeline['weeks'])} weeks for {len(timeline['rows'])} employees")
    # Exercise concurrent confirmation using independent cookie sessions.
    fresh=a.call(f'/projects/{pid}/solve','POST',{'strategy':'BALANCED'})
    peers=[Client(),Client()]
    for c in peers:c.login('admin',os.environ['ARO_ADMIN_PASSWORD'])
    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(lambda c:c.call(f'/resource-plans/{fresh}/confirm','POST'),peers))
    # Phase 3: AI skill draft, human-confirmed merge, history evidence / 自动能力画像。
    fresh_plan=a.call(f'/resource-plans/{fresh}')
    emp=fresh_plan['items'][0]['employee_id']
    evidence=a.call(f'/employees/{emp}/skills/evidence')
    assert evidence and evidence[0]['skill_name']=='Java' and evidence[0]['project_count']>=1,evidence
    extracted=a.call(f'/employees/{emp}/skills/ai-extract','POST',{'text':'精通 Java，负责 Vue 组件开发','source':'RESUME'})
    assert extracted['mode']=='demo',extracted
    matched=[i for i in extracted['skills'] if i['skillId']]
    assert matched and matched[0]['level']==5 and matched[0]['confidence']==0.9,extracted
    written=a.call(f'/employees/{emp}/skills/ai-accept','POST',[{'skillId':matched[0]['skillId'],'level':matched[0]['level'],'source':'RESUME','confidence':matched[0]['confidence']}])
    assert written==1
    profile={row['skillId']:row['level'] for row in a.call(f'/employees/{emp}/skills')}
    assert profile.get(matched[0]['skillId'])==5,profile
    a.call(f'/employees/{emp}/skills/ai-extract','POST',{'text':'普通文本','source':'MANAGER'},status=400)
    # 技能自动更新建议：按历史任务证据补录缺失画像 / adopt missing profile skills from history evidence
    a.call(f'/employees/{emp}/skills','PUT',[])
    target=a.call(f'/employees/{emp}/skills/evidence')[0]
    assert target['profile_level'] is None and target['suggested_level']>=3,target
    a.call(f'/employees/{emp}/skills/ai-accept','POST',[{'skillId':target['skill_id'],'level':target['suggested_level'],'source':'PROJECT','confidence':0.8}])
    assert {row['skillId']:row['level'] for row in a.call(f'/employees/{emp}/skills')}.get(target['skill_id'])==target['suggested_level']
    print('PASS: AI skill draft extraction, human-confirmed merge and history evidence')
    # Phase 4: leave triggers impact analysis; replan swaps the active set atomically / 动态重规划
    assert a.call(f'/projects/{pid}/replan/impact')['conflictCount']==0
    item=fresh_plan['items'][0]
    a.call(f'/employees/{item["employee_id"]}/availability','POST',{'startDate':item['start_date'],'endDate':item['end_date'],'type':'LEAVE','capacity':0,'remark':'smoke replan'})
    impact=a.call(f'/projects/{pid}/replan/impact')
    assert impact['conflictCount']>=1 and any(c['type']=='UNAVAILABLE' for c in impact['conflicts']),impact
    patrol=a.call('/replan/alerts')
    assert any(x['projectId']==pid and x['conflictCount']>=1 for x in patrol),patrol
    replan_id=a.call(f'/projects/{pid}/replan','POST',{'strategy':'LOWEST_RISK'})
    assert a.call(f'/resource-plans/{replan_id}')['status']=='DRAFT'
    explained=a.call(f'/resource-plans/{replan_id}/explain-diff','POST')
    assert explained['mode']=='demo' and '重规划' in explained['text'],explained
    a.call(f'/projects/{pid}/solve','POST',{'strategy':'BALANCED'},status=400)
    a.call(f'/resource-plans/{replan_id}/confirm','POST')
    assert a.call(f'/resource-plans/{replan_id}')['status']=='CONFIRMED'
    assert a.call(f'/resource-plans/{fresh}')['status']=='ARCHIVED'
    assert a.call(f'/projects/{pid}/replan/impact')['conflictCount']==0
    assert not any(x['projectId']==pid for x in a.call('/replan/alerts'))
    a.call(f'/resource-plans/{fresh}/explain-diff','POST',status=400)
    for row in a.call(f'/employees/{item["employee_id"]}/availability'):
        if row['remark']=='smoke replan': a.call(f'/employees/{item["employee_id"]}/availability/{row["id"]}','DELETE')
    a.call(f'/resource-plans/{replan_id}/cancel','POST')
    print('PASS: leave triggers impact analysis, replan swaps the active allocation set atomically')
    a.call(f'/resource-plans/{fresh}/cancel','POST')
    print('PASS: concurrent duplicate confirmation is safe')
    # 执行侧生命周期：任务完结收尾分配，项目/员工状态流转 / execution lifecycle closes the loop
    life=a.call('/projects','POST',{**project,'name':'Life '+suffix})
    life_task=a.call(f'/projects/{life}/tasks','POST',{'name':'Lifecycle deliverable','startDate':'2026-10-05','endDate':'2026-10-16','estimatedHours':24})
    life_plan=a.call(f'/projects/{life}/solve','POST',{'strategy':'BALANCED'})
    a.call(f'/resource-plans/{life_plan}/confirm','POST')
    def timeline_has(name):return name in json.dumps(a.call('/allocations/timeline'))
    assert timeline_has('Lifecycle deliverable')
    a.call(f'/tasks/{life_task}/status','POST',{'status':'BOGUS'},status=400)
    a.call(f'/tasks/{life_task}/status','POST',{'status':'DONE'})
    assert not timeline_has('Lifecycle deliverable')  # DONE 即时收尾分配、释放占用 / completing closes the booking at once
    a.call(f'/tasks/{life_task}/status','POST',{'status':'TODO'},status=400)  # 终态不可回退 / terminal
    a.call(f'/projects/{life}/status','POST',{'status':'COMPLETED'},status=400)  # PLANNING 不可直接完结
    a.call(f'/projects/{life}/status','POST',{'status':'IN_PROGRESS'})
    a.call(f'/projects/{life}/status','POST',{'status':'COMPLETED'})
    a.call(f'/projects/{life}/solve','POST',{'strategy':'BALANCED'},status=400)  # 终态项目不可编排
    assert a.call(f'/projects/{life}')['status']=='COMPLETED'
    a.call(f'/tasks/{ids[0]}/status','POST',{'status':'CANCELLED'})
    a.call(f'/tasks/{ids[1]}/status','POST',{'status':'IN_PROGRESS'})
    a.call(f'/tasks/{ids[1]}/status','POST',{'status':'DONE'})
    a.call(f'/tasks/{ids[2]}/status','POST',{'status':'DONE'})
    a.call(f'/projects/{pid}/status','POST',{'status':'IN_PROGRESS'})
    a.call(f'/projects/{pid}/status','POST',{'status':'COMPLETED'})
    assert a.call(f'/projects/{pid}')['status']=='COMPLETED'
    emp0=a.call('/employees')['list'][0]
    a.call(f'/employees/{emp0["id"]}/status','POST',{'status':'INACTIVE'})
    a.call(f'/employees/{emp0["id"]}/status','POST',{'status':'ON_LEAVE'},status=400)  # INACTIVE 仅可回归 ACTIVE
    assert [e for e in a.call('/employees')['list'] if e['id']==emp0['id']][0]['status']=='INACTIVE'
    a.call(f'/employees/{emp0["id"]}/status','POST',{'status':'ACTIVE'})
    print('PASS: execution lifecycle — task DONE closes bookings, project reaches COMPLETED, employee offboarding guarded')
    print('ALL API SMOKE CHECKS PASSED; record suffix:',suffix)
if __name__=='__main__':main()
