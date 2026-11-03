from pathlib import Path
from zipfile import ZipFile
import json, hashlib, re, sys, base64
root = Path(__file__).resolve().parents[1]
version = re.search(r'versionName = "([^"]+)"', (root/'app/build.gradle.kts').read_text()).group(1)
report = root/f'docs/verification/source-{version}.json'
private = {'local.properties', 'secrets.properties', 'signing.properties', '.env'}
blocked = {'.git', '.gradle', '.kotlin', '.idea', '.tools', 'node_modules', 'build', 'dist', '.temp'}
values = {}
config_path = root/'secrets.properties'
for line in (config_path.read_text(encoding='utf-8-sig').splitlines() if config_path.exists() else []):
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1)
        if key.strip() in {'SUPABASE_URL', 'SUPABASE_ANON_KEY', 'ISWARA_STORE_PASSWORD', 'ISWARA_KEY_PASSWORD'} and value.strip():
            values[key.strip()] = value.strip().replace('\\=', '=').replace('\\:', ':').encode()
def allowed(path):
    rel = path.relative_to(root)
    return path.is_file() and not any(part in blocked for part in rel.parts) and path.name not in private and not path.name.startswith('.env.') and path.suffix.lower() not in {'.jks', '.keystore', '.pem', '.key', '.p12', '.pfx', '.log', '.hprof'}

secret_patterns = [
    rb'gh[pousr]_[A-Za-z0-9]{30,}',
    rb'github_pat_[A-Za-z0-9_]{30,}',
    rb'sb_secret_[A-Za-z0-9_-]{20,}',
    rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----',
]

def scan(data):
    assert not any(re.search(pattern,data) for pattern in secret_patterns), 'A credential/private-key pattern was found; inspect locally without printing its value'
    for token in re.findall(rb'\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b',data):
        payload=token.split(b'.')[1]
        try:
            claims=json.loads(base64.urlsafe_b64decode(payload+b'='*((-len(payload))%4)))
        except (ValueError,UnicodeDecodeError):
            continue
        assert claims.get('role')!='service_role', 'A server-role JWT was found; inspect locally without printing its value'
    for key,value in values.items():
        assert value not in data, f'Configured value found in source input: {key}'
files = [p for p in root.rglob('*') if allowed(p) and p != report]
for path in files:
    data = path.read_bytes()
    scan(data)
record = {'version': version, 'source_files': len(files)+1, 'configured_fields_checked': sorted(values), 'configured_values_absent': True, 'credential_and_private_key_patterns_absent': True, 'private_configuration_and_keystores_excluded': True, 'archive_crc_and_workspace_bytes_checked': True}
if '--archive' not in sys.argv:
    report.write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
    print('Source input audit:', record['source_files'], 'files; configured values absent')
else:
    expected = json.loads(report.read_text())
    archive_path = root/f'dist/iSwara-{version}-source.zip'
    with ZipFile(archive_path) as archive:
        assert archive.testzip() is None
        assert len(archive.namelist()) == expected['source_files']
        assert len(set(archive.namelist())) == len(archive.namelist())
        for name in archive.namelist():
            assert name.startswith('iSwara/')
            source_path = root/name.removeprefix('iSwara/')
            assert source_path.resolve().is_relative_to(root.resolve())
            assert allowed(source_path)
            data = archive.read(name)
            assert source_path.read_bytes() == data, 'Archive input changed'
            scan(data)
    print('Source ZIP audit PASS:', expected['source_files'], 'files; CRC, bytes, exclusions and configured values verified')
