#!/usr/bin/env python3
"""Check signed release identity and compare its payload with a fresh SDK build."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

def source_hash():
    paths=sorted((ROOT/'app/src/main').rglob('*'))+[ROOT/'app/build.gradle',ROOT/'tools/build.py']
    h=hashlib.sha256()
    for path in sorted(p for p in paths if p.is_file()):
        h.update(path.relative_to(ROOT).as_posix().encode()+b'\0')
        h.update(hashlib.sha256(path.read_bytes()).digest())
    return h.hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--compare-unsigned',type=Path)
    args=parser.parse_args()
    info=json.loads((ROOT/'releases/current.json').read_text())
    apk=ROOT/info['apk']
    assert hashlib.sha256(apk.read_bytes()).hexdigest()==info['sha256'],'Release APK checksum mismatch'
    assert source_hash()==info['source_sha256'],'Source changed after release was built'
    if args.compare_unsigned:
        with zipfile.ZipFile(apk) as signed,zipfile.ZipFile(args.compare_unsigned) as unsigned:
            names=[n for n in unsigned.namelist() if not n.endswith('/') and not n.startswith('META-INF/')]
            signed_names=[n for n in signed.namelist() if not n.endswith('/') and not n.startswith('META-INF/')]
            assert set(names)==set(signed_names),'APK payload entry set differs'
            for name in names:
                assert signed.read(name)==unsigned.read(name),'APK payload differs: '+name
    print('PASS: signed release checksum, source identity'+(' and fresh-build payload' if args.compare_unsigned else ''))

if __name__=='__main__':main()
