"""Local OpenAI-compatible contract fixture. No external model requests. Test use only."""
import json
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        assert self.path=='/v1/chat/completions',self.path
        if self.headers.get('Transfer-Encoding','').lower()=='chunked':
            body=b''
            while True:
                length=int(self.rfile.readline().strip().split(b';')[0],16)
                if not length:self.rfile.readline();break
                body+=self.rfile.read(length);self.rfile.read(2)
        else:body=self.rfile.read(int(self.headers['Content-Length']))
        request=json.loads(body)
        content=request['messages'][-1]['content']
        data=json.loads(content)
        if 'project' in data:
            project=data['project'];catalog=data['skillCatalog']
            response=json.dumps({'summary':'本地协议测试输出','tasks':[{'name':'协议验证任务','description':'仅用于验证模型适配','estimatedHours':8,'startDate':project['startDate'],'endDate':project['endDate'],'skills':[{'skillId':catalog[0]['id'],'minLevel':3,'weight':1,'requirementType':'REQUIRED'}] if catalog else [],'predecessorIndexes':[]}]},ensure_ascii=False)
        else:response='本地协议测试解释：依据已分配任务与容量给出建议。'
        payload=json.dumps({'id':'fixture-1','object':'chat.completion','created':1,'model':'local-fixture','choices':[{'index':0,'message':{'role':'assistant','content':response},'finish_reason':'stop'}],'usage':{'prompt_tokens':10,'completion_tokens':20,'total_tokens':30}}).encode()
        self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(payload)));self.end_headers();self.wfile.write(payload)
    def log_message(self,*args):pass
if __name__=='__main__':ThreadingHTTPServer(('127.0.0.1',18089),Handler).serve_forever()
