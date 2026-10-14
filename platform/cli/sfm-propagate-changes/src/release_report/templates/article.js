(()=>{
document.body.classList.add('js-ready');
const dialog=document.getElementById('capture-dialog');
let opener=null,scrollY=0,overflow='',scrollBehavior='';
function openCapture(figure,trigger){
 const original=figure?.querySelector('img');if(!original)return;
 opener=trigger;scrollY=window.scrollY;overflow=document.body.style.overflow;scrollBehavior=document.documentElement.style.scrollBehavior;
 const caption=figure.querySelector('figcaption')?.cloneNode(true);caption?.querySelectorAll('button,details').forEach(e=>e.remove());
 document.getElementById('capture-dialog-caption').textContent=(figure.querySelector('.figure-label')?.textContent||'Evidence capture')+' · '+(caption?.textContent.trim()||original.alt);
 const image=original.cloneNode();for(const name of ['id','tabindex','role','aria-haspopup','aria-label','data-enlarge','loading'])image.removeAttribute(name);
 image.addEventListener('click',()=>dialog.close());
 document.getElementById('capture-dialog-image').replaceChildren(image);
 document.body.style.overflow='hidden';dialog.showModal();
}
for(const image of document.querySelectorAll('figure img')){
 image.dataset.enlarge='';image.tabIndex=0;image.setAttribute('role','button');image.setAttribute('aria-haspopup','dialog');image.setAttribute('aria-label','Enlarge evidence image: '+image.alt);
 image.addEventListener('click',()=>openCapture(image.closest('figure'),image));
 image.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();openCapture(image.closest('figure'),image)}});
}
document.querySelectorAll('[data-capture]').forEach(b=>b.addEventListener('click',()=>openCapture(document.getElementById(b.dataset.capture),b)));
document.getElementById('close-capture').addEventListener('click',()=>dialog.close());
dialog.addEventListener('close',()=>{
 document.body.style.overflow=overflow;document.documentElement.style.scrollBehavior='auto';
 opener?.focus({preventScroll:true});window.scrollTo({top:scrollY,behavior:'instant'});document.documentElement.style.scrollBehavior=scrollBehavior;
});
document.querySelectorAll('a[href^="#"]').forEach(a=>a.addEventListener('click',()=>{const target=document.getElementById(a.hash.slice(1));if(!target)return;for(let p=target.parentElement;p;p=p.parentElement)if(p.tagName==='DETAILS')p.open=true}));
})();

(()=>{const nav=document.querySelector('.article-nav'),menu=document.querySelector('.contents-menu');function update(){let chosen=document.querySelector('#release-story>section');for(const s of document.querySelectorAll('#release-story>section,#technical-appendix'))if(s.getBoundingClientRect().top<=130)chosen=s;nav.querySelectorAll('a').forEach(a=>{if(a.hash==='#'+chosen.id)a.setAttribute('aria-current','location');else a.removeAttribute('aria-current')})}window.addEventListener('scroll',update,{passive:true});window.addEventListener('hashchange',update);if(innerWidth<1250)menu.open=false;matchMedia('(max-width:1249px)').addEventListener('change',e=>{menu.open=!e.matches});nav.addEventListener('click',e=>{if(e.target.closest('a')&&innerWidth<1250)menu.open=false});update();
function select(id){const section=document.getElementById(id)?.closest('.code-diff');if(!section)return false;id=section.id;const pane=section.closest('.source-preview');if(!pane)return false;const bench=pane.closest('.source-workbench');pane.querySelectorAll(':scope>.code-diff').forEach(s=>s.classList.toggle('source-selected',s===section));bench.querySelectorAll('.source-link').forEach(a=>a.setAttribute('aria-current',String(a.hash==='#'+id)));bench.dataset.selected=id;const selectedLink=bench.querySelector('.source-link[aria-current="true"]');const selectedVersion=selectedLink?.closest('[data-version]')?.dataset.version;if(selectedVersion){bench.sourceSelections??=new Map();bench.sourceSelections.set(selectedVersion,id)}const side=bench.querySelector('.source-sidebar');if(selectedLink&&selectedLink.getClientRects().length)side.scrollTop+=selectedLink.getBoundingClientRect().top-side.getBoundingClientRect().top-side.clientHeight/2;pane.scrollTop=0;return true}
const version=document.querySelector('#source-version'),release=document.querySelector('.release-workbench');function changeVersion(){const v=version.value;release.querySelectorAll('.source-sidebar [data-version]').forEach(t=>t.hidden=t.dataset.version!==v);const links=[...release.querySelectorAll('.source-sidebar [data-version="'+v+'"] .source-link')];const chosen=links.find(a=>a.hash==='#'+release.sourceSelections?.get(v))||links.find(a=>a.textContent.endsWith('OutputStatement.java'))||links[0];if(chosen)select(chosen.hash.slice(1))}version?.addEventListener('change',changeVersion);changeVersion();
for(const pane of document.querySelectorAll('.source-preview')){if(pane.closest('.release-workbench'))continue;if(pane.firstElementChild)select(pane.firstElementChild.id)}
function revealHash(){const id=decodeURIComponent(location.hash.slice(1));const section=document.getElementById(id);if(!section)return;const ownerId=section.closest('.code-diff')?.id||id;const link=document.querySelector('.release-workbench .source-link[href="#'+CSS.escape(ownerId)+'"]');if(link){version.value=link.closest('[data-version]').dataset.version;changeVersion()}for(let p=section.parentElement;p;p=p.parentElement)if(p.tagName==='DETAILS')p.open=true;select(id)}
document.querySelectorAll('.source-link').forEach(a=>a.addEventListener('click',e=>{e.preventDefault();select(a.hash.slice(1));history.pushState(null,'',a.hash)}));window.addEventListener('hashchange',revealHash);window.addEventListener('popstate',revealHash);document.addEventListener('click',e=>{const a=e.target.closest('a[href^="#"]');if(a&&!a.classList.contains('source-link'))setTimeout(revealHash,0)});revealHash();
for(const tree of document.querySelectorAll('.source-sidebar,.explorer-sidebar'))tree.addEventListener('keydown',e=>{const nodes=[...tree.querySelectorAll('a,summary')].filter(a=>a.getClientRects().length);const at=nodes.indexOf(document.activeElement);if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();nodes[Math.max(0,Math.min(nodes.length-1,at+(e.key==='ArrowDown'?1:-1)))]?.focus()}if(e.target.tagName==='SUMMARY'&&(e.key==='ArrowLeft'||e.key==='ArrowRight')){e.preventDefault();e.target.parentElement.open=e.key==='ArrowRight'}});
})();

// The fixed rail yields to rich blocks without changing document geometry.
(()=>{
const rail=document.querySelector('.article-contents');let queued=false;
function update(){queued=false;const box=rail.getBoundingClientRect();const beforeStory=document.querySelector('.article-layout').getBoundingClientRect().top>24;const yielding=innerWidth>=1250&&(beforeStory||[...document.querySelectorAll('.comparison,.source-workbench,.explorer-workbench,.review-toolbar,.evidence figure,.appendix')].some(e=>{const r=e.getBoundingClientRect();return r.width>750&&r.height>0&&r.top<box.bottom+24&&r.bottom>box.top-24}));rail.classList.toggle('toc-yield',yielding);rail.inert=yielding;}
function schedule(){if(!queued){queued=true;requestAnimationFrame(update)}}
addEventListener('scroll',schedule,{passive:true});addEventListener('resize',schedule);document.addEventListener('toggle',schedule,true);new ResizeObserver(schedule).observe(document.querySelector('main'));update();
})();
