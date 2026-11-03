"""Buat metadata rilis dari APK hasil build. Tidak mengunggah atau menerbitkan rilis."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import urlparse

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--url',default='',help='Tautan APK GitHub Releases setelah diunggah pemilik')
args=parser.parse_args()
if args.url:
    url=urlparse(args.url)
    assert url.scheme=='https' and url.hostname=='github.com' and not url.username and not url.query and not url.fragment
    assert re.fullmatch(r'/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/[^?#]+\.apk',url.path)
gradle=(root/'app/build.gradle.kts').read_text(encoding='utf-8')
version=re.search(r'versionName = "([^"]+)"',gradle).group(1)
code=int(re.search(r'versionCode = (\d+)',gradle).group(1))
apk=root/'app/build/outputs/apk/release/app-release.apk'
metadata=json.loads((apk.parent/'output-metadata.json').read_text())
assert metadata['elements'][0]['versionCode']==code and metadata['elements'][0]['versionName']==version
with apk.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
result={'schema':1,'version_code':code,'version_name':version,'download_url':args.url,'sha256':digest,'bytes':apk.stat().st_size,'min_sdk':26,'channel':'stable','published':False,
        'notes':'Pengecek pembaruan aplikasi, impor playlist, kurasi antrean, terjemahan banyak bahasa, pilihan remix dan tema lirik.',
        'setup_note':'Isi URL lampiran APK GitHub Releases yang nyata sebelum mendaftarkan rilis melalui panel Admin.'}
destination=root/f'dist/iSwara-{version}-release.json';destination.parent.mkdir(exist_ok=True)
destination.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(destination);print('Metadata draft siap; tidak ada rilis yang diterbitkan.')
