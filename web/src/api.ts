let csrf: { token:string; headerName:string } | null = null;
export async function token() {
  const response = await fetch('/api/v1/auth/csrf');
  const body = await response.json();
  if (!response.ok) throw new Error('无法建立会话，请检查后端服务');
  csrf = body.data;
  return csrf!;
}
// 非 JSON 响应（网关/代理错误页等）给出可读错误而非裸 SyntaxError / readable error for non-JSON responses
async function readJson(response:Response):Promise<any> {
  const text=await response.text();
  try { return JSON.parse(text); } catch { throw new Error('服务响应异常（HTTP '+response.status+'）'); }
}
export async function api(path:string, method='GET', data?:unknown):Promise<any> {
  const headers:Record<string,string> = { 'Accept-Language':'zh-CN' };
  if (method !== 'GET') { const t=csrf || await token(); headers[t.headerName]=t.token; headers['Content-Type']='application/json'; }
  const response = await fetch('/api/v1'+path,{ method,headers,body:data===undefined?undefined:JSON.stringify(data) });
  const body = await readJson(response);
  if (!response.ok || body.code!=='OK') { if(response.status===401) window.dispatchEvent(new Event('session-expired')); throw new Error(body.message || '请求失败'); }
  return body.data;
}
export async function login(username:string,password:string) {
  const t=await token();
  const response=await fetch('/api/v1/auth/login',{method:'POST',headers:{[t.headerName]:t.token,'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({username,password})});
  if(!response.ok) { const body=await readJson(response).catch(()=>null); throw new Error(body?.message || '用户名或密码错误'); }
  await token();
}
export async function streamPlan(id:number,onProgress:(text:string)=>void):Promise<any> {
  const t=csrf || await token();
  // 兜底超时（后端 AI 上限 75s，留足余量）/ hard timeout so a stalled stream cannot hang forever
  const response=await fetch(`/api/v1/projects/${id}/ai-plan/stream`,{method:'POST',headers:{[t.headerName]:t.token},signal:AbortSignal.timeout(180_000)});
  if(!response.ok) throw new Error((await response.json()).message || '规划请求失败');
  const reader=response.body!.getReader(),decoder=new TextDecoder(); let buffer='',result:any;
  for(;;) {
    const chunk=await reader.read(); if(chunk.done) break;
    buffer+=decoder.decode(chunk.value,{stream:true}).replace(/\r\n/g,'\n');
    let boundary:number;
    while((boundary=buffer.indexOf('\n\n'))>=0) {
      const event=buffer.slice(0,boundary); buffer=buffer.slice(boundary+2);
      const name=event.split('\n').find(l=>l.startsWith('event:'))?.slice(6).trim();
      const data=event.split('\n').filter(l=>l.startsWith('data:')).map(l=>l.slice(5).trimStart()).join('\n');
      if(name==='error') throw new Error(data);
      if(name==='progress') onProgress(data);
      if(name==='result') result=JSON.parse(data);
    }
  }
  if(!result) throw new Error('生成中断，未保存任何任务，请重试');
  return result;
}

export async function apiForm(path:string,form:FormData):Promise<any> {
  const t=csrf || await token();
  const response=await fetch('/api/v1'+path,{method:'POST',headers:{[t.headerName]:t.token,'Accept-Language':'zh-CN'},body:form});
  const body=await readJson(response);
  if (!response.ok || body.code!=='OK') { if(response.status===401) window.dispatchEvent(new Event('session-expired')); throw new Error(body.message || '请求失败'); }
  return body.data;
}
