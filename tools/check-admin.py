"""Periksa endpoint admin publik dan penolakan anonim, tanpa membaca pengguna."""
import json
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime, timezone

root = Path(__file__).resolve().parents[1]
properties = {}
for line in (root / 'secrets.properties').read_text().splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1)
        properties[key.strip()] = value.strip()
headers = {'apikey': properties['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + properties['SUPABASE_ANON_KEY'], 'Content-Type': 'application/json'}

def status(path, post=False):
    request = urllib.request.Request(properties['SUPABASE_URL'].rstrip('/') + path, data=b'{}' if post else None, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=12) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code

checks = {
    'published_announcements': status('/rest/v1/app_announcements?select=id&published=eq.true&limit=1') == 200,
    'published_collections': status('/rest/v1/featured_collections?select=id&published=eq.true&limit=1') == 200,
    'anonymous_membership_blocked': status('/rest/v1/admin_members?select=user_id&limit=1') in (401,403),
    'anonymous_audit_blocked': status('/rest/v1/admin_audit?select=id&limit=1') in (401,403),
    'anonymous_summary_blocked': status('/rest/v1/rpc/admin_summary',True) in (401,403),
    'user_function_present_and_blocks_anonymous': status('/functions/v1/admin-users',True) in (401,403),
}
report = {'checked_at': datetime.now(timezone.utc).isoformat(), 'checks': checks, 'authenticated_admin_actions_tested': False}
(root / 'docs/verification/admin-live-preflight.json').write_text(json.dumps(report,indent=2) + '\n')
print(json.dumps(report,indent=2))
assert all(checks.values())
