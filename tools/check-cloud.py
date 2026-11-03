"""Periksa konfigurasi publik tanpa membuat akun atau mengubah data pengguna."""
import base64
import hashlib
import json
from pathlib import Path
from datetime import datetime, timezone
from urllib.request import Request, urlopen
from urllib.error import HTTPError

root = Path(__file__).resolve().parents[1]
config = dict(line.strip().split('=', 1) for line in (root / 'secrets.properties').read_text().splitlines()
              if '=' in line and not line.lstrip().startswith('#'))
base = config['SUPABASE_URL'].rstrip('/')
key = config['SUPABASE_ANON_KEY']
payload = key.split('.')[1]
reference = json.loads(base64.urlsafe_b64decode(payload + '=' * (-len(payload) % 4))).get('ref')
assert base == f'https://{reference}.supabase.co', 'URL proyek tidak cocok dengan anon key'

def probe(path, body=None):
    headers = {'apikey': key, 'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'}
    request = Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
    try:
        with urlopen(request, timeout=25) as response:
            raw = response.read().decode()
            try: body = json.loads(raw or 'null')
            except json.JSONDecodeError: body = {'message': raw}
            return response.status, body
    except HTTPError as error:
        raw = error.read().decode()
        try: body = json.loads(raw or 'null')
        except json.JSONDecodeError: body = {'message': raw}
        return error.code, body

settings_status, settings = probe('/auth/v1/settings')
read_status, _ = probe('/rest/v1/documents?select=id&limit=1')
rpc_status, rpc = probe('/rest/v1/rpc/sync_document', {
    'p_id': 'anonymous-preflight', 'p_kind': 'playlist', 'p_payload': {}, 'p_base_revision': 0,
    'p_deleted': False, 'p_operation': '00000000-0000-0000-0000-000000000001'})
delete_status, delete = probe('/functions/v1/delete-account', {})
report = {
    'checked_at': datetime.now(timezone.utc).isoformat(), 'project_url_sha256': hashlib.sha256(base.encode()).hexdigest(),
    'auth_reachable': settings_status == 200,
    'email_auth_enabled': settings.get('external', {}).get('email'),
    'email_confirmation_required': not settings.get('mailer_autoconfirm', True),
    'anonymous_library_read_blocked': read_status in (401, 403),
    'sync_rpc_exists_and_blocks_anonymous': rpc_status in (401, 403) and rpc.get('code') == '42501',
    'delete_function_present': delete_status != 404,
    'anonymous_account_deletion_blocked': delete_status in (401,403),
    'authenticated_two_device_sync_tested': False,
    'smtp_delivery_tested': False,
}
destination = root / 'docs/verification/cloud-preflight.json'
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
assert report['auth_reachable'] and report['anonymous_library_read_blocked'] and report['sync_rpc_exists_and_blocks_anonymous']
