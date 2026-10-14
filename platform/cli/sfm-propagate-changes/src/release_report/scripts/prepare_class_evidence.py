"""Offline, hash-bound javap evidence from the ten preserved release JAR pairs."""
from pathlib import Path
import json, hashlib, zipfile, subprocess, re, shutil, datetime, difflib

ROOT = OUT = SDK = JAVAP = None
INPUT = OUTPUT_MANIFEST = ASSET_PREFIX = None

def sha(data): return hashlib.sha256(data).hexdigest()

def normalized(text):
    # Constant-pool slot numbering is an encoding detail. Keep symbolic comments,
    # instruction offsets/jump targets, descriptors and every other printed line.
    lines=[]
    for line in text.replace('\r\n','\n').splitlines():
        if re.match(r'\s*\d+:\s+\S+',line):
            operand,sep,comment=line.partition('//')
            line=re.sub(r'#\d+','#CP',operand)+sep+comment
        lines.append(line)
    return '\n'.join(lines)+'\n'

def split_views(text):
    main, debug = [], []
    in_debug = False
    for line in normalized(text).splitlines():
        if re.match(r'\s+(LineNumberTable|LocalVariableTable|LocalVariableTypeTable):', line):
            in_debug = True
        elif in_debug and (not line.strip() or re.match(r'  \S', line) or line == '}'):
            in_debug = False
        (debug if in_debug else main).append(line)
    return '\n'.join(main).strip()+'\n', '\n'.join(debug).strip()+'\n'

def disassemble(jar, names, dest):
    if dest.exists():
        saved=json.loads(dest.read_text()); assert saved['jar_sha256']==sha(jar.read_bytes()); assert saved.get('tool_sha256')==sha(JAVAP.read_bytes()), 'Cached disassembly lacks matching pinned javap identity; choose a fresh output directory.'; return saved['classes']
    texts={}
    for start in range(0, len(names), 60):
        batch=names[start:start+60]
        args=[str(JAVAP), '-J-Xmx512m', '-J-XX:ActiveProcessorCount=2', '-classpath',str(jar), '-c','-p','-s','-l'] + [n[:-6].replace('/','.') for n in batch]
        run=subprocess.run(args,capture_output=True,timeout=90)
        assert run.returncode==0, run.stderr.decode(errors='replace')
        text=run.stdout.decode('utf-8'); chunks=re.split(r'(?=^Compiled from ")',text,flags=re.M)
        chunks=[c for c in chunks if c.strip()]
        assert len(chunks)==len(batch), (len(chunks),len(batch),text[:300])
        texts.update(zip(batch,chunks))
    dest.write_text(json.dumps(dict(jar_sha256=sha(jar.read_bytes()),tool_sha256=sha(JAVAP.read_bytes()),classes=texts)))
    return texts

def counts(a,b):
    add=delete=0
    for op, i,j,k,l in difflib.SequenceMatcher(None,a.splitlines(),b.splitlines(),autojunk=False).get_opcodes():
        if op in ('replace','delete'):delete+=j-i
        if op in ('replace','insert'):add+=l-k
    return add,delete

def zip_metadata(info):
    return (info.date_time,info.compress_type,info.compress_size,info.create_system,
            info.create_version,info.extract_version,info.flag_bits,info.volume,
            info.internal_attr,info.external_attr,info.extra,info.comment)

def classify(ba,bb,ma,mb,ga,gb,meta_a,meta_b,has_a=True,has_b=True):
    if not has_a:return 'added'
    if not has_b:return 'deleted'
    if ba==bb:return 'unchanged' if meta_a==meta_b else 'zip-metadata'
    if ma!=mb:return 'representation'
    if ga!=gb:return 'debug-view'
    return 'unrepresented-bytes'

