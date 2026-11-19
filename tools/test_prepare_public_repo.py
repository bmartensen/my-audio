"""Safety regression tests for the source-only public export; no external side effects."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('export', Path(__file__).with_name('prepare_public_repo.py'))
export = importlib.util.module_from_spec(spec)
spec.loader.exec_module(export)


class PublicExportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.source = self.base/'source'
        self.source.mkdir()
        subprocess.run(['git', 'init', '-q', '-b', 'main', str(self.source)], check=True)
        (self.source/'.gitignore').write_text('.work/\n*.jks\n', encoding='utf-8')
        (self.source/'README.md').write_text('Public source\n', encoding='utf-8')
        (self.source/'.work').mkdir()
        (self.source/'.work/private.txt').write_text('Personal recording notes', encoding='utf-8')

    def test_copy_excludes_history_and_ignored_files(self):
        files = export.source_manifest(self.source)
        dest = self.base/'dest'
        export.copy_to(self.source, dest, files)
        self.assertEqual(set(files), {'.gitignore', 'README.md'})
        self.assertFalse((dest/'.git').exists())
        self.assertFalse((dest/'.work').exists())

    def test_tracked_secret_is_not_protected_by_ignore(self):
        (self.source/'signing.jks').write_bytes(b'private keystore fixture')
        export.git(self.source, 'add', '-f', 'signing.jks')
        with self.assertRaisesRegex(ValueError, 'Private file'):
            export.source_manifest(self.source)

    def test_secret_content_is_rejected_without_echoing_value(self):
        value = b'ghp_' + b'a'*36
        (self.source/'notes.txt').write_bytes(value)
        with self.assertRaises(ValueError) as caught:
            export.source_manifest(self.source)
        self.assertNotIn(value.decode(), str(caught.exception))

    def test_signing_secret_assignments_are_rejected_without_echo(self):
        for field in ('myAudio.signing.storePassword', 'myAudio.signing.keyPassword',
                      'MY_AUDIO_STORE_PASSWORD', 'MY_AUDIO_KEY_PASSWORD',
                      'martenseningStorePassword', 'martenseningKeyPassword'):
            value = 'example-' + 'sensitive-value'
            with self.subTest(field=field), self.assertRaises(ValueError) as caught:
                export.validate_file('notes.txt', (field + '=' + value).encode())
            self.assertNotIn(value, str(caught.exception))

    def test_populated_destination_is_preserved(self):
        dest = self.base/'dest'
        dest.mkdir()
        (dest/'personal.txt').write_text('keep', encoding='utf-8')
        with self.assertRaises(ValueError):
            export.copy_to(self.source, dest, export.source_manifest(self.source))
        self.assertEqual((dest/'personal.txt').read_text(), 'keep')

    def test_empty_git_is_preserved_but_index_and_objects_block_copy(self):
        dest = self.base/'dest'
        subprocess.run(['git', 'init', '-q', '-b', 'main', str(dest)], check=True)
        before = (dest/'.git/config').read_bytes()
        export.copy_to(self.source, dest, export.source_manifest(self.source))
        self.assertEqual((dest/'.git/config').read_bytes(), before)
        export.git(dest, 'add', 'README.md')
        with self.assertRaises(ValueError):
            export.check_empty_git(dest)

    def test_source_change_and_target_tampering_are_detected(self):
        files = export.source_manifest(self.source)
        dest = self.base/'dest'
        export.copy_to(self.source, dest, files)
        (dest/'README.md').write_text('changed', encoding='utf-8')
        with self.assertRaises(ValueError):
            export.verify(dest, files)
        (self.source/'README.md').write_text('changed', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Source changed'):
            export.copy_to(self.source, self.base/'other', files)

    def test_unsafe_paths_client_state_and_unknown_binaries(self):
        for relative in ('../escape', '.gemini/settings.json', 'local.properties', 'release.pfx'):
            with self.subTest(relative=relative), self.assertRaises(ValueError):
                export.validate_file(relative, b'content')
        with self.assertRaises(ValueError):
            export.validate_file('unexpected.dat', b'\x00\xFF')


if __name__ == '__main__':
    unittest.main()
