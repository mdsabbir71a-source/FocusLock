#!/usr/bin/env python3
"""Package the instruction-aware timer hotfix, keeping all other APK resources."""
import argparse
import hashlib
import struct
import zipfile
from pathlib import Path

BASE_SHA256 = 'c511ffcae49b1d570ba099dca972c3cf0377e97af3a6afcee998ff46bc29bb67'

def u16(data, offset):
    return struct.unpack_from('<H', data, offset)[0]

def u32(data, offset):
    return struct.unpack_from('<I', data, offset)[0]

def strings(data):
    pos = 8
    assert u16(data, pos) == 1
    count, flags = u32(data, pos+8), u32(data, pos+16)
    assert not flags & 0x100, 'This release uses UTF-16 binary XML'
    start = pos+u32(data, pos+20)
    result = []
    for i in range(count):
        off = start+u32(data, pos+28+i*4)
        length = u16(data, off)
        result.append(bytes(data[off+2:off+2+length*2]).decode('utf-16le'))
    return result

def attributes(data):
    pool = strings(data)
    pos = 8
    while pos < len(data):
        kind, size = u16(data, pos), u32(data, pos+4)
        if kind == 0x102:
            node = pool[u32(data, pos+20)]
            attrs = pos+16+u16(data, pos+24)
            for i in range(u16(data, pos+28)):
                off = attrs+i*u16(data, pos+26)
                name, typ, value = pool[u32(data, off+4)], data[off+15], u32(data, off+16)
                yield node, name, pool[value] if typ == 3 else value, off, typ
        assert size >= 8
        pos += size

def patch_manifest(raw):
    data = bytearray(raw)
    old = '1.0.22'.encode('utf-16le')
    assert data.count(old) == 1
    data = data.replace(old, '1.0.23'.encode('utf-16le'))
    found = 0
    for node, name, value, offset, typ in attributes(data):
        if node == 'manifest' and name == 'versionCode':
            assert typ == 0x10 and value == 122
            struct.pack_into('<I', data, offset+16, 123)
            found += 1
    assert found == 1
    return data

def main():
    p = argparse.ArgumentParser()
    p.add_argument('base', type=Path)
    p.add_argument('dex', type=Path)
    p.add_argument('output', type=Path)
    args = p.parse_args()
    assert hashlib.sha256(args.base.read_bytes()).hexdigest() == BASE_SHA256, 'Unexpected base APK'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.base) as src, zipfile.ZipFile(args.output, 'w') as dst:
        for info in src.infolist():
            if info.filename.startswith('META-INF/'):
                continue
            raw = src.read(info.filename)
            if info.filename == 'classes.dex':
                raw = args.dex.read_bytes()
            elif info.filename == 'AndroidManifest.xml':
                raw = patch_manifest(raw)
            # Android 11+ requires a stored, 4-byte-aligned resource table.
            if info.filename == 'resources.arsc':
                info.compress_type = zipfile.ZIP_STORED
            if info.compress_type == zipfile.ZIP_STORED:
                start = dst.fp.tell() + 30 + len(info.filename.encode('utf-8')) + len(info.extra)
                if start % 4:
                    padding = (-start - 4) % 4
                    info.extra += struct.pack('<HH', 0xd935, padding) + b'\0' * padding
            dst.writestr(info, raw)
    with zipfile.ZipFile(args.output) as z:
        assert z.testzip() is None
        for node, name, value, _, _ in attributes(z.read('AndroidManifest.xml')):
            if node in ('manifest', 'uses-sdk') and name in ('package', 'versionCode', 'versionName', 'minSdkVersion', 'targetSdkVersion'):
                print(node, name, value)

if __name__ == '__main__':
    main()
