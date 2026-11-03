"""Uji playback APK release di HP: pencarian, kontrol sistem, layar mati, dan skip.

Contoh: python tools/verify-device.py --serial SERIAL --version 0.3.1
Memutar audio sungguhan dan mengganti antrean aktif; tidak menghapus koleksi/akun.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
from datetime import datetime, timezone

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--version', required=True)
parser.add_argument('--query', default='Tulus Hati Hati di Jalan')
parser.add_argument('--screen-off-seconds', type=int, default=12, help='Durasi uji layar mati, 12 sampai 240 detik')
parser.add_argument('--cold', action='store_true', help='Tutup proses aplikasi sebelum menguji; data tetap disimpan')
parser.add_argument('--adb', default=str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
report = {'checked_at': datetime.now(timezone.utc).isoformat(), 'version': args.version, 'checks': {}, 'samples': {}}

def adb(*parts):
    result = subprocess.run([args.adb, '-s', args.serial, *parts], capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=25)
    if result.returncode:
        raise RuntimeError(re.sub(r'https?://\S+', '[URL]', result.stderr.strip() or result.stdout.strip()))
    return result.stdout

def state():
    text = adb('shell', 'dumpsys', 'media_session')
    if 'package=id.iswara.music' not in text:
        return None
    block = text.split('package=id.iswara.music', 1)[1].split('package=', 1)[0]
    match = re.search(r'state=PlaybackState \{(.*?)\}', block, re.S)
    if not match:
        return None
    playback = match.group(1)
    def number(pattern, default=-1):
        found = re.search(pattern, playback)
        return int(found.group(1)) if found else default
    return {'state': number(r'state=[A-Z_]+\((\d+)\)'), 'position_ms': number(r'(?<!buffered )position=(-?\d+)'),
            'buffered_ms': number(r'buffered position=(-?\d+)'), 'active_id': number(r'active item id=(-?\d+)'),
            'error': re.sub(r'https?://\S+', '[URL]', playback.split('error=', 1)[-1].strip()) if 'error=' in playback else None}

def wait(expected, different=None, timeout=60):
    deadline = time.monotonic() + timeout
    latest = None
    while time.monotonic() < deadline:
        latest = state()
        if latest and latest['state'] == expected and (different is None or latest['active_id'] != different):
            return latest
        if latest and latest['state'] == 7:
            raise RuntimeError('Pemutar melaporkan error: ' + str(latest['error']))
        time.sleep(1)
    raise RuntimeError('Waktu tunggu pemutar habis: ' + str(latest))

def key(action):
    # Target this app's session, rather than the globally selected music app.
    sessions = adb('shell', 'cmd', 'media_session', 'list-sessions')
    own = [line for line in sessions.splitlines() if 'package=id.iswara.music' in line]
    assert len(own) == 1, 'Session aplikasi tidak unik'
    tag = re.search(r'tag=(.*), package=', own[0]).group(1)
    assert sum(('tag=' + tag + ',') in line for line in sessions.splitlines()) == 1, 'Tag session tidak unik'
    result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'cmd', 'media_session', 'monitor', tag],
                            input=action + '\nq\n', capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=10)
    assert result.returncode == 0, 'Kontrol session gagal'

try:
    version = adb('shell', 'dumpsys', 'package', 'id.iswara.music')
    assert f'versionName={args.version}' in version, 'Versi terpasang tidak sesuai APK yang diuji'
    report['model'] = adb('shell', 'getprop', 'ro.product.model').strip()
    report['sdk'] = int(adb('shell', 'getprop', 'ro.build.version.sdk').strip())
    if args.cold:
        adb('shell', 'am', 'force-stop', 'id.iswara.music')
    adb('shell', 'input', 'keyevent', '224')
    query = args.query.replace('"', '').replace("'", '').replace('\n', ' ')[:200]
    # One quoted shell value, no user-provided shell fragments.
    import shlex
    command = 'am start -W -n id.iswara.music/.MainActivity -a android.media.action.MEDIA_PLAY_FROM_SEARCH --es query ' + shlex.quote(query)
    started = time.monotonic()
    adb('shell', command)
    report['samples']['start'] = wait(3)
    report['first_playing_observed_ms'] = round((time.monotonic() - started) * 1000)
    time.sleep(10)
    key('pause')
    first_pause = wait(2)
    assert first_pause['position_ms'] >= 5000, 'Posisi audio tidak maju'
    report['samples']['first_pause'] = first_pause
    report['checks']['search_and_native_playback'] = True
    report['checks']['system_pause'] = True
    key('play'); wait(3)
    report['checks']['system_resume'] = True
    adb('shell', 'input', 'keyevent', '223')
    power = adb('shell', 'dumpsys', 'power')
    assert re.search(r'mWakefulness=(Asleep|Dozing)', power), 'Layar belum mati'
    screen_duration = max(12,min(240,args.screen_off_seconds))
    report['screen_off_seconds'] = screen_duration
    captures = []
    deadline = time.monotonic() + screen_duration
    while time.monotonic() < deadline:
        time.sleep(min(5,max(0,deadline-time.monotonic())))
        observed = state()
        captures.append(observed)
        assert observed and observed['state'] not in (1,2,7), 'Audio berhenti atau error saat layar mati'
    report['samples']['screen_off_samples'] = captures
    report['buffering_samples'] = sum(x['state'] == 6 for x in captures)
    report['samples']['screen_off'] = wait(3)
    key('pause')
    second_pause = wait(2)
    assert second_pause['position_ms'] >= first_pause['position_ms'] + screen_duration * 800, 'Audio tidak maju ketika layar mati'
    report['samples']['after_screen_off_pause'] = second_pause
    report['checks']['screen_off_native_playback'] = True
    key('play'); previous = wait(3)
    started = time.monotonic(); key('next')
    report['samples']['next_track'] = wait(3, different=previous['active_id'])
    report['next_playing_observed_ms'] = round((time.monotonic() - started) * 1000)
    time.sleep(5)
    key('pause'); report['samples']['final_pause'] = wait(2)
    assert report['samples']['final_pause']['position_ms'] >= 3000, 'Lagu berikutnya tidak maju'
    report['checks']['next_track_native_playback'] = True
    report['passed'] = True
except Exception as error:
    report['passed'] = False
    report['failure'] = str(error)
finally:
    # Leave the phone awake and the test audio paused.
    try:
        if (state() or {}).get('state') == 3:
            key('pause')
        adb('shell', 'input', 'keyevent', '224')
    except Exception:
        pass
    destination = root / f'docs/verification/device-{args.version}.json'
    destination.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2, ensure_ascii=False))
raise SystemExit(0 if report.get('passed') else 1)
