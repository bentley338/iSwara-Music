"""Pemeriksaan baca-saja endpoint Radio; tidak membuat room atau pesan."""
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError

root = Path(__file__).resolve().parents[1]
config = dict(line.strip().split('=', 1) for line in (root / 'secrets.properties').read_text().splitlines()
              if '=' in line and not line.lstrip().startswith('#'))
base = config['SUPABASE_URL'].rstrip('/')
key = config['SUPABASE_ANON_KEY']

def probe(name, body):
    request = Request(base + '/rest/v1/rpc/' + name, data=json.dumps(body).encode(),
                      headers={'apikey': key, 'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
    try:
        with urlopen(request, timeout=25) as response:
            return response.status, json.loads(response.read())
    except HTTPError as error:
        return error.code, json.loads(error.read())

status, directory = probe('discover_radio', {'p_query': '', 'p_mood': 'all'})
private_status, _ = probe('radio_state', {'p_room': '00000000-0000-0000-0000-000000000000'})
report = {'checked_at': datetime.now(timezone.utc).isoformat(),
          'project_url_sha256': hashlib.sha256(base.encode()).hexdigest(),
          'public_directory_available': status == 200 and isinstance(directory.get('items'), list),
          'anonymous_lounge_blocked': private_status in (401, 403),
          'production_room_created': False, 'authenticated_two_device_tested': False}
destination = root / 'docs/verification/radio-preflight-0.8.0.json'
destination.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
assert report['public_directory_available'] and report['anonymous_lounge_blocked']
