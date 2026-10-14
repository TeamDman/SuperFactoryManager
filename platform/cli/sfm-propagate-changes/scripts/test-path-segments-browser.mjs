import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import http from 'node:http';
import {spawn} from 'node:child_process';
const repo=path.resolve(process.argv[2]||'.');
const script=await fs.readFile(path.join(repo,'src/release_report/templates/review.js'),'utf8');
const css=await fs.readFile(path.join(repo,'src/release_report/templates/review.css'),'utf8');
const cases=[
['namespace','data/minecraft/advancement/recipes/misc/tough_cable_horizontal.json','data/sfm/advancement/recipes/misc/tough_cable_horizontal.json',['minecraft'],['sfm']],
['insert','a/b/file.json','a/new/b/file.json',[],['new']],
['delete','a/old/b/file.json','a/b/file.json',['old'],[]],
['repeated','a/x/x/b/file.json','a/x/b/file.json',['x'],[]],
['unicode','数据/旧/🧪/文件.json','数据/新/🧪/文件.json',['旧'],['新']],
['multiple','a/old/shared/old2/file.json','a/new/shared/new2/file.json',['old','old2'],['new','new2']],
['identical','a/同じ/file.json','a/同じ/file.json',[],[]],
['added',null,'a/new/file.json',['(absent)'],['a','new','file.json']],
['removed','a/old/file.json',null,['a','old','file.json'],['(absent)']],
['repeated-interior','a/x/y/x/z/file.json','a/y/x/y/z/file.json',['x'],['y']],
];
const payload={comparison_identity:'segment-alignment-fixture',provenance:{fixture:true},files:cases.map(([id,old_path,new_path])=>({id,old_path,new_path,identity:id,scope:'fixture',extension:'json',status:old_path===new_path?'exact':'moved'}))};
const fixtureEvidence=numbers=>({schema:'sfm:related-references@1',references:numbers.map(number=>({kind:'issue',owner:'TeamDman',repository:'SuperFactoryManager',number})),projection_key:'fixture',context_sha256:'sha256:'+'a'.repeat(64),declarations:{'assets/path-issues.liquid':'sha256:'+'b'.repeat(64)}});
payload.files[0].new_related_references=fixtureEvidence([527,621]);
payload.files.find(f=>f.id==='multiple').old_related_references=fixtureEvidence([610]);
payload.files.find(f=>f.id==='multiple').new_related_references=fixtureEvidence([527,621]);
payload.files.find(f=>f.id==='identical').new_related_references=fixtureEvidence([]);
payload.files.find(f=>f.id==='added').new_related_references={...fixtureEvidence([]),references:['issue','pull_request','discussion'].map(kind=>({kind,owner:'Other',repository:'Repository',number:42}))};
const fixture='<!doctype html><meta charset="utf-8"><title>Path segment alignment</title><style>body{background:#101820;color:#e6edf3;font:16px system-ui;padding:24px}.code-diff{margin:28px 0}'+css+'</style><template id="comparison-review-payload">'+JSON.stringify(payload)+'</template>'+cases.map(([id])=>'<section class="code-diff" id="'+id+'"><h2>'+id+'</h2></section>').join('')+'<script>'+script+'</script>';
await fs.writeFile('path-segment-fixture.html',fixture);
const actualPath=process.argv[3]||process.env.SFM_PATH_ACTUAL;
if(!actualPath)throw Error('Provide CLI crate root and existing article HTML path');
let actual=await fs.readFile(actualPath,'utf8'),replaced=0;
actual=actual.replace(/<script(?:\s[^>]*)?>[\s\S]*?<\/script>/g,block=>{if(block.includes("const template=document.getElementById('comparison-review-payload')")){replaced++;return '<script>'+script+'</script>'}return block});
assert.equal(replaced,1,'Replace only the reusable review script in a verification copy');
await fs.writeFile('path-segment-actual-review.html',actual);
const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(req.url==='/actual'?actual:fixture)});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const origin='http://127.0.0.1:'+server.address().port;
const profile=await fs.mkdtemp(path.join(process.cwd(),'.path-segment-edge-'));
const browser=spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',['--headless=new','--remote-debugging-port=0','--user-data-dir='+profile,'--no-first-run','--no-default-browser-check','--window-size=1440,1000','about:blank'],{windowsHide:true,stdio:'ignore'});
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(fn){const start=Date.now();for(;;){try{const value=await fn();if(value)return value}catch{}if(Date.now()-start>30000)throw Error('Browser wait timed out');await delay(100)}}
let socket,id=0;const pending=new Map(),errors=[];
async function command(method,params={}){const request=++id;return await new Promise((resolve,reject)=>{const timer=setTimeout(()=>{pending.delete(request);reject(Error(method+' timed out'))},30000);pending.set(request,{resolve:r=>{clearTimeout(timer);resolve(r)},reject});socket.send(JSON.stringify({id:request,method,params}))})}
async function evaluate(expression){const r=await command('Runtime.evaluate',{expression:'(()=>{'+expression+'})()',returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value}
async function navigate(url){await command('Page.navigate',{url});await until(()=>evaluate('return !!document.getElementById("review-counts")'))}
const results=[];
try{
const port=await until(async()=>Number((await fs.readFile(path.join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0]));
const pages=await(await fetch('http://127.0.0.1:'+port+'/json/list')).json();socket=new WebSocket(pages.find(p=>p.type==='page').webSocketDebuggerUrl);await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
socket.onmessage=event=>{const data=JSON.parse(event.data);if(data.id){const entry=pending.get(data.id);if(!entry)return;pending.delete(data.id);if(data.error)entry.reject(Error(JSON.stringify(data.error)));else entry.resolve(data.result)}else if(data.method==='Runtime.exceptionThrown')errors.push(data.params.exceptionDetails)};
await command('Runtime.enable');await command('Page.enable');await navigate(origin);
for(const [caseId,old,newPath,expectedOld,expectedNew] of cases){const lines=await evaluate('return [...document.querySelectorAll("#'+caseId+' .review-paths p")].map(p=>({text:p.textContent,changed:[...p.querySelectorAll(".path-changed")].map(s=>s.textContent),common:[...p.querySelectorAll(".path-common")].map(s=>s.textContent)}))');assert.deepEqual(lines.map(l=>l.changed),[expectedOld,expectedNew],caseId);assert.equal(lines[0].text,'Old: '+(old||'(absent)'));assert.equal(lines[1].text,'New: '+(newPath||'(absent)'));results.push({case:caseId,lines});console.log('PASS '+caseId)}
const namespace=await evaluate('const b=document.querySelector("#namespace .review-references");return {text:b.textContent,links:[...b.querySelectorAll("a")].map(a=>({text:a.textContent,href:a.href})),evidence:b.querySelector("pre").textContent}');
assert.ok(namespace.text.includes('Old: Unavailable'));assert.deepEqual(namespace.links,[{text:'issue TeamDman/SuperFactoryManager#527',href:'https://github.com/TeamDman/SuperFactoryManager/issues/527'},{text:'issue TeamDman/SuperFactoryManager#621',href:'https://github.com/TeamDman/SuperFactoryManager/issues/621'}]);assert.ok(!namespace.text.includes('#610'));assert.ok(namespace.evidence.includes('assets/path-issues.liquid'));results.push({case:'recipe-association-and-source-evidence',...namespace});
assert.deepEqual(await evaluate('return [...document.querySelectorAll("#multiple .review-references p")].slice(1).map(p=>[...p.querySelectorAll("a")].map(a=>a.textContent))'),[['issue TeamDman/SuperFactoryManager#610'],['issue TeamDman/SuperFactoryManager#527','issue TeamDman/SuperFactoryManager#621']]);
assert.ok((await evaluate('return document.querySelector("#identical .review-references").textContent')).includes('Unassociated: no active reference declarations'));results.push({case:'independent-old-new-and-known-empty'});
const typedLinks=await evaluate('return [...document.querySelectorAll("#added .reference-link")].map(a=>a.href)');assert.deepEqual(typedLinks,['https://github.com/Other/Repository/issues/42','https://github.com/Other/Repository/pull/42','https://github.com/Other/Repository/discussions/42']);results.push({case:'typed-reference-kind-and-repository-routes',links:typedLinks});
await navigate(origin+'/actual');
const actualResult=await evaluate('const payload=JSON.parse(document.getElementById("comparison-review-payload").content.textContent);const file=payload.files.find(f=>f.old_path?.endsWith("data/minecraft/advancement/recipes/misc/tough_cable_horizontal.json")&&f.new_path?.endsWith("data/sfm/advancement/recipes/misc/tough_cable_horizontal.json"));if(!file)throw Error("Actual recipe move missing");document.querySelector("a[href=\\\"#"+file.id+"\\\"]").click();const box=document.querySelector("#"+file.id+" .review-paths");box.scrollIntoView({block:"center"});return {id:file.id,lines:[...box.querySelectorAll("p")].map(p=>({text:p.textContent,changed:[...p.querySelectorAll(".path-changed")].map(s=>s.textContent),common:[...p.querySelectorAll(".path-common")].map(s=>s.textContent)}))}');
assert.deepEqual(actualResult.lines.map(l=>l.changed),[['minecraft'],['sfm']]);assert.deepEqual(actualResult.lines[0].common.slice(-5),['data','advancement','recipes','misc','tough_cable_horizontal.json']);
const frozenIssues=await evaluate('return document.querySelector("#'+actualResult.id+' .review-references").textContent');assert.ok(frozenIssues.includes('Old: Unavailable')&&frozenIssues.includes('New: Unavailable'));assert.ok(!frozenIssues.includes('#610'));results.push({case:'actual-frozen-provenance-unavailable',text:frozenIssues});
results.push({case:'actual-recipe-move',...actualResult});console.log('PASS actual recipe move DOM');

assert.equal(errors.length,0,JSON.stringify(errors));await fs.writeFile('path-segment-browser-results.json',JSON.stringify({checks:results,runtimeErrors:errors,screenshotsCaptured:0,actualSource:actualPath},null,2));
}finally{if(socket?.readyState===WebSocket.OPEN){try{await command('Browser.close')}catch{}socket.close()}browser.kill();server.close()}
