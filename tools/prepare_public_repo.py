"""Prepare a reviewed source-only copy; never initialize, stage, commit or push Git.

Requires Python 3.10+ and Git. Reports finding locations, never matching secret values.
Run --help for manifest, copy and verification modes. Use a dedicated secret scanner too.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[1]
PRIVATE_DIRS = {
    '.git', '.gradle', '.kotlin', '.venv', '.work', '.vscode', '.idea', '.aws', '.ssh',
    '.cxx', '.externalnativebuild', '__pycache__', 'node_modules', 'build', 'captures',
    'key', 'keys', 'certificates', 'secrets',
}
PRIVATE_SUFFIXES = {
    '.jks', '.keystore', '.p12', '.pfx', '.pem', '.key', '.cer', '.crt', '.der',
    '.mobileprovision', '.apk', '.aab', '.msi', '.msix', '.exe', '.dll', '.log',
    '.hprof', '.zip', '.7z', '.db', '.sqlite',
}
PRIVATE_NAMES = {
    'local.properties', 'keystore.properties', 'key.properties', 'signing.properties',
    'credentials.json', 'google-services.json', 'id_rsa', 'id_ed25519',
}
BINARY_EXACT = {
    'gradle/wrapper/gradle-wrapper.jar',
    'shared/src/commonMain/composeResources/files/preview/short-jazzy-tune.wav',
}
SECRET_PATTERNS = {
    'private-key': re.compile(rb'-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----'),
    'github-token': re.compile(rb'\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{40,})\b'),
    'google-api-key': re.compile(rb'\bAIza[0-9A-Za-z_-]{35}\b'),
    'aws-access-key': re.compile(rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'credential-url': re.compile(rb'https?://[^\s/:]+:[^\s/@]+@'),
    'assigned-signing-secret': re.compile(rb'(?im)^\s*(?:martensening(?:StorePassword|KeyPassword)|myAudio\.signing\.(?:storePassword|keyPassword)|MY_AUDIO_(?:STORE|KEY)_PASSWORD)\s*[=:]\s*\S+'),
}


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ['git', '-c', f'safe.directory={root.as_posix()}', '-C', str(root), *args],
        check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    return result.stdout.decode('utf-8')


def no_links(path: Path) -> None:
    """Reject symlinks and Windows reparse points in every existing ancestor."""
    for parent in (path, *path.parents):
        if parent.is_symlink() or (parent.exists() and getattr(parent.lstat(), 'st_file_attributes', 0) & 0x400):
            raise ValueError(f'Link/reparse point is not allowed: {parent}')


def validate_file(relative: str, data: bytes) -> None:
    p = Path(relative)
    parts = [part.lower() for part in p.parts]
    if p.is_absolute() or '..' in parts or not parts or ':' in relative:
        raise ValueError(f'Unsafe path: {relative}')
    if set(parts) & PRIVATE_DIRS or p.suffix.lower() in PRIVATE_SUFFIXES:
        raise ValueError(f'Private file/artifact: {relative}')
    if (p.name.lower() in PRIVATE_NAMES or p.name.lower().startswith(('.env', 'service-account'))
            or p.name.lower().endswith('.local.properties')):
        raise ValueError(f'Local configuration: {relative}')
    # Only reviewed, portable harness entrypoints, never client credentials/state.
    if parts[0] in ('.codex', '.claude', '.gemini', '.agent'):
        if not (len(parts) == 4 and parts[1] == 'skills' and parts[2] in ('start', 'wrap', 'grill-me') and parts[3] == 'skill.md'):
            raise ValueError(f'Unexpected client configuration: {relative}')
    for label, pattern in SECRET_PATTERNS.items():
        match = pattern.search(data)
        if match:
            line = data[:match.start()].count(b'\n') + 1
            raise ValueError(f'Potential secret ({label}): {relative}:{line}')
    if data.startswith(b'\x89PNG\r\n\x1a\n'):
        if p.suffix.lower() != '.png':
            raise ValueError(f'Misnamed PNG: {relative}')
        return
    if relative in BINARY_EXACT or (p.suffix.lower() == '.ico' and relative == 'desktop/icons/my-audio.ico'):
        return
    try:
        data.decode('utf-8-sig')
    except UnicodeDecodeError as exc:
        raise ValueError(f'Unreviewed binary/non-UTF8 file: {relative}') from exc
    if b'\0' in data:
        raise ValueError(f'Unreviewed binary: {relative}')


def source_manifest(root: Path) -> dict[str, str]:
    paths = git(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard').split('\0')
    files = {}
    for relative in sorted(set(filter(None, paths))):
        path = root / relative
        no_links(path)
        if not path.exists():  # deletions in the working tree are not exported
            continue
        if not path.is_file():
            raise ValueError(f'Not a regular file: {relative}')
        data = path.read_bytes()
        validate_file(relative, data)
        files[relative] = hashlib.sha256(data).hexdigest()
    if not files:
        raise ValueError('No candidate files found.')
    return files


def check_empty_git(destination: Path) -> None:
    metadata = destination / '.git'
    if not metadata.exists():
        return
    no_links(metadata)
    if not metadata.is_dir() or git(destination, 'rev-parse', '--show-toplevel').strip().replace('\\', '/') != destination.as_posix():
        raise ValueError('Target must have its own ordinary, empty Git directory.')
    if git(destination, 'for-each-ref').strip() or git(destination, 'ls-files').strip():
        raise ValueError('Target contains refs or an index; refusing to alter it.')
    if git(destination, 'reflog', '--all', '--format=%H').strip():
        raise ValueError('Target contains reflog history.')
    # Inspect actual object files too, including unreachable objects/alternates.
    if any(p.is_file() for p in (metadata/'objects').rglob('*')):
        raise ValueError('Target contains Git objects or object metadata.')


def verify(destination: Path, files: dict[str, str]) -> None:
    no_links(destination)
    actual = set()
    for directory, dirs, names in os.walk(destination):
        base = Path(directory)
        if base == destination:
            dirs[:] = [d for d in dirs if d != '.git']
        for name in dirs + names:
            no_links(base/name)
        for name in names:
            path = base/name
            relative = path.relative_to(destination).as_posix()
            actual.add(relative)
            data = path.read_bytes()
            validate_file(relative, data)
            if hashlib.sha256(data).hexdigest() != files.get(relative):
                raise ValueError(f'Unexpected or changed target file: {relative}')
    if actual != set(files):
        raise ValueError('Target file set differs from manifest.')
    check_empty_git(destination)


def copy_to(root: Path, destination: Path, files: dict[str, str]) -> None:
    no_links(destination)
    if destination.exists():
        if not destination.is_dir() or any(p.name != '.git' for p in destination.iterdir()):
            raise ValueError('Destination must be empty (an empty .git is allowed); no overwrite or deletion.')
        check_empty_git(destination)
    destination.mkdir(parents=True, exist_ok=True)
    for relative, digest in files.items():
        source = root/relative
        no_links(source)
        data = source.read_bytes()
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError(f'Source changed since inspection: {relative}')
        target = destination/relative
        no_links(target)
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as stream:
            stream.write(data)
    verify(destination, files)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True, help='Local JSON report outside the publication file set')
    parser.add_argument('--destination', type=Path, help='New/empty directory; never copies .git')
    parser.add_argument('--verify', type=Path, help='Verify existing target against saved manifest without writing')
    args = parser.parse_args()
    if args.verify and args.destination:
        parser.error('--verify and --destination are mutually exclusive')
    if args.verify:
        files = json.loads(args.manifest.read_text(encoding='utf-8'))['files']
        verify(args.verify.absolute(), files)
        print(f'Verified {len(files)} files; target matches manifest; no Git history.')
        return
    files = source_manifest(ROOT)
    report = args.manifest.absolute()
    if report == ROOT or report.is_relative_to(ROOT) and report.relative_to(ROOT).as_posix() in files:
        raise ValueError('Manifest must not overwrite a candidate file.')
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({'files': files}, indent=2) + '\n', encoding='utf-8')
    if args.destination:
        copy_to(ROOT, args.destination.absolute(), files)
    print(f'Inspected {len(files)} files' + ('; copied and hash-verified.' if args.destination else '.'))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
