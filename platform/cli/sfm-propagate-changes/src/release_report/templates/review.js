(()=>{
const template=document.getElementById('comparison-review-payload');if(!template)return;
let payload;try{payload=JSON.parse(template.content.textContent)}catch{return}
const files=payload.files.filter(f=>document.getElementById(f.id)),key='sfm-review@1:'+payload.comparison_identity;
const rowBadges=new Map();const accepted=new Set(['👍','👎']);let reactions={},history=[],applied={reaction:'all',extension:'all',changed:false},pending=false;
let storageOK=true;try{const saved=JSON.parse(localStorage.getItem(key)||'{}');for(const f of files){if(saved[f.identity]&&accepted.has(saved[f.identity]))reactions[f.identity]=saved[f.identity]}}catch{storageOK=false}
const host=document.createElement('section');host.className='review-toolbar';host.setAttribute('aria-label','Review controls');
host.innerHTML='<h2>Review files</h2><div class="tree-controls"><label>Reaction <select id="review-reaction-filter"><option value="all">All reactions</option><option value="unreacted">Unreacted</option><option value="👍">👍 Thumbs up</option><option value="👎">👎 Thumbs down</option></select></label><label>Extension <select id="review-extension-filter"><option value="all">All extensions</option></select></label><label><input id="review-changed-filter" type="checkbox"> Changed logical files only</label><button type="button" id="review-apply">Apply filters</button><button type="button" id="review-undo" disabled>Undo reaction</button><button type="button" id="review-copy">Copy review JSON</button><button type="button" id="review-sync" aria-pressed="false">HTML synchronized scroll: Off</button></div><p id="review-pending" role="status">Filters applied.</p><p id="review-storage" role="status"></p><details id="review-copy-fallback" hidden><summary>Copy JSON manually</summary><textarea readonly aria-label="Review JSON"></textarea><a download="review.json">Download review JSON</a></details>';
const workbench=document.querySelector('.source-workbench'),first=workbench||document.querySelector('.code-diff');
if(!first)return;first.before(host);
const summary=document.createElement('section');summary.className='review-summary';summary.innerHTML='<h2>Review totals</h2><p id="review-counts" role="status"></p><p id="review-extensions"></p><p id="review-approval" role="status"></p>';
(workbench||first.parentElement).after(summary);
const q=id=>host.querySelector('#'+id),extension=q('review-extension-filter');
[...new Set(files.map(f=>f.extension))].sort().forEach(ext=>{const o=document.createElement('option');o.value=ext;o.textContent=ext;extension.append(o)});
// Align complete segments. Fixed prefix/suffix anchors avoid shifting repeated directory names.
function alignedSegments(old,next){
const left=old.map(()=>false),right=next.map(()=>false);let start=0,endOld=old.length,endNext=next.length;
while(start<endOld&&start<endNext&&old[start]===next[start]){left[start]=right[start]=true;start++}
while(endOld>start&&endNext>start&&old[endOld-1]===next[endNext-1]){left[--endOld]=right[--endNext]=true}
// Hirschberg LCS avoids allocating a quadratic alignment matrix for deep paths.
function lengths(a,b){let row=new Uint32Array(b.length+1);for(const segment of a){const nextRow=new Uint32Array(b.length+1);for(let j=0;j<b.length;j++)nextRow[j+1]=segment===b[j]?row[j]+1:Math.max(row[j+1],nextRow[j]);row=nextRow}return row}
function align(a,b,offsetA,offsetB){if(!a.length||!b.length)return;if(a.length===1){const j=b.indexOf(a[0]);if(j>=0){left[offsetA]=true;right[offsetB+j]=true}return}
const middle=Math.floor(a.length/2),front=lengths(a.slice(0,middle),b),back=lengths(a.slice(middle).reverse(),b.slice().reverse());let split=0,best=-1;
for(let j=0;j<=b.length;j++){const score=front[j]+back[b.length-j];if(score>best){best=score;split=j}}
align(a.slice(0,middle),b.slice(0,split),offsetA,offsetB);align(a.slice(middle),b.slice(split),offsetA+middle,offsetB+split)}
align(old.slice(start,endOld),next.slice(start,endNext),start,start);return [left,right]}
function paths(f){const box=document.createElement('div');box.className='review-paths';
const old=f.old_path?f.old_path.split('/'):[],next=f.new_path?f.new_path.split('/'):[],matches=alignedSegments(old,next);
for(const [side,label,parts] of [[0,'Old',old],[1,'New',next]]){const line=document.createElement('p');line.append(label+': ');if(!parts.length){const absent=document.createElement('span');absent.className='path-changed';absent.textContent='(absent)';line.append(absent)}
parts.forEach((part,i)=>{if(i)line.append('/');const span=document.createElement('span');span.className=matches[side][i]?'path-common':'path-changed';span.textContent=part;line.append(span)});box.append(line)}return box}
function referenceLink(reference){const routes={issue:'issues',pull_request:'pull',discussion:'discussions'},route=routes[reference.kind];if(!route||!Number.isInteger(reference.number)||reference.number<=0)return null;const link=document.createElement('a');link.href='https://github.com/'+encodeURIComponent(reference.owner)+'/'+encodeURIComponent(reference.repository)+'/'+route+'/'+reference.number;link.textContent=reference.kind.replace('_',' ')+' '+reference.owner+'/'+reference.repository+'#'+reference.number;link.className='reference-link';return link}
function referenceEvidence(f){const box=document.createElement('div');box.className='review-references';
const title=document.createElement('p');title.textContent='Related references (declared relevance, not attribution of every changed byte):';box.append(title);
for(const [label,path,association] of [['Old',f.old_path,f.old_related_references],['New',f.new_path,f.new_related_references]]){const line=document.createElement('p');line.append(label+': ');
if(!path)line.append('File absent');else if(!association)line.append('Unavailable: no recorded reference provenance');else if(!association.references.length)line.append('Unassociated: no active reference declarations');else for(const reference of association.references){const link=referenceLink(reference);line.append(link||'Invalid reference',' ')}box.append(line);
if(association){const details=document.createElement('details'),summary=document.createElement('summary');summary.textContent=label+' declaration evidence';details.append(summary);const text=document.createElement('pre');text.textContent=JSON.stringify(association,null,2);details.append(text);box.append(details)}}return box}
for(const f of files){const view=document.getElementById(f.id);view.dataset.reviewIdentity=f.identity;
const bar=document.createElement('div');bar.className='review-reactions';bar.setAttribute('role','group');bar.setAttribute('aria-label','Reaction for '+(f.new_path||f.old_path));
for(const [emoji,label] of [['👍','Thumbs up'],['👎','Thumbs down'],['','Clear reaction']]){const button=document.createElement('button');button.type='button';button.dataset.reaction=emoji;button.textContent=label;button.setAttribute('aria-label',label+' '+(f.new_path||f.old_path));if(emoji)button.setAttribute('aria-pressed','false');button.addEventListener('click',()=>edit(f,emoji));bar.append(button)}
const badge=document.createElement('span');badge.className='review-badge';bar.append(badge);view.querySelector('h2,h3').after(paths(f),referenceEvidence(f),bar);if(f.whitespace_basis){const note=document.createElement('p');note.className='whitespace-basis';note.textContent='Whitespace-only certificate: '+f.whitespace_basis;bar.after(note)}
const link=[...document.querySelectorAll('.source-link')].find(link=>link.hash.slice(1)===f.id);
if(link){const badge=document.createElement('span');badge.className='review-row-badge';badge.dataset.reviewIdentity=f.identity;rowBadges.set(f.identity,badge);link.after(badge);const references=[...(f.old_related_references?.references||[]),...(f.new_related_references?.references||[])];if(f.status!=='exact'){const referenceBadge=document.createElement('span');referenceBadge.className='review-reference-badge';const unique=[...new Set(references.map(r=>r.kind+' '+r.owner+'/'+r.repository+'#'+r.number))].sort();referenceBadge.textContent=unique.length?' Related: '+unique.join(', '):[...(f.old_path?[f.old_related_references]:[]),...(f.new_path?[f.new_related_references]:[])].every(Boolean)?' References: unassociated':(f.old_related_references||f.new_related_references)?' References: unassociated / unavailable sides':' References: unavailable';link.after(referenceBadge)}}}

// Java whitespace certificates retain raw evidence in a separate, collapsed group.
const whitespaceGroups=[];
for(const sidebar of document.querySelectorAll('.source-sidebar')){
 const scopes=sidebar.querySelectorAll('[data-version]');
 for(const scope of scopes.length?scopes:[sidebar]){
  const candidates=files.filter(f=>f.whitespace_basis&&f.old_path===f.new_path&&scope.querySelector('.source-link[href="#'+CSS.escape(f.id)+'"]'));
  if(!candidates.length)continue;
  const group=document.createElement('details');group.className='whitespace-review';
  const heading=document.createElement('summary');heading.textContent='Whitespace-only ('+candidates.length+')';group.append(heading);
  const explanation=document.createElement('p');explanation.textContent='Java tokens, comments, literals and syntax structure match. Only whitespace between tokens differs. Raw source and hashes remain available.';group.append(explanation);
  const controls=document.createElement('div');controls.className='review-reactions';
  for(const [emoji,label] of [['👍','Approve group'],['👎','Reject group'],['','Clear group']]){const button=document.createElement('button');button.type='button';button.textContent=label;button.addEventListener('click',()=>bulk(candidates.filter(f=>!scope.querySelector('.source-link[href="#'+CSS.escape(f.id)+'"]').closest('li').hidden),emoji));controls.append(button)}group.append(controls);
  const list=document.createElement('ul');group.append(list);
  for(const f of candidates){const link=scope.querySelector('.source-link[href="#'+CSS.escape(f.id)+'"]');link.textContent=f.new_path;list.append(link.closest('li'))}
  scope.append(group);whitespaceGroups.push(group);
  // Remove empty directory shells left after moving certified whitespace rows.
  for(const directory of [...scope.querySelectorAll('li:has(>details)')].reverse())if(!directory.querySelector('.source-link'))directory.remove();
 }
}
function bulk(candidates,emoji){const changes=[];for(const f of candidates){const prior=reactions[f.identity]||'';if(prior===emoji)continue;changes.push({identity:f.identity,prior});if(emoji)reactions[f.identity]=emoji;else delete reactions[f.identity]}if(!changes.length)return;history.push(changes);save();counts();buttons();if(applied.reaction!=='all')markPending('Group reactions changed. Apply filters to refresh rows; selection is preserved.')}
function save(){try{localStorage.setItem(key,JSON.stringify(reactions));storageOK=true}catch{storageOK=false}q('review-storage').textContent=storageOK?'Reactions saved locally for this immutable comparison.':'Local storage unavailable; copy or download review JSON to retain reactions.'}
function edit(f,emoji){const prior=reactions[f.identity]||'';if(prior===emoji)return;history.push([{identity:f.identity,prior}]);if(emoji)reactions[f.identity]=emoji;else delete reactions[f.identity];save();counts();buttons();if(applied.reaction!=='all')markPending('Reactions changed. Apply filters to refresh rows; selection is preserved.')}
function buttons(){for(const f of files){const view=document.getElementById(f.id),value=reactions[f.identity]||'';view.querySelectorAll('[data-reaction]').forEach(b=>{if(b.dataset.reaction)b.setAttribute('aria-pressed',String(b.dataset.reaction===value))});view.querySelector('.review-badge').textContent=value||'Unreacted';const rowBadge=rowBadges.get(f.identity);if(rowBadge)rowBadge.textContent=' '+(value||'Unreacted')}q('review-undo').disabled=!history.length}
function counts(){const whitespace=files.filter(f=>f.whitespace_basis&&f.old_path===f.new_path),changed=files.filter(f=>f.status!=='exact'&&!whitespace.includes(f)),groups={'👍':0,'👎':0,unreacted:0},extensions={};for(const f of files)groups[reactions[f.identity]||'unreacted']++;for(const f of changed)extensions[f.extension]=(extensions[f.extension]||0)+1
summary.querySelector('#review-counts').textContent=changed.length+' substantive/path changes · '+whitespace.length+' whitespace-only files · '+files.length+' total files · 👍 '+groups['👍']+' · 👎 '+groups['👎']+' · Unreacted '+groups.unreacted;
summary.querySelector('#review-extensions').textContent='Changed by extension: '+Object.entries(extensions).sort().map(([ext,n])=>ext+' '+n).join(' · ');
const all=files.length>0&&files.every(f=>reactions[f.identity]==='👍');summary.querySelector('#review-approval').textContent=all?'Fully approved: every file has thumbs up.':'Not fully approved: every file requires thumbs up; any reaction alone is not approval.'}
function markPending(text='Filter changes pending. Apply filters to refresh rows.'){pending=true;q('review-pending').textContent=text}
for(const id of ['review-reaction-filter','review-extension-filter','review-changed-filter'])q(id).addEventListener('change',()=>markPending());
q('review-apply').addEventListener('click',()=>{applied={reaction:q('review-reaction-filter').value,extension:extension.value,changed:q('review-changed-filter').checked};let visible=0;for(const f of files){const match=(applied.reaction==='all'||(reactions[f.identity]||'unreacted')===applied.reaction)&&(applied.extension==='all'||f.extension===applied.extension)&&(!applied.changed||f.status!=='exact');const link=[...document.querySelectorAll('.source-link')].find(l=>l.hash.slice(1)===f.id);if(link)link.closest('li').hidden=!match;else document.getElementById(f.id).hidden=!match;if(match)visible++}pending=false;q('review-pending').textContent='Filters applied: '+visible+' files match. Current preview remains selected.'});
q('review-undo').addEventListener('click',()=>{const changes=history.pop();if(!changes)return;for(const prior of changes){if(prior.prior)reactions[prior.identity]=prior.prior;else delete reactions[prior.identity]}save();counts();buttons();if(applied.reaction!=='all')markPending('Undo changed reactions. Apply filters to refresh rows; selection is preserved.')});
function exported(){const groups={'👍':[],'👎':[],unreacted:[]};for(const f of files)groups[reactions[f.identity]||'unreacted'].push({scope:f.scope,old_path:f.old_path,new_path:f.new_path,file_identity:f.identity,status:f.status,whitespace_basis:f.whitespace_basis||null,old_related_references:f.old_related_references||null,new_related_references:f.new_related_references||null,before_sha256:f.before_sha256,after_sha256:f.after_sha256});return JSON.stringify({schema:'sfm:comparison-review@1',comparison_identity:payload.comparison_identity,provenance:payload.provenance,fully_approved:files.length>0&&files.every(f=>reactions[f.identity]==='👍'),groups},null,2)}
let download;
q('review-copy').addEventListener('click',async()=>{const json=exported();try{if(!navigator.clipboard)throw Error();await navigator.clipboard.writeText(json);q('review-pending').textContent='Review JSON copied.'}catch{const fallback=q('review-copy-fallback');fallback.hidden=false;fallback.open=true;const textarea=fallback.querySelector('textarea');textarea.value=json;textarea.focus();textarea.select();if(download)URL.revokeObjectURL(download);download=URL.createObjectURL(new Blob([json],{type:'application/json'}));fallback.querySelector('a').href=download;q('review-pending').textContent='Clipboard unavailable. Copy the selected JSON manually or download it.'}});
let synchronized=false,busy=false;
const panes=[...document.querySelectorAll('.static-diff pre,.text-preview pre')];
panes.forEach(pane=>pane.addEventListener('scroll',()=>{if(!synchronized||busy)return;const group=pane.closest('.static-diff,.text-preview');if(!group)return;busy=true;for(const other of group.querySelectorAll('pre'))if(other!==pane){other.scrollTop=pane.scrollTop;other.scrollLeft=pane.scrollLeft}busy=false}));
q('review-sync').addEventListener('click',()=>{synchronized=!synchronized;q('review-sync').setAttribute('aria-pressed',String(synchronized));q('review-sync').textContent='HTML synchronized scroll: '+(synchronized?'On':'Off');document.dispatchEvent(new CustomEvent('sfm-scroll-sync',{detail:{enabled:synchronized}}))});
save();counts();buttons();
})();
