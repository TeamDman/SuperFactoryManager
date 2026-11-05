"""Fail-closed cloud input checks. No acquisition, extraction or trust mutation."""
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import ssl
import stat
import subprocess
import sys
import tomllib

DENIED = frozenset((
    'BC4D809B15189D78DB3E1D8CF4F9726A795DA1643CA5F1358E1DDB0EDC0D7EB3',
    '86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B',
    '125609AA301DA0A249B97A8239CB6A34216F44DCAC9F3954B14292F2E8C8608F',
    '40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367',
))
MUTABLE = frozenset(('check-everything-native', 'projects', 'nfrt'))


def require(ok, message):
    if not ok:
        raise ValueError(message)


def real_path(value, directory=False):
    path = Path(value)
    require(path.is_absolute() and '..' not in path.parts, f'expected absolute normal path: {path}')
    for part in [path, *path.parents]:
        require(not part.is_symlink(), f'linked path is not allowed: {part}')
    info = path.stat()
    require(stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode), f'wrong file type: {path}')
    return path


def digest(path, algorithm='sha256'):
    real_path(path)
    with Path(path).open('rb') as source:
        return hashlib.file_digest(source, algorithm).hexdigest()


def load(path):
    return json.loads(real_path(path).read_text())


def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args], text=True).strip()


