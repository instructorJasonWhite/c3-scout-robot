"""Verify Scout's published files, editable sources, downloads and guide links."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urljoin, urlsplit, unquote
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Page(HTMLParser):
    def __init__(self, path):
        super().__init__()
        self.ids, self.refs = set(), []
        self.feed(path.read_text(encoding='utf-8-sig'))
    def handle_starttag(self, tag, attributes):
        values = dict(attributes)
        if values.get('id'):
            self.ids.add(values['id'])
        for name in ('href','src'):
            if values.get(name):
                self.refs.append(values[name])


def main():
    project = json.loads((ROOT/'PROJECT-MANIFEST.json').read_text(encoding='utf8'))
    assert project['snapshot_date'] == '2026-10-21'
    assert project['storage_cases']['guide'] == 'accessories.html#choose-case'
    for item in project['files']:
        path = (ROOT/item['file']).resolve()
        assert path.is_relative_to(ROOT) and path.is_file(), item['file']
        assert path.stat().st_size == item['bytes'] and digest(path) == item['sha256'], item['file']
    control = ROOT/'06-Scout-Control'
    release = json.loads((control/'MANIFEST.json').read_text(encoding='utf8'))
    canonical = 0
    for item in release['files']:
        frozen = control/item['file']
        assert digest(frozen) == item['sha256'], item['file']
        if item['file'].startswith('source/'):
            relative = item['file'].removeprefix('source/')
            assert (ROOT/relative).read_bytes() == frozen.read_bytes(), relative
            canonical += 1
    sync = json.loads((ROOT/'REPOSITORY-SYNC.json').read_text(encoding='utf8'))
    for item in sync['additional_files']:
        assert digest(ROOT/item['file']) == item['sha256'], item['file']
    cases = 0
    for variant in ('Bumper','OLED','ESP32-CAM'):
        folder = ROOT/'08-Storage-Cases'/variant
        stem = f'Scout-{variant}-Case-v8'
        for suffix in ('.f3d','.step','.stl','-Box.stl','-Latch.stl',
                       '-P1S-PLA-print.3mf','-Thin-Walls.zip',
                       '-closed.png','-open.png','-fit.png'):
            assert (folder/(stem+suffix)).is_file(), stem+suffix
        assert not any('-v6' in p.name or '-v7' in p.name for p in folder.iterdir())
        cases += 1
    archives = [ROOT/p['file'] for p in project['files'] if p['file'].endswith('.zip')]
    archives += [ROOT/p['file'] for p in sync['additional_files'] if p['file'].endswith('.zip')]
    for archive in archives:
        with zipfile.ZipFile(archive) as package:
            assert package.testzip() is None, archive.name
            assert len(package.namelist()) == len(set(package.namelist())), archive.name
            assert all(not p.startswith(('/','\\')) and '..' not in Path(p).parts for p in package.namelist())
    pages = {name:Page(ROOT/(name+'.html')) for name in ('index','START-HERE','build','board','code','accessories')}
    targets, anchors = set(), 0
    cache = {}
    for name, page in pages.items():
        assert len(page.ids)
        for reference in page.refs:
            url = urlsplit(urljoin(f'http://scout.local/{name}.html',reference))
            if url.netloc != 'scout.local':
                continue
            target = (ROOT/unquote(url.path).lstrip('/')).resolve()
            assert target.is_relative_to(ROOT) and target.is_file(), f'{name}: {reference}'
            targets.add(target)
            if url.fragment and target.suffix == '.html':
                if target not in cache:
                    cache[target] = Page(target)
                assert unquote(url.fragment) in cache[target].ids, f'{name}: {reference}'
                anchors += 1
    readme = (ROOT/'README.md').read_text(encoding='utf8')
    for ref in re.findall(r'\]\(([^)]+)\)',readme):
        url = urlsplit(ref)
        if not url.scheme and not url.netloc:
            assert (ROOT/unquote(url.path)).exists(), f'README link: {ref}'
    print(f'PASS: {len(project["files"])} published files, {canonical} editable sources, '
          f'{cases} v8 cases, {len(archives)} ZIPs, {len(targets)} guide targets, {anchors} anchors.')


if __name__ == '__main__':
    main()
