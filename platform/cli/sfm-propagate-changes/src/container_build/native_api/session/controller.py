"""Publish one fixed puppet operation; no shell, download, or account access."""
from pathlib import Path
import json, os, re, sys

root = Path('/work/platform/minecraft/projections/sfm-dev/mc-1.19.2/runGameTestPreview/managed-session')
operation = json.loads(sys.argv[1])
nonce = sys.argv[2]
assert re.fullmatch('[0-9a-f]{16}', nonce)
assert set(operation) <= {'op', 'game_test', 'angle'}
assert operation['op'] in {'reset', 'run_game_test', 'capture', 'stop'}
receipt = Path('/work/output') / ('controller-' + nonce + '.json')
lock = root / 'controller.lock'
pending = root / ('controller-' + nonce + '.pending.json')
owned = False
result = {}
try:
    ready = json.loads((root / 'ready.json').read_text())
    assert ready['schema'] == 'sfm.managed-session/1' and ready['max_requests'] == 128
    fd = os.open(lock, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    owned = True
    with os.fdopen(fd, 'w') as stream:
        stream.write(nonce)
    for sequence in range(1, 129):
        prefix = f'{sequence:06d}'
        request = root / (prefix + '.request.json')
        response = root / (prefix + '.response.json')
        if response.exists():
            previous = json.loads(response.read_text())
            assert previous['schema'] == 'sfm.managed-session-response/1' and previous['sequence'] == sequence
            continue
        assert not request.exists(), 'another operation is pending; status before retry'
        operation['sequence'] = sequence
        with pending.open('x') as stream:
            json.dump(operation, stream, allow_nan=False)
        # The exclusive controller lock serializes all typed clients. Publication
        # uses a hard link, whose destination must not exist, then drops our temp.
        os.link(pending, request)
        pending.unlink()
        result['sequence'] = sequence
        break
    else:
        raise RuntimeError('session request budget exhausted')
except Exception as error:
    result['error'] = str(error)
finally:
    if owned:
        if pending.exists():
            pending.unlink()
        assert lock.read_text() == nonce
        lock.unlink()
with receipt.open('x') as stream:
    json.dump(result, stream, allow_nan=False)
sys.exit(0 if 'sequence' in result else 1)
