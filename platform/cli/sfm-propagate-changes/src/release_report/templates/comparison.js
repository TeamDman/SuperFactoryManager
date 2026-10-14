(()=>{
const links=[...document.querySelectorAll('.source-link')],views=[...document.querySelectorAll('.source-preview>.code-diff')],status=document.getElementById('comparison-status');
function select(id){const view=document.getElementById(id)?.closest('.code-diff');id=view?.id||id;if(!views.includes(view))return;views.forEach(v=>v.hidden=v!==view);links.forEach(link=>link.setAttribute('aria-current',String(link.hash.slice(1)===id)));status.textContent=view.querySelector('h2').textContent;}
links.forEach(link=>link.addEventListener('click',()=>select(link.hash.slice(1))));
window.addEventListener('hashchange',()=>select(location.hash.slice(1)));
if(links.length)select(location.hash.slice(1)||links[0].hash.slice(1));else status.textContent='Both selected inputs have no files.';
})();
