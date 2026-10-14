import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import assert from 'node:assert/strict';

// Uses the installed browser and a fresh task-local profile; no screenshots or dependencies.
const [first, second] = process.argv.slice(2).map(p => path.resolve(p));
if (!first || !second) throw Error('Provide two comparison HTML paths');
const profile = await fs.mkdtemp(path.join(process.cwd(), '.comparison-edge-'));
const server = http.createServer(async (req, res) => {
  try {
    const chosen = req.url.startsWith('/second') ? second : first;
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(await fs.readFile(chosen));
  } catch { res.statusCode = 404; res.end(); }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const origin = 'http://127.0.0.1:' + server.address().port;
const browser = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--remote-debugging-port=0', '--user-data-dir=' + profile,
  '--no-first-run', '--no-default-browser-check', '--window-size=1440,1000', 'about:blank',
], { windowsHide: true, stdio: 'ignore' });
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(fn, timeout = 20000) {
  const start = Date.now();
  for (;;) { try { const value = await fn(); if (value) return value; } catch {}
    if (Date.now() - start > timeout) throw Error('Browser wait timed out'); await delay(100); }
}
let socket, endpoint, id = 0;
const pending = new Map(), errors = [];
async function command(method, params = {}) {
  const request = ++id;
  return await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => { pending.delete(request); reject(Error(method + ' timed out')); }, 25000);
    pending.set(request, { resolve: result => { clearTimeout(timeout); resolve(result); }, reject });
    socket.send(JSON.stringify({ id: request, method, params }));
  });
}
async function evaluate(expression) {
  const result = await command('Runtime.evaluate', { expression: '(()=>eval(' + JSON.stringify(expression) + '))()', returnByValue: true, awaitPromise: true, userGesture: true });
  if (result.exceptionDetails) throw Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}
