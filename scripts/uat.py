"""Full UAT walk of every business domain, happy paths AND error scenarios.

Runs against a DEMO instance of the released image; creates labelled records —
use an isolated database. Set ARO_ADMIN_PASSWORD (required); ARO_URL optional.

    ARO_ADMIN_PASSWORD=... [ARO_URL=http://127.0.0.1:8080] python scripts/uat.py

Sections: auth & roles / org basics / availability / project-task validation /
AI planning / solver errors / gaps / plan lifecycle / execution lifecycle /
replan loop / capability / optional guards / AI profile / notify-off.
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
        return json.loads(payload).get('data')
    def login(self,name,password):
        self.csrf=self.call('/auth/csrf')
        self.opener.open(urllib.request.Request(BASE+'/auth/login',method='POST',data=urllib.parse.urlencode({'username':name,'password':password}).encode(),headers={self.csrf['headerName']:self.csrf['token']})).read()
        self.csrf=self.call('/auth/csrf')
    def upload(self,path,filename,content):
        if self.csrf is None:self.csrf=self.call('/auth/csrf')
        boundary=uuid.uuid4().hex
        body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
              f'Content-Type: text/plain\r\n\r\n{content}\r\n--{boundary}--\r\n').encode()
        headers={'Accept-Language':'zh-CN',self.csrf['headerName']:self.csrf['token'],
                 'Content-Type':f'multipart/form-data; boundary={boundary}'}
        try:
            r=self.opener.open(urllib.request.Request(BASE+path,data=body,headers=headers,method='POST'),timeout=100)
            actual=r.status;payload=r.read()
        except urllib.error.HTTPError as e:actual=e.code;payload=e.read()
        assert actual==200,(path,actual,payload.decode()[:500])
        return json.loads(payload).get('data')

def main():
    tag=uuid.uuid4().hex[:8]
    a=Client()
    # ---------------- 1 认证与权限 ----------------
    a.call('/employees',status=401)
    a.call('/auth/csrf')
    a.call('/projects','POST',{},status=403,csrf=False)
    try:
        a.login('admin','Wrong-Password-000'); raise AssertionError('wrong password accepted')
    except urllib.error.HTTPError as e: assert e.code==401,e.code
    a.login('admin',os.environ['ARO_ADMIN_PASSWORD'])
    assert a.call('/auth/me')['roles']==['ADMIN']
    pw='Uat-Password-2026!'
    for role in ['EMPLOYEE','PROJECT_MANAGER','DEPARTMENT_MANAGER']:
        name=f'uat_{role.lower()}_{tag}'
        a.call('/auth/users','POST',{'username':name,'password':pw,'role':role})
        c=Client();c.login(name,pw)
        if role=='EMPLOYEE':
            c.call('/projects','POST',{},status=403);c.call('/employees')
        if role=='PROJECT_MANAGER': c.call('/employees','POST',{},status=403)
        if role=='DEPARTMENT_MANAGER':
            c.call('/projects','POST',{},status=403);c.call('/auth/users',status=403)
    a.call('/auth/users','POST',{'username':f'uat_employee_{tag}','password':pw,'role':'EMPLOYEE'},status=409)
    a.call('/auth/users','POST',{'username':f'uat_bad_{tag}','password':pw,'role':'CEO'},status=400)
    print('PASS 1/14: auth — 401/CSRF 403, wrong password, duplicate user 409, invalid role, role boundaries')

    # ---------------- 2 组织基础数据 ----------------
    if a.call('/employees')['total']==0: a.call('/system/demo-data','POST')
    dep=a.call('/departments','POST',{'name':'UAT 部门 '+tag,'code':'UAT'+tag})
    a.call(f'/departments/{dep}','PUT',{'name':'UAT 部门改 '+tag,'code':'UAT'+tag})
    dep2=a.call('/departments','POST',{'name':'UAT 子部门 '+tag,'parentId':dep})
    a.call(f'/departments/{dep}','DELETE',status=409)
    emp1=a.call('/employees','POST',{'employeeNo':'UAT'+tag,'name':'UAT 员工','departmentId':dep2,'position':'工程师','weeklyHours':40,'defaultCapacity':100})
    a.call(f'/departments/{dep2}','DELETE',status=409)
    a.call('/employees','POST',{'employeeNo':'UAT'+tag,'name':'重复工号','departmentId':dep2,'weeklyHours':40,'defaultCapacity':100},status=409)
    a.call('/employees','POST',{'employeeNo':'X'+tag,'name':'零工时','departmentId':dep2,'weeklyHours':0,'defaultCapacity':100},status=400)
    a.call('/employees/999999999','GET',status=404)
    a.call(f'/employees/{emp1}','DELETE')
    a.call(f'/departments/{dep2}','DELETE'); a.call(f'/departments/{dep}','DELETE')
    cat=a.call('/skill-categories','POST',{'name':'UAT 分类 '+tag,'sortOrder':1})
    skill=a.call('/skills','POST',{'name':'UAT技能 '+tag,'categoryId':cat})
    a.call('/skills','POST',{'name':'UAT技能 '+tag,'categoryId':cat},status=409)
    alias=a.call(f'/skills/{skill}/aliases','POST',{'alias':'UAT别名 '+tag})
    a.call(f'/skills/{skill}/aliases/{alias}','DELETE')
    a.call(f'/skills/{skill}','DELETE')
    sk=a.call('/skills','POST',{'name':'UAT占用技能 '+tag,'categoryId':cat})
    emp=a.call('/employees','POST',{'employeeNo':'UAT2'+tag,'name':'UAT 占用人','departmentId':a.call('/departments')[0]['id'],'weeklyHours':40,'defaultCapacity':100})
    a.call(f'/employees/{emp}/skills','PUT',[{'skillId':sk,'level':3}])
    a.call(f'/skills/{sk}','DELETE',status=409)
    print('PASS 2/14: org — dept/skill CRUD, not-empty/in-use 409, duplicates, employee bounds + 404')

    # ---------------- 3 可用性窗口 ----------------
    a.call(f'/employees/{emp}/availability','POST',{'startDate':'2026-11-02','endDate':'2026-11-06','type':'LEAVE','capacity':0,'remark':'uat'})
    a.call(f'/employees/{emp}/availability','POST',{'startDate':'2026-11-06','endDate':'2026-11-02','type':'LEAVE','capacity':0},status=400)
    keep=[r['id'] for r in a.call(f'/employees/{emp}/availability') if r['remark']=='uat'][0]
    a.call(f'/employees/{emp}/availability/{keep}','DELETE')
    a.call(f'/employees/{emp}/availability/999999999','DELETE',status=404)
    print('PASS 3/14: availability — reversed range 400, delete + missing 404')

    # ---------------- 4 项目与任务校验 ----------------
    proj={'name':'UAT '+tag,'description':'UAT 主项目','startDate':'2026-10-05','endDate':'2026-10-30','priority':3}
    pid=a.call('/projects','POST',proj)
    a.call(f'/projects/{pid}/tasks','POST',{'name':'结束早于开始','startDate':'2026-10-10','endDate':'2026-10-08','estimatedHours':8},status=400)
    a.call(f'/projects/{pid}/tasks','POST',{'name':'越窗','startDate':'2026-11-01','endDate':'2026-11-05','estimatedHours':8},status=400)
    t_root=a.call(f'/projects/{pid}/tasks','POST',{'name':'父任务','startDate':'2026-10-05','endDate':'2026-10-20','estimatedHours':16})
    t_child=a.call(f'/projects/{pid}/tasks','POST',{'name':'子任务','parentId':t_root,'startDate':'2026-10-06','endDate':'2026-10-10','estimatedHours':8})
    other=a.call('/projects','POST',{**proj,'name':'UAT other '+tag})
    other_task=a.call(f'/projects/{other}/tasks','POST',{'name':'外部任务','startDate':'2026-10-05','endDate':'2026-10-05','estimatedHours':8})
    a.call(f'/projects/{pid}/tasks','POST',{'name':'跨项目父','parentId':other_task,'startDate':'2026-10-05','endDate':'2026-10-05','estimatedHours':8},status=422)
    a.call(f'/tasks/{t_root}','PUT',{'name':'父任务','parentId':t_child,'startDate':'2026-10-05','endDate':'2026-10-20','estimatedHours':16},status=400)
    a.call(f'/tasks/{t_child}/dependencies','POST',{'predecessorTaskId':t_child,'dependencyType':'FS'},status=422)
    a.call(f'/tasks/{t_child}/dependencies','POST',{'predecessorTaskId':other_task,'dependencyType':'FS'},status=422)
    a.call(f'/tasks/{t_child}/dependencies','POST',{'predecessorTaskId':t_root,'dependencyType':'XX'},status=400)  # 非法枚举 → 反序列化 400
    a.call(f'/tasks/{t_child}/skill-requirements','POST',{'skillId':999999999,'minLevel':3,'weight':1,'requirementType':'REQUIRED'},status=404)
    a.call(f'/tasks/{t_child}/skill-requirements','POST',{'skillId':sk,'minLevel':9,'weight':1,'requirementType':'REQUIRED'},status=400)
    a.call(f'/tasks/{t_child}/skill-requirements','POST',{'skillId':sk,'minLevel':3,'weight':2,'requirementType':'REQUIRED'},status=400)
    req=a.call(f'/tasks/{t_child}/skill-requirements','POST',{'skillId':sk,'minLevel':3,'weight':1,'requirementType':'PREFERRED'})
    a.call(f'/tasks/{t_child}/skill-requirements/{req}','DELETE')
    a.call(f'/projects/{pid}/milestones','POST',{'name':'UAT 里程碑','dueDate':'2026-10-20','sortOrder':1})
    a.call(f'/projects/{pid}','PUT',{**proj,'name':'UAT 缩窗 '+tag,'endDate':'2026-10-08'},status=400)
    a.call(f'/projects/{pid}','PUT',{**proj,'endDate':'2026-10-31'})
    print('PASS 4/14: project & task — windows, parent cycles 400/422, dependency & requirement bounds, shrink guard')

    # ---------------- 5 AI 规划（demo） ----------------
    ai_proj={'name':'UAT AI '+tag,'description':'建设员工能力平台','startDate':'2026-10-05','endDate':'2026-10-30','priority':3}
    aid=a.call('/projects','POST',ai_proj)
    draft=a.call(f'/projects/{aid}/ai-plan','POST')
    assert len(draft['tasks'])==3
    bad=json.loads(json.dumps(draft));bad['tasks'][0]['startDate']='2026-12-01';bad['tasks'][0]['endDate']='2026-12-05'
    a.call(f'/projects/{aid}/ai-plan/accept','POST',bad,status=400)
    ids=a.call(f'/projects/{aid}/ai-plan/accept','POST',draft)
    a.call(f'/projects/{aid}/ai-plan/accept','POST',draft,status=400)
    print('PASS 5/14: AI planning — out-of-window draft rejected, atomic accept, duplicate guarded')

    # ---------------- 6 求解错误场景 ----------------
    empty=a.call('/projects','POST',{**ai_proj,'name':'UAT empty '+tag})
    a.call(f'/projects/{empty}/solve','POST',{'strategy':'BALANCED'},status=400)
    nodate=a.call('/projects','POST',{'name':'UAT 无日期 '+tag,'description':'x','priority':3})
    a.call(f'/projects/{nodate}/tasks','POST',{'name':'t','startDate':'2026-10-05','endDate':'2026-10-05','estimatedHours':8})
    a.call(f'/projects/{nodate}/solve','POST',{'strategy':'BALANCED'},status=400)
    span=a.call('/projects','POST',{'name':'UAT 超两年 '+tag,'description':'x','startDate':'2026-01-01','endDate':'2029-01-01','priority':3})
    a.call(f'/projects/{span}/tasks','POST',{'name':'t','startDate':'2026-01-05','endDate':'2026-01-09','estimatedHours':8})
    a.call(f'/projects/{span}/solve','POST',{'strategy':'BALANCED'},status=400)
    wknd=a.call('/projects','POST',{'name':'UAT 纯周末 '+tag,'description':'x','startDate':'2026-10-10','endDate':'2026-10-11','priority':3})
    a.call(f'/projects/{wknd}/tasks','POST',{'name':'t','startDate':'2026-10-10','endDate':'2026-10-11','estimatedHours':8})
    a.call(f'/projects/{wknd}/solve','POST',{'strategy':'BALANCED'},status=400)
    fs=a.call('/projects','POST',{**ai_proj,'name':'UAT FS '+tag})
    f1=a.call(f'/projects/{fs}/tasks','POST',{'name':'前置','startDate':'2026-10-05','endDate':'2026-10-09','estimatedHours':8})
    f2=a.call(f'/projects/{fs}/tasks','POST',{'name':'后继','startDate':'2026-10-05','endDate':'2026-10-09','estimatedHours':8})
    a.call(f'/tasks/{f2}/dependencies','POST',{'predecessorTaskId':f1,'dependencyType':'FS'})  # 创建放行，日期校验在求解期
    a.call(f'/projects/{fs}/solve','POST',{'strategy':'BALANCED'},status=400)                    # FS 日期不满足 → 求解拒绝
    a.call(f'/projects/{pid}/solve','POST',{'strategy':'FASTEST'},status=400)
    bulk=a.call('/projects','POST',{'name':'UAT 201 '+tag,'description':'x','startDate':'2026-10-05','endDate':'2026-10-30','priority':3})
    for i in range(201): a.call(f'/projects/{bulk}/tasks','POST',{'name':f'b{i}','startDate':'2026-10-05','endDate':'2026-10-09','estimatedHours':8})
    a.call(f'/projects/{bulk}/solve','POST',{'strategy':'BALANCED'},status=400)
    print('PASS 6/14: solver guards — empty/no-dates/2y-span/weekend-only/FS/strategy/201-tasks rejected')

    # ---------------- 7 缺口显性化 ----------------
    gap_p=a.call('/projects','POST',{**ai_proj,'name':'UAT gap '+tag})
    a.call(f'/projects/{gap_p}/tasks','POST',{'name':'不可能负载','startDate':'2026-10-05','endDate':'2026-10-05','estimatedHours':10000})
    g1=a.call(f'/projects/{gap_p}/solve','POST',{'strategy':'BALANCED'})
    assert len(a.call(f'/resource-plans/{g1}')['gaps'])==1
    a.call(f'/resource-plans/{g1}/confirm','POST',status=400)
    rare=a.call('/skills','POST',{'name':'UAT稀有 '+tag,'categoryId':cat})
    sg=a.call('/projects','POST',{**ai_proj,'name':'UAT 稀有 '+tag})
    st=a.call(f'/projects/{sg}/tasks','POST',{'name':'无人可担','startDate':'2026-10-05','endDate':'2026-10-05','estimatedHours':8})
    a.call(f'/tasks/{st}/skill-requirements','POST',{'skillId':rare,'minLevel':5,'weight':1,'requirementType':'REQUIRED'})
    g2=a.call(f'/projects/{sg}/solve','POST',{'strategy':'BALANCED'})
    assert a.call(f'/resource-plans/{g2}')['gaps'][0]['missingSkills'][0]['skillId']==rare
    a.call(f'/resource-plans/{g1}/cancel','POST'); a.call(f'/resource-plans/{g2}/cancel','POST')
    print('PASS 7/14: gaps — infeasible workload & unstaffable skill surface as gaps; confirmation blocked')

    # ---------------- 8 方案生命周期 ----------------
    plan_id=a.call(f'/projects/{aid}/solve','POST',{'strategy':'BALANCED'})
    cands=a.call(f'/projects/{aid}/candidates')
    a.call(f'/resource-plans/{plan_id}/items','PUT',cands[:2],status=400)
    a.call(f'/resource-plans/{plan_id}/items','PUT',[{'taskId':c['taskId'],'employeeId':999999999} for c in cands],status=400)
    a.call(f'/resource-plans/{plan_id}/items','PUT',[{'taskId':c['taskId'],'employeeId':c['candidates'][-1]['employeeId']} for c in cands])  # 合法编辑须紧随求解（哈希为全库口径）
    a.call(f'/tasks/{ids[0]}','PUT',{'name':draft['tasks'][0]['name'],'startDate':draft['tasks'][0]['startDate'],'endDate':draft['tasks'][0]['endDate'],'estimatedHours':draft['tasks'][0]['estimatedHours'],'priority':3})  # 草稿期任务可编辑（无生效分配，但会使草稿过期）
    fresh_plan=a.call(f'/projects/{aid}/solve','POST',{'strategy':'BALANCED'})             # 上一编辑已变哈希 → 重解新草稿
    a.call(f'/resource-plans/{fresh_plan}/confirm','POST')
    a.call(f'/resource-plans/{fresh_plan}/confirm','POST')
    a.call(f'/projects/{aid}/solve','POST',{'strategy':'BALANCED'},status=400)
    a.call(f'/tasks/{ids[0]}','PUT',{'name':draft['tasks'][0]['name'],'startDate':draft['tasks'][0]['startDate'],'endDate':draft['tasks'][0]['endDate'],'estimatedHours':draft['tasks'][0]['estimatedHours'],'priority':3},status=400)
    rp=a.call(f'/projects/{aid}/replan','POST',{'strategy':'LOWEST_RISK'})
    a.call(f'/employees/{emp}/availability','POST',{'startDate':'2026-10-05','endDate':'2026-10-30','type':'LEAVE','capacity':0,'remark':'uat stale'})
    a.call(f'/resource-plans/{rp}/confirm','POST',status=409)
    for row in a.call(f'/employees/{emp}/availability'):
        if row['remark']=='uat stale': a.call(f'/employees/{emp}/availability/{row["id"]}','DELETE')
    a.call(f'/resource-plans/{rp}/cancel','POST'); a.call(f'/resource-plans/{rp}/cancel','POST')
    ovp=a.call('/projects','POST',{**ai_proj,'name':'UAT over '+tag})
    a.call(f'/projects/{ovp}/tasks','POST',{'name':'同日甲','startDate':'2026-10-05','endDate':'2026-10-05','estimatedHours':8})
    a.call(f'/projects/{ovp}/tasks','POST',{'name':'同日乙','startDate':'2026-10-05','endDate':'2026-10-05','estimatedHours':8})
    op=a.call(f'/projects/{ovp}/solve','POST',{'strategy':'BALANCED'})
    oc=a.call(f'/projects/{ovp}/candidates')
    same=oc[0]['candidates'][0]['employeeId']
    a.call(f'/resource-plans/{op}/items','PUT',[{'taskId':c['taskId'],'employeeId':same} for c in oc],status=400)  # 同日双任务压同一人 → 超配
    a.call(f'/resource-plans/{op}/cancel','POST')
    a.call(f'/resource-plans/{op}/items','PUT',[{'taskId':c['taskId'],'employeeId':same} for c in oc],status=400)  # 撤销后不可再编辑
    peers=[Client(),Client()]
    for c in peers: c.login('admin',os.environ['ARO_ADMIN_PASSWORD'])
    fresh=a.call(f'/projects/{aid}/replan','POST',{'strategy':'BALANCED'})
    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(lambda c:c.call(f'/resource-plans/{fresh}/confirm','POST'),peers))  # 幂等：两次都 200
    a.call(f'/resource-plans/{fresh}/cancel','POST')
    print('PASS 8/14: plan lifecycle — edit guards, draft-period edit allowed, idempotent confirm/cancel, lock, stale 409')

    # ---------------- 9 执行侧生命周期 ----------------
    life=a.call('/projects','POST',{**ai_proj,'name':'UAT life '+tag})
    lr=a.call(f'/projects/{life}/tasks','POST',{'name':'生命周期父'+tag,'startDate':'2026-10-05','endDate':'2026-10-20','estimatedHours':16})
    lc=a.call(f'/projects/{life}/tasks','POST',{'name':'生命周期子'+tag,'parentId':lr,'startDate':'2026-10-06','endDate':'2026-10-09','estimatedHours':8})
    a.call(f'/tasks/{lr}/status','POST',{'status':'DONE'},status=400)
    lp=a.call(f'/projects/{life}/solve','POST',{'strategy':'BALANCED'})
    a.call(f'/resource-plans/{lp}/confirm','POST')
    def booked(name): return name in json.dumps(a.call('/allocations/timeline'), ensure_ascii=False)
    assert booked('生命周期子'+tag)
    a.call(f'/tasks/{lc}/status','POST',{'status':'BOGUS'},status=400)
    a.call(f'/tasks/{lc}/status','POST',{'status':'DONE'})
    assert not booked('生命周期子'+tag)
    a.call(f'/tasks/{lc}/status','POST',{'status':'TODO'},status=400)
    a.call(f'/tasks/{lr}/status','POST',{'status':'DONE'})
    a.call(f'/projects/{life}/status','POST',{'status':'COMPLETED'},status=400)
    a.call(f'/projects/{life}/status','POST',{'status':'IN_PROGRESS'})
    a.call(f'/projects/{life}/status','POST',{'status':'ON_HOLD'})
    a.call(f'/projects/{life}/replan','POST',{'strategy':'BALANCED'},status=400)
    a.call(f'/projects/{life}/status','POST',{'status':'IN_PROGRESS'})
    a.call(f'/projects/{life}/status','POST',{'status':'COMPLETED'})
    a.call(f'/projects/{life}/solve','POST',{'strategy':'BALANCED'},status=400)
    a.call(f'/tasks/{lr}/status','POST',{'status':'TODO'},status=400)
    stop=a.call('/projects','POST',{**ai_proj,'name':'UAT stop '+tag})
    spt=a.call(f'/projects/{stop}/tasks','POST',{'name':'停用验证','startDate':'2026-10-05','endDate':'2026-10-09','estimatedHours':8})
    a.call(f'/tasks/{spt}/skill-requirements','POST',{'skillId':sk,'minLevel':3,'weight':1,'requirementType':'REQUIRED'})
    sp=a.call(f'/projects/{stop}/solve','POST',{'strategy':'BEST_SKILL_MATCH'})
    a.call(f'/resource-plans/{sp}/confirm','POST')
    victim=[i for i in a.call(f'/resource-plans/{sp}')['items'] if i['task_id']==spt][0]['employee_id']
    a.call(f'/employees/{victim}','DELETE',status=409)                                   # 有分配删除被 FK 拦截
    a.call(f'/employees/{victim}/status','POST',{'status':'INACTIVE'})
    assert any(c['type']=='EMPLOYEE_INACTIVE' for c in a.call(f'/projects/{stop}/replan/impact')['conflicts'])
    a.call(f'/employees/{victim}/status','POST',{'status':'ON_LEAVE'},status=400)
    a.call(f'/employees/{victim}/status','POST',{'status':'ACTIVE'})
    assert a.call(f'/projects/{stop}/replan/impact')['conflictCount']==0
    a.call(f'/resource-plans/{sp}/cancel','POST')
    a.call(f'/tasks/{spt}/status','POST',{'status':'DONE'})
    a.call(f'/projects/{stop}/status','POST',{'status':'IN_PROGRESS'})
    a.call(f'/projects/{stop}/status','POST',{'status':'COMPLETED'})
    print('PASS 9/14: lifecycle — parent/terminal guards, DONE frees bookings, ON_HOLD/COMPLETED block solving, offboarding 409→conflict→return')

    # ---------------- 10 重规划环 ----------------
    rl=a.call('/projects','POST',{**ai_proj,'name':'UAT replan '+tag})
    draft2=a.call(f'/projects/{rl}/ai-plan','POST')
    a.call(f'/projects/{rl}/ai-plan/accept','POST',draft2)
    r1=a.call(f'/projects/{rl}/solve','POST',{'strategy':'BALANCED'})
    a.call(f'/projects/{rl}/replan/impact',status=400)                                   # 草稿期无生效方案
    a.call(f'/resource-plans/{r1}/confirm','POST')
    item=a.call(f'/resource-plans/{r1}')['items'][0]
    a.call(f"/employees/{item['employee_id']}/availability",'POST',{'startDate':item['start_date'],'endDate':item['end_date'],'type':'LEAVE','capacity':0,'remark':'uat replan'})
    assert any(c['type']=='UNAVAILABLE' for c in a.call(f'/projects/{rl}/replan/impact')['conflicts'])
    assert any(x['projectId']==rl for x in a.call('/replan/alerts'))
    r2=a.call(f'/projects/{rl}/replan','POST',{'strategy':'LOWEST_RISK'})
    ex=a.call(f'/resource-plans/{r2}/explain-diff','POST')
    assert ex['mode']=='demo' and ex['text']
    a.call(f'/resource-plans/{r2}/confirm','POST')
    assert a.call(f'/resource-plans/{r1}')['status']=='ARCHIVED'
    assert a.call(f'/projects/{rl}/replan/impact')['conflictCount']==0
    a.call(f"/employees/{item['employee_id']}/availability/{[r['id'] for r in a.call(f'/employees/{item['employee_id']}/availability') if r['remark']=='uat replan'][0]}",'DELETE')
    active_item=a.call(f'/resource-plans/{r2}')['items'][0]
    v=active_item['employee_id']
    a.call(f'/employees/{v}/skills','PUT',[])
    assert any(c['type']=='SKILL_DRIFT' for c in a.call(f'/projects/{rl}/replan/impact')['conflicts'])
    a.call(f'/projects/{rl}','PUT',{**ai_proj,'name':'UAT replan '+tag,'endDate':'2026-10-06'},status=400)  # 窗口收缩被任务日期拦截（防御性约束）
    a.call(f'/resource-plans/{r2}/cancel','POST')
    a.call(f'/projects/{rl}/replan/impact',status=400)
    a.call(f'/resource-plans/compare?left={r1}&right={r2}')
    other_plan=a.call(f'/projects/{aid}/resource-plans')[0]['id']
    a.call(f'/resource-plans/compare?left={r1}&right={other_plan}',status=400)
    print('PASS 10/14: replan — impact-before-confirm 400, leave/skill-drift conflicts, alerts, diff, atomic swap, cross-compare 400')

    # ---------------- 11 能力决策 ----------------
    sd=a.call('/capability/supply-demand?weeks=12')
    a.call('/capability/supply-demand?weeks=12&model=weekly')
    a.call('/capability/supply-demand?weeks=12&model=bogus',status=400)
    assert isinstance(a.call('/capability/key-people'),list)
    a.call('/capability/leave-impact?employeeId=999999999',status=400)
    assert 'rows' in a.call('/capability/scenario') and 'rows' in a.call('/capability/trends')
    a.call('/capability/market/import','POST',{'source':'uat','items':[{'skillId':999999999,'demandIndex':50}]},status=400)
    any_skill=sd['rows'][0]['skillId'] if sd['rows'] else a.call('/skills')[0]['id']
    a.call('/capability/market/import','POST',{'source':'uat','items':[{'skillId':any_skill,'demandIndex':90,'salaryMin':10000,'salaryMax':20000,'hiringLeadWeeks':4}]})
    a.call(f'/capability/market/{any_skill}','DELETE')
    a.call(f'/capability/market/{any_skill}','DELETE',status=400)
    adv=a.call('/capability/advise?weeks=12','POST')
    assert adv['mode']=='demo' and adv['text']
    print('PASS 11/14: capability — flat/weekly/bogus, key people, leave-impact 404, market lifecycle, advice')

    # ---------------- 12 可选能力守卫 ----------------
    a.call('/skills/semantic?q=java',status=400)
    a.call('/skills/semantic/rebuild','POST',status=400)
    a.call('/sync/jira/projects',status=400)
    a.call('/sync/bogus/projects',status=400)
    assert isinstance(a.call('/replan/triggers'),list)
    tl=a.call('/allocations/timeline')
    assert tl['weeks'] and 'rows' in tl
    print('PASS 12/14: optional guards (semantic/sync 400), trigger log, timeline')

    # ---------------- 13 AI 画像 ----------------
    any_emp=a.call('/employees')['list'][0]['id']
    ex1=a.call(f'/employees/{any_emp}/skills/ai-extract','POST',{'text':'精通 Java 与 Spring Boot','source':'RESUME'})
    assert ex1['mode']=='demo' and ex1['skills']
    a.call(f'/employees/{any_emp}/skills/ai-extract','POST',{'text':'无技能文本','source':'BOGUS'},status=400)
    m=[i for i in ex1['skills'] if i['skillId']]
    if m: a.call(f'/employees/{any_emp}/skills/ai-accept','POST',[{'skillId':m[0]['skillId'],'level':m[0]['level'],'source':'RESUME','confidence':m[0]['confidence']}])
    ex2=a.upload(f'/employees/{any_emp}/skills/ai-extract-file','resume.txt','精通 Java，负责 Spring Boot 服务开发')
    assert ex2['skills']
    assert isinstance(a.call(f'/employees/{any_emp}/skills/evidence'),list)
    print('PASS 13/14: AI profile — extract text+file, bad source 400, human-confirmed accept, evidence')

    # ---------------- 14 通知（off 模式） ----------------
    st=a.call('/system/notify/status')
    assert st['mode']=='off',st
    assert isinstance(a.call('/system/notify/log'),list)
    print('PASS 14/14: notify — off-mode status and log listing')
    print(f'UAT ALL PASSED; tag: {tag}')

if __name__=='__main__':main()
