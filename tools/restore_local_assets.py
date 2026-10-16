#!/usr/bin/env python3
"""Restore explicitly listed original integration assets from a local project ZIP.
Does not fetch data, restore credentials, or grant redistribution rights.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

def restore(archive, root):
    root = Path(root).resolve()
    files = json.loads((root / 'ASSET_MANIFEST.json').read_text())['files']
    with zipfile.ZipFile(archive) as z:
        names = z.namelist()
        plan = []
        for entry in files:
            rel = entry['path']
            target = (root / rel).resolve()
            if root not in target.parents:
                raise ValueError('Unsafe manifest path')
            candidates = [n for n in names if n == rel or n.endswith('/' + rel)]
            if len(candidates) != 1:
                raise ValueError('Missing/ambiguous original asset: ' + rel)
            info = z.getinfo(candidates[0])
            if info.file_size != entry['size']:
                raise ValueError('Size mismatch: ' + rel)
            with z.open(info) as stream:
                digest = hashlib.sha256()
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(chunk)
            if digest.hexdigest() != entry['sha256']:
                raise ValueError('Hash mismatch: ' + rel)
            plan.append((info, target))
        # Every required asset has passed before the first local write.
        for info, target in plan:
            target.parent.mkdir(parents=True, exist_ok=True)
            with z.open(info) as source, target.open('wb') as output:
                for chunk in iter(lambda: source.read(1024 * 1024), b''):
                    output.write(chunk)
    print('Restored', len(plan), 'local assets. Upstream licenses still apply.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    args = parser.parse_args()
    restore(args.archive, Path(__file__).resolve().parents[1])
