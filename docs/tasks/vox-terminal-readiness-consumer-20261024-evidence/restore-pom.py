"""Restore explicitly named metadata for existing frozen test artifacts; never follow redirects."""
import datetime, hashlib, json, os, pathlib, subprocess, sys, xml.etree.ElementTree as E
root=pathlib.Path('/workspace/sfm-terminal-readiness-20261024')
cache=pathlib.Path('/workspace/.sfm-env/cache/minecraft-toolchain/maven')
catalog=cache/'maven-metadata-pins.json'
pins=json.loads(catalog.read_text())
receipts=[]
for coordinate in sys.argv[1:]:
 group,artifact,version=coordinate.split(':')
 relative=f"{group.replace('.','/')}/{artifact}/{version}/{artifact}-{version}.pom"
 url='https://repo.maven.apache.org/maven2/'+relative
 output=root/f'{artifact}-{version}.pom'
 for suffix in ['', '.sha1']:
  command=['curl','--fail','--silent','--show-error','--proto','=https','--max-time','45',
           '--cacert',os.environ['SSL_CERT_FILE'],'--capath',os.environ['SSL_CERT_DIR'],
           '--output',str(output)+suffix,'--write-out','%{http_code} %{ssl_verify_result}',url+suffix]
  result=subprocess.run(command,capture_output=True,text=True,check=True)
  assert result.stdout=='200 0',result.stdout
 data=output.read_bytes()
 assert hashlib.sha1(data).hexdigest()==pathlib.Path(str(output)+'.sha1').read_text().strip()
 entry={'coordinate':coordinate+'@pom','url':url,'sha256':'sha256:'+hashlib.sha256(data).hexdigest(),
        'bytes':len(data),'path':relative,'retrievedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()}
 target=cache/relative;target.parent.mkdir(parents=True,exist_ok=True)
 if target.exists(): assert target.read_bytes()==data
 else: target.write_bytes(data)
 old=next((p for p in pins['artifacts'] if p['coordinate']==entry['coordinate']),None)
 if old: assert old['sha256']==entry['sha256']
 else: pins['artifacts'].append(entry)
 xml=E.fromstring(data);ns={'m':'http://maven.apache.org/POM/4.0.0'};parent=xml.find('m:parent',ns)
 parent_id=None if parent is None else ':'.join(parent.findtext('m:'+k,namespaces=ns) for k in ['groupId','artifactId','version'])
 receipts.append({'artifact':entry,'http_status':200,'tls_verification_result':0,'official_sha1_match':True,'parent':parent_id})
 print(coordinate,'parent:',parent_id)
catalog.write_text(json.dumps(pins,indent=2)+'\n')
receipt=root/'metadata-additional-acquisition.json'
prior=json.loads(receipt.read_text()) if receipt.exists() else []
receipt.write_text(json.dumps(prior+receipts,indent=2)+'\n')
