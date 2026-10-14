(async()=>{
const root=document.getElementById('class-explorer');if(!root)return;
const panel=document.getElementById('class-explorer-panel');if(panel&&!panel.open)await new Promise(resolve=>{const opened=()=>{if(panel.open){panel.removeEventListener('toggle',opened);resolve()}};panel.addEventListener('toggle',opened)});
const expansion=root.querySelector('#class-expansion-status');
try{
if(!window.DecompressionStream)throw Error('Browser lacks DecompressionStream');
const payload=root.querySelector('#class-payload'),size=Number(payload.dataset.size);
if(!Number.isSafeInteger(size)||size<0||size>32*1024*1024)throw Error('Invalid expansion budget');
const bytes=Uint8Array.from(atob(payload.content.textContent.trim()),c=>c.charCodeAt(0));
const reader=new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw')).getReader();
let total=0,chunks=[];
for(;;){const {done,value}=await reader.read();if(done)break;total+=value.length;if(total>size){await reader.cancel();throw Error('Expansion exceeds budget')}chunks.push(value)}
if(total!==size)throw Error('Expanded evidence size mismatch');
const decoded=new Uint8Array(total);let offset=0;for(const chunk of chunks){decoded.set(chunk,offset);offset+=chunk.length}
const digest=[...new Uint8Array(await crypto.subtle.digest('SHA-256',decoded))].map(n=>n.toString(16).padStart(2,'0')).join('');
if('sha256:'+digest!==payload.dataset.sha256)throw Error('Expanded evidence hash mismatch');
root.querySelector('#class-interactive').innerHTML=new TextDecoder('utf-8',{fatal:true}).decode(decoded);
expansion.textContent='Complete class explorer expanded locally and its size/SHA-256 verified. Detailed class views require JavaScript; the static summary and evidence below remain readable without it.';
}catch(error){expansion.textContent='Detailed class explorer unavailable: '+error.message+'. The static projection summary, representative instruction diff, source diffs, captures and JAR provenance remain readable.';return}

const select=root.querySelector('#class-projection-select'),search=root.querySelector('#class-search'),changed=root.querySelector('#class-changed-only'),status=root.querySelector('#class-selection-status');
root.classList.add('tree-ready');
let selected;
function filter(){let visible=0;root.querySelectorAll('.class-projection').forEach(p=>{p.hidden=p.dataset.projection!==select.value;if(p.hidden)return;p.querySelectorAll('.class-leaf').forEach(l=>{l.hidden=!l.dataset.path.toLowerCase().includes(search.value.toLowerCase())||(changed.checked&&['unchanged','zip-metadata'].includes(l.dataset.status));if(!l.hidden)visible++});p.querySelectorAll('.class-package').forEach(p=>p.hidden=![...p.querySelectorAll('.class-leaf')].some(l=>!l.hidden))});status.textContent=visible+' visible classes. Counts are representation lines, not semantic impact.';if(selected){selected.hidden=true;selected=undefined}}
select.addEventListener('change',filter);search.addEventListener('input',filter);changed.addEventListener('change',filter);filter();
root.querySelectorAll('.class-link').forEach(link=>link.addEventListener('click',e=>{e.preventDefault();if(selected)selected.hidden=true;selected=document.getElementById(link.hash.slice(1));selected.hidden=false;selected.querySelector('h3').textContent=link.closest('.class-leaf').dataset.path;if(link.closest('.class-leaf').dataset.status==='debug-view'){for(const d of selected.querySelectorAll('details'))if(d.querySelector('summary').textContent==='Debug table diff')d.open=true;}status.textContent=select.selectedOptions[0].textContent+' · '+link.closest('.class-leaf').dataset.path;root.querySelector('.explorer-preview').scrollTop=0;selected.querySelector('h3').tabIndex=-1;selected.querySelector('h3').focus({preventScroll:true})}));
root.querySelectorAll('.class-view').forEach(v=>v.hidden=true);
})();
