#!/usr/bin/env python3
"""Exercise publication cleanup without contacting GitHub or deleting real releases."""
import copy
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('retention', Path(__file__).with_name('prune-releases.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
releases = [{'id': n, 'tag_name': f'v0.0.{n}', 'draft': False, 'prerelease': n == 2,
             'published_at': f'2026-11-0{n}T00:00:00Z'} for n in range(1, 8)]
releases.append({'id': 8, 'tag_name': 'v0.0.8', 'draft': True, 'prerelease': False, 'published_at': None})
latest = copy.deepcopy(releases[6])
latest['assets'] = [{'name': 'AirPit-v0.0.7.apk', 'state': 'uploaded'}]
keep, remove = module.retained(releases[::-1], 'v0.0.7')
assert [r['id'] for r in keep] == [7, 6, 5]
assert [r['id'] for r in remove] == [4, 3, 2, 1]
assert module.retained(releases[5:7], 'v0.0.7')[1] == []
try:
    module.retained(releases, 'v0.0.6')
    raise AssertionError('Wrong newest tag must block cleanup')
except ValueError:
    pass


def exercise(data, apply):
    outputs = [json.dumps(data).encode(), json.dumps([releases[:4], releases[4:]]).encode()]
    with patch.dict(os.environ, {'GITHUB_REPOSITORY': 'owner/AirPit'}), \
            patch.object(sys, 'argv', ['prune-releases.py', '--latest', 'v0.0.7'] + (['--apply'] if apply else [])), \
            patch.object(module.subprocess, 'check_output', side_effect=outputs), \
            patch.object(module.subprocess, 'run') as commands, contextlib.redirect_stdout(io.StringIO()):
        try:
            module.main()
        except SystemExit:
            assert commands.call_count == 0
            return False
        if apply:
            assert [call.args[0] for call in commands.call_args_list] == [
                ['gh', 'api', '--method', 'DELETE', f'repos/owner/AirPit/releases/{n}'] for n in (4, 3, 2, 1)]
        else:
            assert commands.call_count == 0
        return True


assert exercise(latest, False)
assert exercise(latest, True)
for change in [{'tag_name': 'v0.0.6'}, {'draft': True}, {'prerelease': True}, {'assets': []},
               {'assets': [{'name': 'AirPit-v0.0.7.apk', 'state': 'new'}]}]:
    data = dict(latest, **change)
    assert not exercise(data, True)
print('PASS: release retention keeps the newest three, preserves tags/drafts and blocks unsafe cleanup')
