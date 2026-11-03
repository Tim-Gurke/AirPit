#!/usr/bin/env python3
"""Small dependency-free APK build using Android SDK 35 build tools and JDK 17.

AIRPIT_KEYSTORE and AIRPIT_STORE_PASSWORD select a persistent signing identity.
No signing material belongs in this public repository.
"""
import argparse
import os
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def run(command, env):
    subprocess.run([str(s) for s in command], check=True, env=env)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=ROOT / 'build')
    parser.add_argument('--smoke-api26', action='store_true', help='Internal visual QA only; never distribute this APK.')
    args = parser.parse_args()
    sdk_name = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk_name:
        raise SystemExit('Set ANDROID_HOME to an Android SDK with platform and build tools 35.')
    sdk = Path(sdk_name)
    bt = sdk / 'build-tools/35.0.0'
    android_jar = sdk / 'platforms/android-35/android.jar'
    env = os.environ.copy()
    if env.get('JAVA_HOME'):
        env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env['PATH']
    build = args.out.resolve()
    build.mkdir(parents=True, exist_ok=True)
    for name in ('classes', 'dex', 'generated'):
        target = build / name
        if target.exists():
            shutil.rmtree(target)
        target.mkdir()
    gradle = (ROOT / 'app/build.gradle').read_text()
    version = re.search(r"versionName '([^']+)'", gradle).group(1)
    code = re.search(r'versionCode (\d+)', gradle).group(1)
    min_sdk = 26 if args.smoke_api26 else 28
    manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
    manifest = manifest.replace('<manifest ', '<manifest package="de.airpit.app" android:versionCode="' + code + '" android:versionName="' + version + '" ', 1)
    manifest = manifest.replace('    <uses-permission', f'    <uses-sdk android:minSdkVersion="{min_sdk}" android:targetSdkVersion="35" />\n    <uses-permission', 1)
    (build / 'AndroidManifest.xml').write_text(manifest)
    run([bt / 'aapt2', 'compile', '--dir', ROOT / 'app/src/main/res', '-o', build / 'resources.zip'], env)
    run([bt / 'aapt2', 'link', '-I', android_jar, '--manifest', build / 'AndroidManifest.xml', '--java', build / 'generated', '-o', build / 'resources.apk', build / 'resources.zip'], env)
    config = build / 'generated/de/airpit/app/BuildConfig.java'
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text(f'package de.airpit.app; public final class BuildConfig {{ public static final String VERSION_NAME="{version}"; public static final int VERSION_CODE={code}; }}\n')
    sources = sorted((ROOT / 'app/src/main/java').rglob('*.java')) + sorted((build / 'generated').rglob('*.java'))
    bootclasspath = str(android_jar) + os.pathsep + str(bt / 'core-lambda-stubs.jar')
    run(['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', bootclasspath, '-d', build / 'classes', *sources], env)
    run([bt / 'd8', '--lib', android_jar, '--min-api', str(min_sdk), '--output', build / 'dex', *sorted((build / 'classes').rglob('*.class'))], env)
    unsigned = build / 'unsigned.apk'
    shutil.copy2(build / 'resources.apk', unsigned)
    with zipfile.ZipFile(unsigned, 'a') as apk:
        for dex in sorted((build / 'dex').glob('*.dex')):
            apk.write(dex, dex.name, compress_type=zipfile.ZIP_DEFLATED)
    aligned = build / 'aligned.apk'
    run([bt / 'zipalign', '-p', '-f', '4', unsigned, aligned], env)
    keystore = env.get('AIRPIT_KEYSTORE')
    suffix = '-smoke-only' if args.smoke_api26 else ''
    output = build / f'AirPit-v{version}{suffix}.apk'
    if keystore:
        if not env.get('AIRPIT_STORE_PASSWORD'):
            raise SystemExit('AIRPIT_STORE_PASSWORD is required when a keystore is selected.')
        run([bt / 'apksigner', 'sign', '--ks', keystore, '--ks-key-alias', env.get('AIRPIT_KEY_ALIAS', 'airpit'), '--ks-pass', 'env:AIRPIT_STORE_PASSWORD', '--key-pass', 'env:AIRPIT_STORE_PASSWORD', '--out', output, aligned], env)
        run([bt / 'apksigner', 'verify', '--verbose', output], env)
    else:
        output = output.with_name(output.stem + '-unsigned.apk')
        shutil.copy2(aligned, output)
    print(output)

if __name__ == '__main__':
    main()
