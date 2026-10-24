"""Bounded, scratch-only native-parser graph and leave-one-metadata-out checks."""
import collections, hashlib, json, pathlib, subprocess

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
data = json.loads((HERE / 'inputs.json').read_text())
graph = json.loads((HERE / 'candidate-graph.json').read_text())
assert not graph['parserErrors']
assert not graph['versionConflicts']
assert not graph['unlockedCoordinates']
assert not graph['outsideExpectedCoordinates']
assert not graph['expectedNotReached']
assert len(graph['roots']) == 13 and len(graph['reachedCoordinates']) == 24 and len(graph['edges']) == 29
external = {x['coordinate'] for x in graph['stagedExternalMetadataActuallyRead']}
receipts = json.loads((ROOT / 'minimal-receipts.json').read_text())
assert external == {x['coordinate'] for x in receipts} and len(external) == 18
assert sum(x['bytes'] for x in receipts) == 185009
for receipt in receipts:
    source = data['sources'][receipt['coordinate']]
    assert receipt['sha256'] == 'sha256:' + source['sha256'] and receipt['bytes'] == source['bytes']
catalog_path = pathlib.Path('/workspace/.sfm-env/cache/minecraft-toolchain/maven/maven-metadata-pins.json')
live_raw = catalog_path.read_bytes()
assert hashlib.sha256(live_raw).hexdigest() == data['approvedCatalogSha256']
live = json.loads(live_raw)
proposed = json.loads((ROOT / 'proposed-catalog-NOT-APPLIED.json').read_text())
assert len(live['artifacts']) == 1 and len(proposed['artifacts']) == 19
assert len({x['coordinate'] for x in proposed['artifacts']}) == 19
assert live['artifacts'][0] in proposed['artifacts']
for receipt in receipts:
    pin = next(x for x in proposed['artifacts'] if x['coordinate'] == receipt['coordinate'])
    assert all(pin[key] == receipt[key] for key in ['coordinate', 'path', 'url', 'sha256', 'bytes', 'retrievedUtc'])
    assert not (catalog_path.parent / receipt['path']).exists()
for path, expected in data['parserSourceSha256'].items():
    assert hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest() == expected
for source in data['sources'].values():
    assert hashlib.sha256(pathlib.Path(source['path']).read_bytes()).hexdigest() == source['sha256']

outdir = HERE / 'withheld-metadata'
outdir.mkdir(exist_ok=True)
checks = []
for index, coordinate in enumerate(sorted(external)):
    variant = dict(data)
    variant['sources'] = {key: value for key, value in data['sources'].items() if key != coordinate}
    input_path, output_path = outdir / f'{index:02d}.input.json', outdir / f'{index:02d}.result.json'
    input_path.write_text(json.dumps(variant, indent=2) + '\n')
    run = subprocess.run([str(HERE / 'native_graph'), str(input_path), str(output_path)], capture_output=True, text=True, check=True)
    result = json.loads(output_path.read_text())
    missing = {x['coordinate'] for x in result['metadataReads'] if x['kind'] == 'missing'}
    assert coordinate in missing and result['parserErrors'], coordinate
    checks.append(dict(withheld=coordinate, missingReadObserved=True, parserRejectedArtifacts=sorted(result['parserErrors']),
                       exit=run.returncode, stdout=run.stdout.strip()))
assert hashlib.sha256(catalog_path.read_bytes()).hexdigest() == data['approvedCatalogSha256']
summary = dict(scope='Analytical native-parser execution; not metadata admission or aggregate tests',
               roots=13, reachedLockedArtifacts=24, runtimeEdges=29, parserErrors=0,
               versionConflicts=0, unlockedCoordinates=0, outsideExpectedCoordinates=0,
               uniqueMetadataReadsByKind=dict(collections.Counter(x['kind'] for x in graph['uniqueMetadataReads'].values())),
               stagedExternalPomsActuallyRead=18, stagedExternalBytes=185009,
               byteIdenticalEmbeddedReplacements=data['omittedStagedMetadata'],
               leaveOneOut=checks, nativeSourceHashesUnchanged=True, metadataSourceHashesUnchanged=True,
               liveCatalogUnchanged=True, candidateCacheFilesAbsent=True,
               candidateGraphSha256=hashlib.sha256((HERE / 'candidate-graph.json').read_bytes()).hexdigest(),
               proposedCatalogSha256=hashlib.sha256((ROOT / 'proposed-catalog-NOT-APPLIED.json').read_bytes()).hexdigest())
(HERE / 'verification.json').write_text(json.dumps(summary, indent=2) + '\n')
print('PASS locked closure, 18 exact analytical metadata receipts, 18 leave-one-out rejection checks, unchanged live catalog/source inputs')
