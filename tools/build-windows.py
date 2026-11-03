#!/usr/bin/env python3
"""Build the optional portable .NET Framework Windows helper from its C# source."""
import argparse
import hashlib
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DLL_SHA256='37c035e7a58e337c79e0449a338dcc143f8d923ae2b0a027ccb953db53b264b2'

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--dotnet-root',type=Path)
    parser.add_argument('--references',type=Path)
    parser.add_argument('--out',type=Path,default=ROOT/'build/windows')
    parser.add_argument('--self-test',action='store_true')
    args=parser.parse_args()
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=True)
    library=ROOT/'windows/vendor/InTheHand.Net.Bluetooth.dll'
    assert hashlib.sha256(library.read_bytes()).hexdigest()==DLL_SHA256,'Bluetooth library checksum differs'
    common=['/nologo','/target:winexe','/optimize+','/langversion:5','/platform:anycpu','/codepage:65001','/out:'+str(out/'AirPit-Unicode.exe')]
    refs=args.references
    if args.dotnet_root:
        home=args.dotnet_root.resolve();sdk=sorted((home/'sdk').glob('*'))[-1]
        cmd=[str(home/('dotnet.exe' if os.name=='nt' else 'dotnet')),str(sdk/'Roslyn/bincore/csc.dll'),'/deterministic+','/nostdlib+',*common]
        if refs is None:refs=home/'refs/build/.NETFramework/v4.8'
    else:
        if os.name!='nt':raise SystemExit('On Linux pass --dotnet-root and .NET Framework 4.8 reference assemblies.')
        refs=refs or Path(os.environ['WINDIR'])/'Microsoft.NET/Framework64/v4.0.30319'
        cmd=[str(refs/'csc.exe'),*common]
    for name in ['mscorlib','System','System.Core','System.Windows.Forms','System.Drawing','System.Web.Extensions']:cmd.append('/reference:'+str(refs/(name+'.dll')))
    cmd.extend(['/reference:'+str(library),str(ROOT/'windows/AirPitUnicode.cs')])
    env=os.environ.copy();env['DOTNET_CLI_TELEMETRY_OPTOUT']='1';env['DOTNET_SKIP_FIRST_TIME_EXPERIENCE']='1'
    if args.dotnet_root:env['DOTNET_CLI_HOME']=str(args.dotnet_root/'cli-home')
    subprocess.run(cmd,check=True,env=env)
    shutil.copy2(library,out/library.name)
    for name in ['AirPit-Unicode.exe.config','README.txt','LICENSE-32feet.txt']:shutil.copy2(ROOT/'windows'/name,out/name)
    shutil.copy2(ROOT/'LICENSE',out/'LICENSE-AirPit.txt')
    if args.self_test:
        subprocess.run([str(out/'AirPit-Unicode.exe'),'--self-test'],cwd=out,check=True)
        print((out/'AirPit-Unicode-self-test.txt').read_text())
    zip_path=out.parent/'AirPit-Unicode-Windows.zip'
    with zipfile.ZipFile(zip_path,'w',zipfile.ZIP_DEFLATED) as z:
        for name in ['AirPit-Unicode.exe','InTheHand.Net.Bluetooth.dll','AirPit-Unicode.exe.config','README.txt','LICENSE-32feet.txt','LICENSE-AirPit.txt']:z.write(out/name,name)
    print(zip_path)

if __name__=='__main__':main()
