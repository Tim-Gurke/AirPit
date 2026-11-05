#!/usr/bin/env python3
"""Run deterministic HID/keyboard/motion tests on a normal JDK."""
import os
import subprocess
import tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='airpit-tests-') as tmp:
    sources=list((root/'app/src/main/java/de/airpit/app/core').glob('*.java'))
    subprocess.run(['javac','-encoding','UTF-8','-d',tmp,*map(str,sources),str(root/'tools/CoreChecks.java'),str(root/'tools/TransmissionChecks.java'),str(root/'tools/LayoutChecks.java'),str(root/'tools/InsertionChecks.java'),str(root/'tools/BackupChecks.java')],check=True)
    subprocess.run(['java','-cp',tmp,'CoreChecks'],check=True)
    subprocess.run(['java','-cp',tmp,'TransmissionChecks'],check=True)
    subprocess.run(['java','-cp',tmp,'LayoutChecks'],check=True)
    subprocess.run(['java','-cp',tmp,'InsertionChecks'],check=True)
    subprocess.run(['java','-cp',tmp,'BackupChecks'],check=True)