def helper(repo, filename):
    path = repo / 'platform/cli/sfm-propagate-changes/scripts' / filename
    spec = importlib.util.spec_from_file_location(filename.replace('-', '_'), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def receipt(pin, artifact):
    fields = {k: pin[k] for k in ('major', 'vendor', 'version', 'build', 'flavor')}
    fields.update({k: artifact[k] for k in ('platform', 'url', 'sha512')})
    fields['sha512'] = fields['sha512'].lower()
    return 'sfm-jbrsdk-cache-v1\n' + ''.join(f'{k}={v}\n' for k, v in fields.items())


def sdk_pin(repo, sdk):
    lock = real_path(repo / sdk['authority_lock'])
    require(lock.is_relative_to(repo), 'SDK lock authority escapes repository')
    git(repo, 'ls-files', '--error-unmatch', str(lock.relative_to(repo)))
    require(digest(lock) == sdk['authority_lock_sha256'], 'SDK authority lock changed')
    matches = [(pin, artifact) for pin in load(lock).get('jdk_pins', []) for artifact in pin['artifacts']
               if pin['major'] == sdk['major'] and artifact['platform'] == 'linux-x64'
               and artifact['sha512'].lower() == sdk['sha512'].lower()]
    require(len(matches) == 1, 'SDK must match exactly one authored Linux pin')
    return matches[0]


def audit_sdk(repo, sdk):
    """Delegate full file/link/mode verification to the sole repository tar helper."""
    pin, artifact = sdk_pin(repo, sdk)
    home, archive = real_path(sdk['home'], True), real_path(sdk['archive'])
    cache = real_path(sdk['sdk_cache_root'], True) / 'minecraft-toolchain/jbrsdk'
    require(home.parent == cache / 'installs' and archive.parent == cache / 'archives',
            'SDK paths differ from declared managed SDK root')
    require(artifact['url'].endswith('.tar.gz'), 'cloud gate requires pinned Linux tar SDK')
    require(digest(archive, 'sha512') == artifact['sha512'].lower(), 'SDK archive SHA-512 mismatch')
    require(real_path(home / '.sfm-jbrsdk-receipt').read_text() == receipt(pin, artifact),
            'SDK install receipt differs from authored pin')
    with archive.open('rb') as source:
        subprocess.run([sys.executable, '-B', str(repo / 'platform/cli/sfm-propagate-changes/src/jdk_tar.py'),
                        'verify', str(home), artifact['sha512'].lower()], stdin=source, check=True)
    return home


def audit_java_trust(repo, sdk):
    trust = helper(repo, 'prepare-java-trust.py')
    home, store = real_path(sdk['home'], True), real_path(sdk['trust_store'])
    require(not store.is_relative_to(home), 'filtered store must be outside original SDK')
    require(digest(store) == sdk['trust_sha256'], 'filtered Java trust store changed')
    source = real_path(home / 'lib/security/cacerts')
    record = load(sdk['trust_receipt'])
    require(record['schema'] == 'sfm:derived_java_trust_store@1'
            and record['source_sha256'] == digest(source)
            and record['filtered_sha256'] == digest(store)
            and set(record['denied_sha256']) == DENIED, 'Java trust receipt mismatch')
    original = trust.entries(source, home / 'bin/keytool')
    filtered = trust.entries(store, home / 'bin/keytool')
    require(not DENIED.intersection(filtered.values()), 'denied Java trust anchor present')
    retained = {a: h for a, h in original.items() if h not in DENIED}
    require(all(filtered.get(a) == h for a, h in retained.items()), 'unrelated Java anchor changed')
    expected = dict(retained)
    expected[trust.ENVIRONMENT_ALIAS] = sdk['proxy_ca_sha256']
    require(filtered == expected, 'unexpected Java anchors or missing proxy CA')


def snapshot_root(root, sdk_homes=()):
    """Hash immutable bytes, paths, modes and links; mutable outputs are explicit."""
    root = real_path(root, True)
    result = {}
    homes = {real_path(h, True) for h in sdk_homes}
    def walk(directory):
        for path in sorted(directory.iterdir()):
            relative = path.relative_to(root)
            if relative.parts[0] in MUTABLE:
                require(not path.is_symlink(), f'linked mutable output: {path}')
                continue
            info = path.lstat()
            mode = stat.S_IMODE(info.st_mode)
            if stat.S_ISLNK(info.st_mode):
                owners = [h for h in homes if path.is_relative_to(h)]
                require(len(owners) == 1, f'cache link lacks verified SDK authority: {path}')
                require(path.resolve(strict=True).is_relative_to(owners[0]), f'SDK link escapes home: {path}')
                result[str(relative)] = ['link', os.readlink(path), mode]
            elif stat.S_ISDIR(info.st_mode):
                result[str(relative)] = ['directory', mode]
                walk(path)
            elif stat.S_ISREG(info.st_mode):
                result[str(relative)] = ['file', info.st_size, digest(path), mode]
            else:
                raise ValueError(f'special cache entry: {path}')
    walk(root)
    return result


def snapshots(layout, allow_missing_native=False):
    roots = {layout['native_cache_root'], layout['application_cache_root']}
    roots.update(s['sdk_cache_root'] for s in layout['sdks'])
    paths = [Path(p) for p in sorted(roots)]
    for path in paths:
        if allow_missing_native and path == Path(layout['native_cache_root']) and not path.exists():
            require(not path.is_symlink() and path.is_absolute() and path.parent != path
                    and '..' not in path.parts, 'invalid absent native root')
            real_path(path.parent, True)
        else:
            real_path(path, True)
    require(not any(a.is_relative_to(b) for a in paths for b in paths if a != b),
            'managed cache roots overlap')
    homes = [real_path(s['home'], True) for s in layout['sdks']]
    return {str(p): snapshot_root(p, homes) if p.exists() else None for p in paths}


def same_snapshot(before, after):
    require(before == after, 'managed dependency/tool bytes, paths, links or modes changed')


def java_environment(layout):
    environment = os.environ.copy()
    for key in list(environment):
        if key.startswith('SFM_JAVA_TRUST_') or key in (
                'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVAC_OPTIONS',
                'JDK_ALTERNATE_VM', 'CLASSPATH'):
            environment.pop(key)
    environment['SFM_PROPAGATE_CHANGES_CACHE'] = layout['application_cache_root']
    for sdk in layout['sdks']:
        environment[f'SFM_JAVA_TRUST_STORE_{sdk["major"]}'] = sdk['trust_store']
        environment[f'SFM_JAVA_TRUST_SHA256_{sdk["major"]}'] = sdk['trust_sha256']
    return environment


def native_policy(repo):
    manifest = tomllib.loads((repo / 'platform/cli/sfm-propagate-changes/Cargo.toml').read_text())
    reqwest = manifest['dependencies']['reqwest']
    features = reqwest.get('features', [])
    require(reqwest.get('default-features') is False and 'rustls-tls-native-roots' in features
            and not any('webpki' in f or f == 'rustls-tls' for f in features), 'native-only TLS required')
    lock = tomllib.loads((repo / 'platform/cli/sfm-propagate-changes/Cargo.lock').read_text())
    require(not any(p['name'] == 'webpki-roots' for p in lock['package']), 'embedded WebPKI roots forbidden')


def native_binary(repo, binary, denied_certificates):
    native_policy(repo)
    data = real_path(binary).read_bytes()
    require(data.startswith(b'\x7fELF'), 'expected native Linux release executable')
    found = set()
    for path in denied_certificates:
        pem = real_path(path).read_text()
        identity = hashlib.sha256(ssl.PEM_cert_to_DER_cert(pem)).hexdigest().upper()
        require(identity in DENIED, 'unexpected denied certificate identity')
        found.add(identity)
        public = subprocess.check_output(['openssl', 'x509', '-in', str(path), '-pubkey', '-noout'])
        key = base64.b64decode(b''.join(public.splitlines()[1:-1]), validate=True)
        require(key[-64:] not in data, 'binary embeds denied public key')
    require(found == DENIED, 'all four denied certificate witnesses required')


def check_tls(layout):
    def certs(data):
        return [base64.b64decode(re.sub(rb'\s', b'', x), validate=True)
                for x in re.findall(rb'-----BEGIN CERTIFICATE-----\s*(.*?)-----END CERTIFICATE-----', data, re.S)]
    bundle = real_path(layout['tls_ca_file'])
    require(digest(bundle) == layout['tls_ca_sha256'], 'native TLS bundle changed')
    identities = {hashlib.sha256(x).hexdigest().upper() for x in certs(bundle.read_bytes())}
    require(identities and not identities.intersection(DENIED), 'native trust contains denied root')
    require(layout['proxy_ca_sha256'] in identities, 'native TLS proxy CA missing')
    original = real_path(layout['tls_source_file'])
    require(digest(original) == layout['tls_source_sha256'], 'original native trust bundle changed')
    original_ids = {hashlib.sha256(x).hexdigest().upper() for x in certs(original.read_bytes())}
    require(identities == (original_ids - DENIED) | {layout['proxy_ca_sha256']},
            'native filtering must preserve every unrelated anchor')
    directory = real_path(layout['tls_ca_dir'], True)
    for path in directory.iterdir():
        require(path.resolve(strict=True).is_relative_to(directory), 'CApath link escapes filtered directory')
        if path.is_file():
            selected = {hashlib.sha256(x).hexdigest().upper() for x in certs(path.read_bytes())}
            require(selected and selected <= identities, 'unexpected or denied CApath anchor')
    context = ssl.create_default_context(cafile=str(bundle), capath=str(directory))
    require(context.verify_mode == ssl.CERT_REQUIRED and context.check_hostname, 'TLS verification required')


def tls_environment(layout):
    check_tls(layout)
    env = java_environment(layout)
    for key in ('SSL_CERT_FILE', 'REQUESTS_CA_BUNDLE', 'CURL_CA_BUNDLE', 'GIT_SSL_CAINFO',
                'CARGO_HTTP_CAINFO', 'CARGO_PROXY_CAINFO', 'NPM_CONFIG_CAFILE', 'PIP_CERT', 'NODE_EXTRA_CA_CERTS'):
        env[key] = layout['tls_ca_file']
    for key in ('SSL_CERT_DIR', 'GIT_SSL_CAPATH'):
        env[key] = layout['tls_ca_dir']
    require(not any(env.get(k, '').lower() in ('0', 'false', 'no') for k in
                    ('GIT_SSL_VERIFY', 'CARGO_HTTP_SSL_VERIFY', 'NPM_CONFIG_STRICT_SSL')), 'TLS bypass environment')
    require(env.get('NODE_TLS_REJECT_UNAUTHORIZED') != '0', 'Node TLS bypass environment')
    env['NODE_OPTIONS'] = '--use-openssl-ca'
    return env


def digest_payload(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def validate_layout(layout):
    require(layout.get('schema') == 'sfm:cloud-preparation-layout@1', 'unknown preparation layout schema')
    require(isinstance(layout.get('stage_timeout_seconds', 1800), int)
            and 1 <= layout.get('stage_timeout_seconds', 1800) <= 7200, 'invalid bounded stage timeout')
    require(layout.get('sdks') and layout.get('target_java'), 'layout needs explicit SDK and target selections')
    require(all(s['proxy_ca_sha256'] == layout['proxy_ca_sha256'] for s in layout['sdks']),
            'Java and native proxy CA identities differ')


def require_drained(repo, layout):
    """Refuse activation/build while owned workers run; never kill from this entrypoint."""
    roots = [repo, Path(layout['native_cache_root']), Path(layout['application_cache_root'])]
    for entry in Path('/proc').iterdir():
        if not entry.name.isdigit() or int(entry.name) == os.getpid():
            continue
        try:
            comm = (entry / 'comm').read_text().strip()
            if comm not in ('cargo', 'rustc', 'java', 'javac', 'sfm-propagate-c') and not comm.startswith('sfm-propagate'):
                continue
            cwd = (entry / 'cwd').resolve(strict=True)
        except (OSError, RuntimeError):
            continue
        require(not any(cwd.is_relative_to(root) for root in roots),
                f'owned worker still active: pid={entry.name} name={comm}; drain and retain receipts first')
