"""Kemas APK, mapping, source tanpa rahasia, serta checksum setelah verifikasi selesai."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import re
import shutil

root = Path(__file__).resolve().parents[1]
version = re.search(r'versionName = "([^"]+)"', (root / 'app/build.gradle.kts').read_text()).group(1)
metadata = json.loads((root / 'app/build/outputs/apk/release/output-metadata.json').read_text())
assert metadata['elements'][0]['versionName'] == version, 'APK release belum sesuai versi source'
report = (root / 'docs/VERIFICATION.md').read_text(encoding='utf-8')
assert 'STATUS_' not in report and f'iSwara {version}' in report, 'Selesaikan verifikasi versi ini dahulu'
destination = root / 'dist'
destination.mkdir(exist_ok=True)
shutil.copy2(root / 'app/build/outputs/apk/release/app-release.apk', destination / f'iSwara-{version}.apk')
shutil.copy2(root / 'app/build/outputs/apk/debug/app-debug.apk', destination / f'iSwara-{version}-debug.apk')
mapping = root / 'app/build/outputs/mapping/release'
with ZipFile(destination / f'iSwara-{version}-mapping.zip', 'w', ZIP_DEFLATED) as archive:
    for path in sorted(mapping.rglob('*')):
        if path.is_file():
            archive.write(path, path.relative_to(mapping).as_posix())

blocked = {'.git', '.gradle', '.kotlin', '.idea', '.tools', 'node_modules', 'build', 'dist', '.temp'}
private = {'local.properties', 'secrets.properties', 'signing.properties', '.env'}
source = destination / f'iSwara-{version}-source.zip'
with ZipFile(source, 'w', ZIP_DEFLATED, compresslevel=6) as archive:
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        if not path.is_file() or any(part in blocked for part in relative.parts) or path.name in private or path.name.startswith('.env.') or path.suffix.lower() in {'.jks', '.keystore', '.pem', '.key', '.p12', '.pfx', '.log', '.hprof'}:
            continue
        archive.write(path, 'iSwara/' + relative.as_posix())
with ZipFile(source) as archive:
    assert archive.testzip() is None
    assert not any(Path(name).name in private or name.endswith(('.jks', '.keystore')) for name in archive.namelist())
    assert archive.read(f'iSwara/docs/PEMBARUAN_{version}.md') == (root / f'docs/PEMBARUAN_{version}.md').read_bytes()
    print('Source diperiksa:', len(archive.namelist()), 'berkas, tanpa konfigurasi privat')

checksums = []
for path in sorted(destination.iterdir()):
    if path.is_file() and path.name != 'SHA256SUMS.txt':
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        checksums.append(f'{digest}  {path.name}')
        if version in path.name:
            print(path.name, path.stat().st_size, digest)
(destination / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n', encoding='utf-8')
