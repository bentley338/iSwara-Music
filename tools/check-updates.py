"""Pemeriksaan baca-saja layanan info pembaruan; tidak menerbitkan rilis."""
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
root=Path(__file__).resolve().parents[1]
config=dict(line.strip().split('=',1) for line in (root/'secrets.properties').read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
base=config['SUPABASE_URL'].rstrip('/');key=config['SUPABASE_ANON_KEY']
def probe(path,body=None):
    request=Request(base+path,data=None if body is None else json.dumps(body).encode(),headers={'apikey':key,'Authorization':'Bearer '+key,'Content-Type':'application/json'})
    try:
        with urlopen(request,timeout=25) as r:return r.status,json.loads(r.read())
    except HTTPError as e:return e.code,{}
status,data=probe('/rest/v1/rpc/check_app_update',{'p_current':10,'p_sdk':36,'p_channel':'stable'})
table_status,_=probe('/rest/v1/app_releases?select=version_code&limit=1')
result={'checked_at':datetime.now(timezone.utc).isoformat(),'project_url_sha256':hashlib.sha256(base.encode()).hexdigest(),'update_rpc_available':status==200 and isinstance(data,dict),'anonymous_draft_table_blocked':table_status in (401,403),'release_published_by_test':False,'github_asset_uploaded':False}
(root/'docs/verification/update-preflight-0.9.0.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2));assert result['update_rpc_available'] and result['anonymous_draft_table_blocked']