async function navigate(url) {
  await command('Page.navigate', { url });
  await until(() => evaluate('document.readyState === "complete" && !!document.getElementById("review-counts")'));
}
const click = selector => evaluate('document.querySelector(' + JSON.stringify(selector) + ').click()');
const value = selector => evaluate('document.querySelector(' + JSON.stringify(selector) + ').textContent');
const results = [];
function passed(name) { results.push(name); console.log('PASS ' + name); }
try {
  const port = await until(async () => Number((await fs.readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]));
  endpoint = 'http://127.0.0.1:' + port;
  const pages = await (await fetch(endpoint + '/json/list')).json();
  socket = new WebSocket(pages.find(p => p.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  socket.onmessage = event => { const data = JSON.parse(event.data); if (data.id) {
    const entry = pending.get(data.id); if (!entry) return; pending.delete(data.id);
    if (data.error) entry.reject(Error(JSON.stringify(data.error))); else entry.resolve(data.result);
  } else if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails); };
  await command('Runtime.enable'); await command('Page.enable');
  await command('Emulation.setFocusEmulationEnabled', { enabled: true });
  await navigate(origin + '/first');
  const payload = await evaluate('JSON.parse(document.getElementById("comparison-review-payload").content.textContent)');
  if (process.env.SFM_COMPARISON_SMOKE) {
    const move = payload.files.find(f => f.status === 'moved' && (f.new_path || '').includes('tough_cable_horizontal.json'));
    assert.ok(move, 'Actual pinned/current source comparison retains the unique recipe move');
    await click('a[href="#' + move.id + '"]');
    const paths = await value('#' + move.id + ' .review-paths');
    assert.ok(paths.includes('Old:') && paths.includes('New:') && paths.includes('data/minecraft') && paths.includes('sfm'));
    assert.match(await value('#review-approval'), /^Not fully approved/);
    const totals = await value('#review-counts');
    assert.ok(totals.includes(payload.files.length + ' total files'));
    await click('#' + move.id + ' [data-reaction="👍"]');
    assert.equal(await value('#' + move.id + ' .review-badge'), '👍');
    await click('#review-undo');
    assert.equal(await evaluate('document.querySelector("#' + move.id + ' .review-badge").textContent'), 'Unreacted');
    passed('actual pinned/current projection viewer: move provenance, counts, reactions and undo');
    assert.equal(errors.length, 0, JSON.stringify(errors));
    await fs.writeFile('actual-comparison-browser-results.json', JSON.stringify({ browser: 'Installed Edge headless', passed: results, runtimeErrors: errors, comparisonIdentity: payload.comparison_identity, logicalFiles: payload.files.length, totals }, null, 2));
  } else if (process.env.SFM_RELEASE_SMOKE) {
    const move = payload.files.find(f => f.scope === '26.1.2' && f.status === 'moved' && (f.new_path || '').includes('tough_cable_horizontal.json'));
    assert.ok(move, '26.1.2 frozen tough_cable_horizontal.json path move must remain present');
    await evaluate('const select=document.getElementById("source-version");select.value="26.1.2";select.dispatchEvent(new Event("change"));');
    await click('a[href="#' + move.id + '"]');
    const paths = await value('#' + move.id + ' .review-paths');
    assert.ok(paths.includes('Old:') && paths.includes('New:') && paths.includes('tough_cable_horizontal.json'));
    assert.ok(await evaluate('document.querySelectorAll("#' + move.id + ' .path-common").length > 0'));
    assert.match(await value('#review-approval'), /^Not fully approved/);
    const totals = await value('#review-counts');
    assert.ok(totals.includes(payload.files.length + ' total files'));
    passed('actual numbered release: 26.1.2 path-only move, identity, counts and default unreacted state');
    const changelog = payload.files.find(f => f.scope === '1.19.2' && (f.new_path || '').endsWith('changelog.sfml'));
    assert.ok(changelog);
    await evaluate('const select=document.getElementById("source-version");select.value="1.19.2";select.dispatchEvent(new Event("change"));');
    await click('a[href="#' + changelog.id + '"]');
    const code = await evaluate('document.querySelector("#' + changelog.id + ' .after code");const c=document.querySelector("#' + changelog.id + ' .after code");c.dataset.sfmlOriginal?document.getElementById(c.dataset.sfmlOriginal).content.textContent:c.textContent');
    assert.ok(code.indexOf('https://github.com/sponsors/TeamDman') < code.indexOf('---- 4.35.0 ----'));
    assert.ok(code.indexOf('https://discord.gg/5mbUY3mu6m') < code.indexOf('---- 4.35.0 ----'));
    passed('actual numbered release: changelog links precede new version history');
    assert.equal(errors.length, 0, JSON.stringify(errors));
    await fs.writeFile('release-browser-results.json', JSON.stringify({ browser: 'Installed Edge headless', passed: results, runtimeErrors: errors, comparisonIdentity: payload.comparison_identity, logicalFiles: payload.files.length, totals }, null, 2));
  } else {
  const total = payload.files.length;
  assert.ok(total >= 3);
  const moved = payload.files.find(f => f.status === 'moved');
  assert.ok(moved);
  await click('a[href="#' + moved.id + '"]');
  assert.ok((await value('#' + moved.id + ' .review-paths')).includes(moved.old_path.split('/').at(-1)));
  assert.ok(await evaluate('document.querySelector("#' + moved.id + ' .path-common").textContent.length > 0'));
  assert.ok((await value('#review-counts')).includes(total + ' total files'));
  passed('path-only move, segment views and logical counts');
  const selected = '#' + moved.id;
  await click(selected + ' [data-reaction="👍"]');
  assert.match(await value('#review-approval'), /Not fully approved/);
  await command('Page.reload');
  await until(() => evaluate('!!document.getElementById("review-counts")'));
  assert.equal(await evaluate('document.querySelector(' + JSON.stringify(selected + ' [data-reaction="👍"]') + ').getAttribute("aria-pressed")'), 'true');
  passed('localStorage persistence across reload without premature approval');
  await click('a[href="#' + moved.id + '"]');
  await evaluate('const f=document.getElementById("review-reaction-filter");f.value="unreacted";f.dispatchEvent(new Event("change"));');
  assert.match(await value('#review-pending'), /pending/);
  assert.equal(await evaluate('document.querySelector(' + JSON.stringify('a[href="#' + moved.id + '"]') + ').closest("li").hidden'), false);
  await click('#review-apply');
  assert.equal(await evaluate('document.querySelector(' + JSON.stringify('a[href="#' + moved.id + '"]') + ').closest("li").hidden'), true);
  assert.equal(await evaluate('document.getElementById(' + JSON.stringify(moved.id) + ').hidden'), false);
  await click(selected + ' [data-reaction="👎"]');
  await click('#review-undo');
  assert.equal(await evaluate('document.querySelector(' + JSON.stringify(selected + ' [data-reaction="👍"]') + ').getAttribute("aria-pressed")'), 'true');
  assert.match(await value('#review-pending'), /Apply filters/);
  passed('staged filters preserve preview; reaction edits and undo do not refilter');
  await navigate(origin + '/second');
  const secondPayload = await evaluate('JSON.parse(document.getElementById("comparison-review-payload").content.textContent)');
  assert.notEqual(payload.comparison_identity, secondPayload.comparison_identity);
  assert.equal(await evaluate('document.querySelectorAll(".review-reactions [aria-pressed=true]").length'), 0);
  passed('different comparison identities cannot inherit reactions');
  await navigate(origin + '/first');
  await evaluate('document.querySelectorAll(".review-reactions button").forEach(b=>{if(b.dataset.reaction === "👍")b.click()})');
  assert.match(await value('#review-approval'), /^Fully approved/);
  await click('#review-undo');
  assert.match(await value('#review-approval'), /^Not fully approved/);
  passed('all thumbs up approval and undo revoke approval');
  const longFile = payload.files.find(f => f.new_path?.endsWith('Long.java'));
  assert.ok(longFile);
  await click('a[href="#' + longFile.id + '"]');
  await evaluate('const pane=document.querySelector("#' + longFile.id + ' .text-preview pre");pane.scrollTop=300;pane.dispatchEvent(new Event("scroll"));');
  assert.equal(await evaluate('document.querySelectorAll("#' + longFile.id + ' .text-preview pre")[1].scrollTop'), 0);
  await click('#review-sync');
  await evaluate('const pane=document.querySelector("#' + longFile.id + ' .text-preview pre");pane.scrollTop=400;pane.dispatchEvent(new Event("scroll"));');
  const positions = await evaluate('[...document.querySelectorAll("#' + longFile.id + ' .text-preview pre")].map(p=>p.scrollTop)');
  assert.ok(positions[0] > 0 && positions[0] === positions[1]);
  passed('static fallback scroll synchronization changes actual pane positions');
  // Optional real Monaco loading: report separately if the public CDN is inaccessible.
  await click('#review-sync'); await click('#' + longFile.id + ' .enhance');
  await until(() => evaluate('!/Loading/.test(document.querySelector("#' + longFile.id + ' .editor-status").textContent)'), 22000);
  const monacoStatus = await value('#' + longFile.id + ' .editor-status');
  if (monacoStatus.startsWith('Interactive source panes ready')) {
    const independent = await evaluate('monaco.editor.getEditors().filter(e=>e.getModel()).map(e=>({before:e.getScrollTop(),id:e.getId()}))');
    assert.equal(independent.length, 2);
    await evaluate('monaco.editor.getEditors().filter(e=>e.getModel())[0].setScrollTop(300)');
    await delay(150);
    const offPositions = await evaluate('monaco.editor.getEditors().filter(e=>e.getModel()).map(e=>e.getScrollTop())');
    assert.ok(offPositions[0] > 0 && offPositions[1] === 0);
    await click('#review-sync');
    assert.match(await value('#' + longFile.id + ' .editor-status'), /synchronization is on/);
    await evaluate('monaco.editor.getEditors().filter(e=>e.getModel())[0].setScrollTop(300)');
    await delay(150);
    const onPositions = await evaluate('monaco.editor.getEditors().filter(e=>e.getModel()).map(e=>e.getScrollTop())');
    assert.ok(onPositions[0] > 0 && Math.abs(onPositions[0] - onPositions[1]) < 30);
    passed('real Monaco independent panes and synchronized diff scroll');
  } else { assert.match(monacoStatus, /unavailable/); console.log('LIMIT Monaco CDN unavailable: ' + monacoStatus); }
  await navigate(pathToFileURL(first).href);
  await command('Page.addScriptToEvaluateOnNewDocument', { source: 'Object.defineProperty(navigator,"clipboard",{value:{writeText:()=>Promise.reject(Error("fixture denied"))},configurable:true});' });
  await command('Page.reload'); await until(() => evaluate('!!document.getElementById("review-counts")'));
  await click('#review-copy');
  await until(() => evaluate('!document.getElementById("review-copy-fallback").hidden'));
  const exported = JSON.parse(await evaluate('document.querySelector("#review-copy-fallback textarea").value'));
  assert.equal(exported.schema, 'sfm:comparison-review@1');
  assert.equal(exported.comparison_identity, payload.comparison_identity);
  assert.equal(Object.values(exported.groups).reduce((n, group) => n + group.length, 0), total);
  assert.ok(Object.hasOwn(exported.groups, 'unreacted'));
  passed('file URL clipboard denial provides schema-bound grouped JSON fallback');
  const firstButton = '#' + payload.files[0].id + ' [data-reaction="👍"]';
  await click('a[href="#' + payload.files[0].id + '"]');
  await evaluate('document.querySelector(' + JSON.stringify(firstButton) + ').focus()');
  await command('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', text: '\r', unmodifiedText: '\r', windowsVirtualKeyCode: 13 });
  await command('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
  assert.equal(await evaluate('document.querySelector(' + JSON.stringify(firstButton) + ').getAttribute("aria-pressed")'), 'true');
  await command('Emulation.setTouchEmulationEnabled', { enabled: true });
  const downButton = '#' + payload.files[0].id + ' [data-reaction="👎"]';
  const point = await evaluate('const b=document.querySelector(' + JSON.stringify(downButton) + ');b.scrollIntoView({block:"center"});const r=b.getBoundingClientRect();({x:r.x+r.width/2,y:r.y+r.height/2})');
  await command('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
  await command('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await delay(400);
  assert.equal(await evaluate('document.querySelector(' + JSON.stringify(downButton) + ').getAttribute("aria-pressed")'), 'true');
  passed('native keyboard and touch activation of visible reaction buttons');
  assert.equal(errors.length, 0, JSON.stringify(errors));
  await fs.writeFile('comparison-browser-results.json', JSON.stringify({ browser: 'Installed Edge headless', passed: results, runtimeErrors: errors, monacoStatus }, null, 2));
  }
} finally {
  if (socket?.readyState === WebSocket.OPEN) { try { await command('Browser.close'); } catch {} socket.close(); }
  browser.kill(); server.close();
}
