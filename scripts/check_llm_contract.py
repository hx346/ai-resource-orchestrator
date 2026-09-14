"""Run against an app configured with AI_MODE=live and the local llm_fixture.py server."""
import os,uuid
from smoke import Client
def main():
    c=Client();c.login('admin',os.environ['ARO_ADMIN_PASSWORD'])
    assert c.call('/ai/status')['mode']=='live'
    project=c.call('/projects','POST',{'name':'LLM contract '+uuid.uuid4().hex[:8],'description':'协议测试','startDate':'2026-12-07','endDate':'2026-12-07','priority':3})
    draft=c.call(f'/projects/{project}/ai-plan','POST')
    assert draft['summary']=='本地协议测试输出'
    c.call(f'/projects/{project}/ai-plan/accept','POST',draft)
    plan=c.call(f'/projects/{project}/solve','POST',{'strategy':'BALANCED'})
    review=c.call(f'/resource-plans/{plan}/review','POST')
    assert review['model']=='local-fixture' and review['mode']=='live'
    c.call(f'/resource-plans/{plan}/cancel','POST')
    print('PASS: real Spring AI OpenAI-compatible HTTP request, structured output parsing, acceptance and review (local fixture; no external model)')
if __name__=='__main__':main()
