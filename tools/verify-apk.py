"""Periksa APK hasil build tanpa membuka keystore atau menjalankan aplikasi di HP."""
from pathlib import Path
from zipfile import ZipFile
import hashlib
import argparse
import json
import os
import re
import struct
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--test-results', type=Path, default=root / 'app/build/test-results/testDebugUnitTest')
args = parser.parse_args()
gradle = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
version = re.search(r'versionName = "([^"]+)"', gradle).group(1)
code = int(re.search(r'versionCode = (\d+)', gradle).group(1))
apk = root / 'app/build/outputs/apk/release/app-release.apk'
metadata = json.loads((apk.parent / 'output-metadata.json').read_text())
assert metadata['elements'][0]['versionName'] == version
assert metadata['elements'][0]['versionCode'] == code
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'))
build_tools = sdk / 'build-tools/36.0.0'
report = root / 'docs/verification'
report.mkdir(exist_ok=True)
env = dict(os.environ)
env['PATH'] = env['PATH'].replace('"', '')

def run(name, *args):
    executable = build_tools / (name + ('.bat' if name == 'apksigner' and os.name == 'nt' else '.exe' if os.name == 'nt' else ''))
    result = subprocess.run([str(executable), *map(str, args)], capture_output=True, text=True, encoding='utf-8', errors='replace', env=env)
    assert result.returncode == 0, f'{name} gagal, kode {result.returncode}'
    return result.stdout

signature = run('apksigner', 'verify', '--verbose', '--print-certs', apk)
(report / f'signature-{version}.txt').write_text(signature, encoding='utf-8')
certificate = re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', signature).group(1)
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature
previous = sorted((root / 'dist').glob('iSwara-*.apk'), key=lambda path: path.stat().st_mtime, reverse=True)
previous = next((path for path in previous if '-debug' not in path.name and path.name != f'iSwara-{version}.apk'), None)
if previous:
    old_signature = run('apksigner', 'verify', '--print-certs', previous)
    assert certificate == re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', old_signature).group(1), 'Sertifikat berubah: APK tidak bisa menjadi update'
alignment = run('zipalign', '-c', '-P', '16', '-v', '4', apk)
(report / f'alignment-{version}.txt').write_text(alignment, encoding='utf-8')
badging = run('aapt', 'dump', 'badging', apk)
(report / f'apk-{version}.txt').write_text(badging, encoding='utf-8')
assert f"versionCode='{code}'" in badging and f"versionName='{version}'" in badging
assert re.search(r"(?:minSdkVersion|sdkVersion):'26'", badging) and "targetSdkVersion:'36'" in badging
if (root / 'app/src/main/java/id/iswara/music/data/AppUpdates.kt').exists():
    assert "android.permission.REQUEST_INSTALL_PACKAGES" in badging
    mapping = (root / 'app/build/outputs/mapping/release/mapping.txt').read_text(encoding='utf-8')
    for worker in ('UpdateCheckWorker', 'UpdateDownloadWorker'):
        name = 'id.iswara.music.data.' + worker
        assert name + ' -> ' + name + ':' in mapping, 'Nama worker berubah dan dapat memutus pekerjaan setelah upgrade'

native = {}
with ZipFile(apk) as archive:
    assert archive.testzip() is None
    if 'com.atilika.kuromoji:kuromoji-ipadic' in gradle:
        for resource in ('com/atilika/kuromoji/ipadic/tokenInfoDictionary.bin',
                         'com/atilika/kuromoji/ipadic/doubleArrayTrie.bin',
                         'com/ibm/icu/impl/data/icudata/translit/root.res'):
            assert archive.getinfo(resource).file_size > 0, 'Kamus/cara baca hilang dari APK: ' + resource
        for notice in ('KUROMOJI_LICENSE.txt', 'KUROMOJI_IPADIC_NOTICE.txt', 'ICU_LICENSE.txt'):
            assert archive.read('assets/legal/' + notice) == (root / 'app/src/main/assets/legal' / notice).read_bytes()
    manifest = json.loads(archive.read('assets/stem-models/manifest.json'))
    for name, model in manifest['models'].items():
        data = archive.read(f'assets/stem-models/{name}.onnx')
        assert len(data) == model['bytes'] and hashlib.sha256(data).hexdigest() == model['sha256']
    for name in archive.namelist():
        if not (name.startswith('lib/') and name.endswith('.so')):
            continue
        data = archive.read(name)
        assert data[:4] == b'\x7fELF'
        bits = 64 if data[4] == 2 else 32
        endian = '<' if data[5] == 1 else '>'
        offset = struct.unpack_from(endian + ('Q' if bits == 64 else 'I'), data, 32 if bits == 64 else 28)[0]
        size, count = struct.unpack_from(endian + 'HH', data, 54 if bits == 64 else 42)
        alignments = []
        for index in range(count):
            start = offset + index * size
            if struct.unpack_from(endian + 'I', data, start)[0] == 1:
                alignments.append(struct.unpack_from(endian + ('Q' if bits == 64 else 'I'), data, start + (48 if bits == 64 else 28))[0])
        assert alignments and all(alignment >= 16384 for alignment in alignments), f'ELF tidak mendukung halaman 16 KB: {name}'
        native[name] = {'bits': bits, 'load_alignment': alignments, 'supports_16kb_elf': True}
    abis = sorted({name.split('/')[1] for name in native})
    assert abis == ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']

def write(name, data):
    (report / name).write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')

with apk.open('rb') as stream:
    apk_digest = hashlib.file_digest(stream, 'sha256').hexdigest()
integrity = {'version': version, 'version_code': code, 'bytes': apk.stat().st_size, 'sha256': apk_digest,
             'certificate_sha256': certificate, 'matches_previous_certificate': previous is not None,
             'bundled_models_match_manifest': True, 'all_native_abis': abis, 'apk_runtime_executed_on_android': False}
write(f'apk-integrity-{version}.json', integrity)
write(f'native-elf-{version}.json', native)

tests = []
for path in sorted(args.test_results.glob('TEST-*.xml')):
    suite = ET.parse(path).getroot()
    assert int(suite.get('failures', 0)) == 0 and int(suite.get('errors', 0)) == 0 and int(suite.get('skipped', 0)) == 0, path.name
    tests += [{'class': case.get('classname'), 'name': case.get('name'), 'seconds': float(case.get('time', '0'))} for case in suite.findall('testcase')]
assert tests and len({(case['class'], case['name']) for case in tests}) == len(tests)
write(f'tests-{version}.json', {'version': version, 'tests': len(tests), 'failures': 0, 'errors': 0, 'skipped': 0, 'cases': tests})
lint = ET.parse(root / 'app/build/reports/lint-results-debug.xml').getroot()
severities = {level: sum(issue.get('severity') == level for issue in lint.findall('issue')) for level in ('Fatal', 'Error', 'Warning', 'Information')}
assert severities['Fatal'] + severities['Error'] == 0
write(f'lint-{version}.json', severities)
print(json.dumps({'version': version, 'bytes': integrity['bytes'], 'sha256': integrity['sha256'], 'certificate_matches_previous': previous is not None, 'native_libraries_16kb': len(native), 'tests_passed': len(tests)}))
