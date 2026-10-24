"""Read-only assertions over events produced by actual Forge UI interaction.

This does not drive the picker, synthesize events, or modify a document.
Known failed preservation checks are reported separately from passing checks.
"""
import hashlib
import json
import sys
from pathlib import Path

events = [json.loads(line) for line in Path(sys.argv[1]).read_text().splitlines()]
assert not [e for e in events if e['event'] in ('fixture_error', 'observer_error')]
state = [(i, e) for i, e in enumerate(events) if e['event'] == 'state']
picker_name = 'SFMTextEditorSelectionScreen'
stable = ('editor', 'editorIdentity', 'text', 'textSha256', 'savedSha256',
          'widgetIdentity', 'cursor', 'selectionCursor', 'contextIdentity',
          'cursors', 'factoryCalls')
cycles = []
for n, (i, e) in enumerate(state):
    if not e['screen'].endswith(picker_name):
        continue
    if n and state[n - 1][1]['screenIdentity'] == e['screenIdentity']:
        continue
    before = state[n - 1][1]
    j = n
    while j < len(state) and state[j][1]['screenIdentity'] == e['screenIdentity']:
        j += 1
    assert j < len(state), 'Picker left open at end of recording'
    after = state[j][1]
    for _, frame in state[n:j + 1]:
        for key in stable:
            assert frame.get(key) == before.get(key), (i + 1, key)
    changed = after['preferred'] != before['preferred']
    if changed:
        assert after['preferred'] == state[j - 1][1]['pickerSelected']
    cycles.append({'firstLine': i + 1, 'lastLine': state[j][0] + 1,
                   'editor': before['editor'].split('.')[-1],
                   'preferenceChanged': changed, 'preferredAfter': after['preferred'],
                   'documentAndObservedStatePreserved': True})

opens, factories, saves, initialization = [], [], [], []
for i, e in enumerate(events):
    if e['event'] == 'external_factory':
        before = events[i - 1]
        assert before['event'] == 'open_request'
        assert e['id'] == before['preferred']
        assert e['initialText'] == before['savedText']
        assert e['initialSha256'] == before['savedSha256']
        factories.append({'line': i + 1, 'id': e['id'], 'exactInput': True})
    elif e['event'] == 'open_request':
        frame = next(x for x in events[i + 1:] if x['event'] == 'state')
        assert frame['screen'] == frame['editor']
        exact = frame['text'] == e['savedText']
        delta = None if exact else 'one_terminal_LF_lost' if e['savedText'] == frame['text'] + '\n' else 'other'
        assert delta != 'other'
        opens.append({'line': i + 1, 'preferred': e['preferred'], 'exact': exact})
        if not exact:
            initialization.append({'line': i + 1, 'editor': frame['editor'].split('.')[-1],
                                   'failure': delta, 'beforeSha256': e['savedSha256'],
                                   'afterSha256': frame['textSha256']})
    elif e['event'] == 'save':
        before = next(x for x in reversed(events[:i]) if x['event'] == 'state')
        assert e['text'] == before['text']
        assert e['sha256'] == hashlib.sha256(e['text'].encode()).hexdigest()
        saves.append({'line': i + 1, 'editor': before['editor'].split('.')[-1],
                      'exactCurrentDocument': True})

# Unedited V3 open -> first loaded state -> save must be reported as a
# preservation failure, despite the save faithfully serializing current text.
unedited_v3_loss = []
for i, e in enumerate(events[:-2]):
    if e['event'] != 'open_request' or e['preferred'] != 'sfm:text_editor_v3':
        continue
    frame, saved = events[i + 1:i + 3]
    if frame['event'] == 'state' and saved['event'] == 'save' and e['savedText'] == saved['text'] + '\n':
        unedited_v3_loss.append({'openLine': i + 1, 'saveLine': i + 3,
                                 'beforeSha256': e['savedSha256'], 'afterSha256': saved['sha256']})
assert unedited_v3_loss, 'Expected observed V3 regression evidence missing'
assert {x['id'] for x in factories} == {'sfm_picker_fixture:external', 'sfm_picker_fixture:external_alt'}
assert len(cycles) >= 10 and len(factories) >= 3
print(json.dumps({'events': len(events), 'pickerCycles': cycles,
                  'opens': opens, 'extensionFactories': factories, 'saves': saves,
                  'failedInitializationPreservation': initialization,
                  'failedUneditedV3SavePreservation': unedited_v3_loss,
                  'limitations': ['V3 cursor/undo not observed', 'V2 save not qualified',
                                  'file-backed context, not server inventory',
                                  'extension registrations delegate production V1 renderer']}, indent=2))
