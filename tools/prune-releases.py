#!/usr/bin/env python3
"""Keep three public releases after verifying the newly published Latest release.

Only GitHub release publications are removed. Git tags and repository files remain.
"""
import argparse
import json
import os
import subprocess


def retained(releases, latest_tag):
    published = sorted((r for r in releases if not r['draft']),
                       key=lambda r: (r.get('published_at') or '', r['id']), reverse=True)
    if not published or published[0]['tag_name'] != latest_tag or published[0]['prerelease']:
        raise ValueError('Expected the new regular release to be the newest publication')
    return published[:3], published[3:]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--latest', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    repo = os.environ['GITHUB_REPOSITORY']
    latest = json.loads(subprocess.check_output(['gh', 'api', f'repos/{repo}/releases/latest']))
    if latest['tag_name'] != args.latest or latest['draft'] or latest['prerelease']:
        raise SystemExit('Latest release verification failed; nothing removed')
    if not any(a['name'] == f'AirPit-{args.latest}.apk' and a['state'] == 'uploaded' for a in latest['assets']):
        raise SystemExit('New APK missing; nothing removed')
    pages = json.loads(subprocess.check_output(['gh', 'api', '--paginate', '--slurp', f'repos/{repo}/releases?per_page=100']))
    keep, remove = retained([r for page in pages for r in page], args.latest)
    print('Retaining:', ', '.join(r['tag_name'] for r in keep))
    for release in remove:
        print(('Removing publication: ' if args.apply else 'Would remove publication: ') + release['tag_name'])
        if args.apply:
            subprocess.run(['gh', 'api', '--method', 'DELETE', f'repos/{repo}/releases/{release["id"]}'], check=True)


if __name__ == '__main__':
    main()
