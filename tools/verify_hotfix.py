#!/usr/bin/env python3
"""Check the released DEX and APK, without claiming a device/emulator test."""
import argparse
import hashlib
import re
import struct
import subprocess
import zipfile
import zlib
from pathlib import Path
from cryptography.hazmat.primitives.serialization import pkcs7
from package_hotfix import attributes
from sign_apk_v2 import verify

def method(text, name):
    for part in text.split('.method ')[1:]:
        if re.search(r'(?:^|\s)'+re.escape(name)+r'\(', part.split('\n', 1)[0]):
            return part.split('.end method', 1)[0]
    raise AssertionError('Missing method: '+name)

def main():
    p = argparse.ArgumentParser()
    for name in ('base', 'updated', 'classpath', 'workdir'):
        p.add_argument(name, type=Path)
    a = p.parse_args()
    a.workdir.mkdir(parents=True, exist_ok=True)
    for name, apk in [('old', a.base), ('new', a.updated)]:
        subprocess.run(['java', '-cp', str(a.classpath), 'com.android.tools.smali.baksmali.Main',
                        'disassemble', str(apk), '-o', str(a.workdir/name)], check=True)
    old_main = (a.workdir/'old/com/focuslock/app/MainActivity.smali').read_text()
    new_main = (a.workdir/'new/com/focuslock/app/MainActivity.smali').read_text()
    unsafe = r'HapticFeedbackConstants;->(?:CONFIRM|REJECT):I'
    assert len(re.findall(unsafe, old_main)) == 3
    assert not re.search(unsafe, new_main)
    for name in ('applyWheelTime', 'playSuccessCelebration'):
        assert 'HapticFeedbackConstants;->CONFIRM:I' in method(old_main, name)
        assert not re.search(unsafe, method(new_main, name))
        assert 'performHapticFeedback(I)Z' in method(new_main, name)
    save = method(new_main, 'startCommitment')
    assert 'LockStore;->configure(' in save
    assert 'playSuccessCelebration()V' in save
    assert 'requestPermissions(' not in save
    assert 'Intent;-><init>(Ljava/lang/String;' not in save
    startup = method(new_main, 'startSavedMonitoring')
    assert 'ProtectionRestarter;->ensureMonitorRunning(' in startup
    assert '.catch Ljava/lang/RuntimeException;' in startup
    assert 'startForegroundService(' not in startup
    callback = (a.workdir/'new/com/focuslock/app/MainActivity$15.smali').read_text()
    bridge = re.search(r'MainActivity;->([^\(]+)\(Lcom/focuslock/app/MainActivity;\)V', method(callback, 'run')).group(1)
    assert 'refreshStatus()V' in method(new_main, bridge)
    print('PASS: all three unsafe API-30 haptic field reads removed from compiled code.')
    print('PASS: timer persistence and save feedback retained; permission prompt removed from Save.')
    print('PASS: monitor startup uses the permission-aware starter and handles RuntimeException.')

    old_classes = {x.relative_to(a.workdir/'old') for x in (a.workdir/'old').rglob('*.smali')}
    new_classes = {x.relative_to(a.workdir/'new') for x in (a.workdir/'new').rglob('*.smali')}
    assert old_classes == new_classes
    changed = {x.name for x in old_classes if (a.workdir/'old'/x).read_bytes() != (a.workdir/'new'/x).read_bytes()}
    assert changed == {'MainActivity.smali', 'BuildConfig.smali', 'RemoteConfigStore.smali', 'SupabaseApi.smali'}
    print('PASS: compiled class inventory preserved; only timer/startup code and version metadata changed.')
    with zipfile.ZipFile(a.base) as old, zipfile.ZipFile(a.updated) as new:
        assert new.testzip() is None
        assert {x for x in old.namelist() if not x.startswith('META-INF/')} == set(new.namelist())
        for name in new.namelist():
            if name not in ('classes.dex', 'AndroidManifest.xml'):
                assert old.read(name) == new.read(name), name
        old_attrs = {(node, name): value for node, name, value, *_ in attributes(old.read('AndroidManifest.xml')) if node in ('manifest', 'uses-sdk')}
        new_attrs = {(node, name): value for node, name, value, *_ in attributes(new.read('AndroidManifest.xml')) if node in ('manifest', 'uses-sdk')}
        assert new_attrs[('manifest','versionCode')] == 123
        assert new_attrs[('manifest','versionName')] == '1.0.23'
        for key, value in old_attrs.items():
            if key[1] not in ('versionName','versionCode'): assert new_attrs[key] == value
        dex = new.read('classes.dex')
        assert dex[12:32] == hashlib.sha1(dex[32:]).digest()
        assert struct.unpack_from('<I', dex, 8)[0] == zlib.adler32(dex[12:]) & 0xffffffff
        signature = next(x for x in old.namelist() if x.endswith('.RSA'))
        previous_cert = pkcs7.load_der_pkcs7_certificates(old.read(signature))[0]
        fingerprint = verify(a.updated.read_bytes(), previous_cert)
        raw = a.updated.read_bytes()
        for info in new.infolist():
            if info.compress_type == zipfile.ZIP_STORED:
                off = info.header_offset
                length, extra = struct.unpack_from('<HH', raw, off+26)
                assert (off+30+length+extra) % 4 == 0, info.filename
    print('PASS: original resources, package, SDK settings and certificate preserved; version 1.0.23 (123).')
    print('PASS: APK v2 signature, DEX checksums, ZIP CRCs and stored-entry alignment verified.')
    print('Certificate SHA256: '+fingerprint)
    print('LIMITATION: static APK/DEX verification only; no connected Android device or emulator.')

if __name__ == '__main__':
    main()