def main():
    assert shutil.disk_usage(ROOT).free>12*1024**3
    OUT.mkdir(exist_ok=True)
    doc=json.loads(INPUT.read_text())
    tool=dict(name='javap',version=subprocess.check_output([str(JAVAP),'-version'],text=True).strip(),
              executable_sha256=sha(JAVAP.read_bytes()),sdk_release_sha256=sha((SDK/'release').read_bytes()),
              options='-c -p -s -l; -J-Xmx512m -J-XX:ActiveProcessorCount=2',
              normalization='CRLF to LF; constant-pool #numbers in instruction operands only replaced with #CP. Comments/string literals, bootstrap indices and offsets retained. Debug tables separated.',
              metric='Representation-line insertions/deletions, not semantic impact. Python SequenceMatcher(auto-junk disabled) preparation counts; rendered Histogram counts are authoritative.')
    summary=[]
    for pair in doc['jars']:
        a,b=Path(pair['baseline_path']),Path(pair['candidate_path'])
        with zipfile.ZipFile(a) as za,zipfile.ZipFile(b) as zb:
            namesa=sorted(n for n in za.namelist() if n.endswith('.class'));namesb=sorted(n for n in zb.namelist() if n.endswith('.class'))
            da=disassemble(a,namesa,OUT/(pair['id']+'-before.raw.json'));db=disassemble(b,namesb,OUT/(pair['id']+'-after.raw.json'))
            rows=[]
            for name in sorted(set(namesa)|set(namesb)):
                ba=za.read(name) if name in da else b'';bb=zb.read(name) if name in db else b''
                ma,ga=split_views(da.get(name,''));mb,gb=split_views(db.get(name,''))
                state=classify(ba,bb,ma,mb,ga,gb,zip_metadata(za.getinfo(name)) if name in da else None,zip_metadata(zb.getinfo(name)) if name in db else None,name in da,name in db)
                add,delete=counts(ma,mb)
                row=dict(path=name,status=state,added=add,removed=delete,before=ma if ma!=mb else '',after=mb if ma!=mb else '',
                         debug_before=ga if ga!=gb else '',debug_after=gb if ga!=gb else '',
                         before_sha256=sha(ba) if ba else '',after_sha256=sha(bb) if bb else '')
                rows.append(row)
            evidence=dict(schema='sfm:jar-class-evidence@1',projection=pair['id'],baseline_sha256='sha256:'+sha(a.read_bytes()),candidate_sha256='sha256:'+sha(b.read_bytes()),tool=json.dumps(tool),classes=rows)
            path=OUT/(pair['id']+'.json'); path.write_text(json.dumps(evidence))
            pair['class_evidence_path']=ASSET_PREFIX.rstrip('/')+'/'+path.name
            pair['class_evidence_sha256']='sha256:'+sha(path.read_bytes())
            tallies={s:sum(r['status']==s for r in rows) for s in sorted(set(r['status'] for r in rows))}
            reps=[{k:r[k] for k in ('path','status','added','removed')} for r in rows if 'OutputStatement.class' in r['path'] or 'SFMMod.class' in r['path']]
            line=dict(projection=pair['id'],counts=tallies,representative=reps,evidence_bytes=path.stat().st_size)
            summary.append(line);print(json.dumps(line),flush=True)
    doc['generated_utc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
    OUTPUT_MANIFEST.write_text(json.dumps(doc,indent=2))
    (OUT/'summary.json').write_text(json.dumps(dict(tool=tool,projections=summary),indent=2))

if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--output-manifest',type=Path,required=True)
    p.add_argument('--asset-prefix',required=True)
    p.add_argument('--javap',type=Path,required=True)
    opts=p.parse_args()
    INPUT=opts.manifest;ROOT=INPUT.parent;OUT=opts.output;OUTPUT_MANIFEST=opts.output_manifest
    ASSET_PREFIX=opts.asset_prefix;JAVAP=opts.javap;SDK=JAVAP.parent.parent
    assert INPUT.stat().st_size<=32*1024**2
    assert not OUTPUT_MANIFEST.exists(), 'Output manifest already exists; choose a fresh checkpoint.'
    main()
