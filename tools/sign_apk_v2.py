#!/usr/bin/env python3
"""Sign a minSdk 26 APK with its existing production certificate (v2).
Format: https://source.android.com/docs/security/features/apksigning/v2
No private key material is written to the output or log.
"""
import argparse
import hashlib
import struct
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs12

MAGIC = b'APK Sig Block 42'
V2_ID = 0x7109871a
RSA_SHA256 = 0x0103

def u32(n): return struct.pack('<I', n)
def u64(n): return struct.pack('<Q', n)
def lp(data): return u32(len(data)) + data
def read32(data, off): return struct.unpack_from('<I', data, off)[0]
def read64(data, off): return struct.unpack_from('<Q', data, off)[0]

def zip_sections(data):
    eocd = data.rfind(b'PK\x05\x06', max(0, len(data)-65557))
    assert eocd >= 0
    assert eocd+22+struct.unpack_from('<H', data, eocd+20)[0] == len(data)
    central = read32(data, eocd+16)
    assert central+read32(data, eocd+12) == eocd
    return central, eocd

def content_digest(sections):
    chunks = []
    for section in sections:
        for start in range(0, len(section), 1024*1024):
            chunk = section[start:start+1024*1024]
            chunks.append(hashlib.sha256(b'\xa5'+u32(len(chunk))+chunk).digest())
    return hashlib.sha256(b'\x5a'+u32(len(chunks))+b''.join(chunks)).digest()

def take(data, offset=0):
    end = offset+4+read32(data, offset)
    assert end <= len(data)
    return data[offset+4:end], end

def sign(raw, key, cert):
    central, eocd = zip_sections(raw)
    assert raw[central-16:central] != MAGIC, 'Input must be unsigned'
    digest = content_digest((raw[:central], raw[central:eocd], raw[eocd:]))
    certificate = cert.public_bytes(serialization.Encoding.DER)
    public_key = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    signed_data = lp(lp(u32(RSA_SHA256)+lp(digest)))+lp(lp(certificate))+lp(b'')
    signature = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signer = lp(signed_data)+lp(lp(u32(RSA_SHA256)+lp(signature)))+lp(public_key)
    value = lp(lp(signer))
    pair = u64(4+len(value))+u32(V2_ID)+value
    size = len(pair)+24
    block = u64(size)+pair+u64(size)+MAGIC
    new_eocd = bytearray(raw[eocd:])
    struct.pack_into('<I', new_eocd, 16, central+len(block))
    return raw[:central]+block+raw[central:eocd]+new_eocd

def verify(data, expected_certificate):
    data = bytes(data)
    central, eocd = zip_sections(data)
    assert data[central-16:central] == MAGIC
    size = read64(data, central-24)
    start = central-size-8
    assert start >= 0 and read64(data, start) == size
    pos, value = start+8, None
    while pos < central-24:
        pair_size = read64(data, pos)
        if read32(data, pos+8) == V2_ID:
            value = data[pos+12:pos+8+pair_size]
        pos += 8+pair_size
    assert pos == central-24 and value is not None
    signers, end = take(value)
    assert end == len(value)
    signer, end = take(signers)
    assert end == len(signers)
    signed_data, offset = take(signer)
    signatures, offset = take(signer, offset)
    pub, offset = take(signer, offset)
    assert offset == len(signer)
    signature_record, end = take(signatures)
    assert end == len(signatures) and read32(signature_record, 0) == RSA_SHA256
    signature, end = take(signature_record, 4)
    assert end == len(signature_record)
    serialization.load_der_public_key(pub).verify(signature, signed_data, padding.PKCS1v15(), hashes.SHA256())
    digests, offset = take(signed_data)
    certs, offset = take(signed_data, offset)
    attrs, offset = take(signed_data, offset)
    assert offset == len(signed_data) and not attrs
    cert_der, end = take(certs)
    assert end == len(certs) and cert_der == expected_certificate.public_bytes(serialization.Encoding.DER)
    cert = x509.load_der_x509_certificate(cert_der)
    assert cert.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo) == pub
    digest_record, end = take(digests)
    assert end == len(digests) and read32(digest_record, 0) == RSA_SHA256
    digest, end = take(digest_record, 4)
    assert end == len(digest_record)
    normalized_eocd = bytearray(data[eocd:])
    struct.pack_into('<I', normalized_eocd, 16, start)
    assert digest == content_digest((data[:start], data[central:eocd], normalized_eocd))
    return cert.fingerprint(hashes.SHA256()).hex()

def main():
    p = argparse.ArgumentParser()
    for name in ('input', 'output', 'keystore', 'password_file'):
        p.add_argument(name, type=Path)
    a = p.parse_args()
    key, cert, _ = pkcs12.load_key_and_certificates(a.keystore.read_bytes(), a.password_file.read_bytes().strip())
    result = sign(a.input.read_bytes(), key, cert)
    fingerprint = verify(result, cert)
    # A changed payload must fail verification, not only a matching signature.
    altered = bytearray(result)
    altered[40] ^= 1
    try:
        verify(altered, cert)
    except (AssertionError, ValueError):
        pass
    else:
        raise AssertionError('Tampered APK incorrectly passed verification')
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_bytes(result)
    print('APK v2 signature and content digest verified; tamper test passed.')
    print('Signer SHA256:', fingerprint)

if __name__ == '__main__':
    main()
