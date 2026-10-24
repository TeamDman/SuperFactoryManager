"""Read-only validation of evidence; writes analytical inputs only in this directory."""
import hashlib, json, pathlib, zipfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
REPO = pathlib.Path('/workspace/SuperFactoryManager')
CACHE = pathlib.Path('/workspace/.sfm-env/cache/minecraft-toolchain/maven')
PROPOSAL = pathlib.Path('/workspace/scratch/release-inspection/metadata-admission-20261024/proposed-bundle')
sha = lambda raw: hashlib.sha256(raw).hexdigest()
lock_path = REPO / 'platform/minecraft/projections/sfm-dev/mc-1.19.2/sfm-toolchain.lock.json'
lock = json.loads(lock_path.read_text())
locked = {a['coordinate']: a for a in lock['artifacts'] if a.get('coordinate')}
roots = json.loads((ROOT / 'test-roots.json').read_text())
declared = {c['derived_checks']['resolved_coordinate'] for d in lock['dependencies']
            for c in d.get('components', [])
            if set(c.get('declaration', {}).get('scopes', [])) & {'test-compile', 'test-runtime'}}
assert set(roots) == declared
artifacts = json.loads((ROOT / 'artifacts.json').read_text())
sources = {}
for a in artifacts:
    assert a['coordinate'] in locked
    # The parent native verifier establishes the locked BLAKE3 receipt. Here
    # independently bind that receipt to the exact JAR and extracted ZIP entry.
    assert locked[a['coordinate']]['hash'] == a['lockedHash']
    raw = pathlib.Path(a['cachePath']).read_bytes()
    assert sha(raw) == a['sha256'] and len(raw) == a['bytes']
    group, artifact, version = a['coordinate'].split(':')
    with zipfile.ZipFile(a['cachePath']) as jar:
        member = f'META-INF/maven/{group}/{artifact}/pom.xml'
        assert (member in jar.namelist()) == a['hasEmbeddedPom']
        if a['hasEmbeddedPom']:
            p = pathlib.Path(a['embeddedPomPath']); pom = p.read_bytes()
            assert pom == jar.read(member)
            assert sha(pom) == a['embeddedPomSha256'] and len(pom) == a['embeddedPomBytes']
            sources[a['coordinate'] + '@pom'] = dict(kind='verified-embedded', path=str(p), sha256=sha(pom), bytes=len(pom))
catalog_raw = (CACHE / 'maven-metadata-pins.json').read_bytes()
catalog = json.loads(catalog_raw)
assert len(catalog['artifacts']) == 1
for a in catalog['artifacts']:
    p = CACHE / a['path']; raw = p.read_bytes()
    assert 'sha256:' + sha(raw) == a['sha256'] and len(raw) == a['bytes']
    sources.setdefault(a['coordinate'], dict(kind='approved-live-catalog', path=str(p), sha256=sha(raw), bytes=len(raw)))
omitted = []
for a in json.loads((PROPOSAL / 'receipts.json').read_text()):
    p = PROPOSAL / a['path']; raw = p.read_bytes()
    assert 'sha256:' + sha(raw) == a['sha256'] and len(raw) == a['bytes']
    if a['coordinate'] in sources:
        omitted.append(dict(coordinate=a['coordinate'], preferredSource=sources[a['coordinate']]['kind'],
                            byteIdentical=pathlib.Path(sources[a['coordinate']]['path']).read_bytes() == raw))
    else:
        assert not (CACHE / a['path']).exists()
        sources[a['coordinate']] = dict(kind='staged-external-analysis-only', path=str(p), sha256=sha(raw), bytes=len(raw))
src = REPO / 'platform/cli/sfm-propagate-changes/src/jar_build'
engine = (src / 'engine_model.rs').read_text()
struct_start = engine.index('#[derive(Clone, Debug, Eq, PartialEq)]\nstruct MavenCoordinate {')
struct_end = engine.index('\n}\n', struct_start) + 3
impl_start = engine.index('impl MavenCoordinate {')
# engine_model.rs ends with the complete MavenCoordinate impl and Display impl.
coordinate = engine[struct_start:struct_end] + '\n' + engine[impl_start:]
(HERE / 'coordinate.rs').write_text(coordinate)
source_hashes = {str(src / f): sha((src / f).read_bytes()) for f in
                 ['engine_model.rs', 'maven_pom_model.rs', 'maven_pom_dependency.rs', 'maven_pom_resolution.rs', 'xml_node.rs']}
output = dict(roots=roots, lockedCoordinates=sorted(locked), expectedCoordinates=sorted(a['coordinate'] for a in artifacts),
              sources=sources, omittedStagedMetadata=omitted, approvedCatalogSha256=sha(catalog_raw),
              lockSha256=sha(lock_path.read_bytes()), parserSourceSha256=source_hashes,
              coordinateExtractSha256=sha(coordinate.encode()))
(HERE / 'inputs.json').write_text(json.dumps(output, indent=2) + '\n')
print(f'validated {len(roots)} declared test roots, {len(artifacts)} locked JARs, {len(sources)} metadata sources; omitted {len(omitted)} staged POMs')
