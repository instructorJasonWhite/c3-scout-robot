"""Rebuild Scout's published guide from the editable repository fragments.

Run from any directory with Python 3.9 or newer. The six HTML pages and shared
guide assets are written at the repository root. Released downloads, firmware,
native design files, and the annotated assembly drawing remain unchanged.
"""
from pathlib import Path
from html.parser import HTMLParser
import hashlib
import html
import json

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs/builder-guide'
ASSETS = ROOT / 'assets'
DATE = '21 October 2026'
PAGES = [
    ('build', 'The Build', 'Put Scout together.', 'Nine illustrated steps, the right hardware at each stage, and the current printable and editable files.', 'Rev B mechanics'),
    ('board', 'The Board', 'Meet the electronics.', 'Explore the current board, understand its connections, and download the matching schematic and manufacturing files.', 'Carrier C v10 · Silk 03'),
    ('code', 'The Code', 'Bring Scout to life.', 'Load the current firmware, drive from a browser, and add ten animated expressions with ScoutFaces.', 'Web release · 19 October 2026'),
    ('accessories', 'The Accessories', 'A case for your Scout.', 'Choose a case for your robot, print the matching parts, and explore the optional OLED and camera mounts.', 'Three variants · all v8'),
]


class Contents(HTMLParser):
    def __init__(self):
        super().__init__()
        self.current = None
        self.level = None
        self.entries = []
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in ('section', 'article') and attrs.get('id'):
            self.current = attrs['id']
        if tag == 'h2' and self.current:
            self.level = []
    def handle_data(self, data):
        if self.level is not None:
            self.level.append(data)
    def handle_endtag(self, tag):
        if tag == 'h2' and self.level is not None:
            self.entries.append((self.current, ''.join(self.level)))
            self.level = None


def nav(current):
    return '<nav class="topnav" aria-label="Scout guides">' + ''.join(
        f'<a href="{key}.html"' + (' aria-current="page"' if key == current else '') + f'>{label.removeprefix("The ")}</a>'
        for key, label, *_ in PAGES) + '<a class="source-link" href="https://github.com/instructorJasonWhite/c3-scout-robot" target="_blank" rel="noopener noreferrer">GitHub source <span aria-hidden="true">↗</span></a></nav>'


def shell(key, label, title, description, revision, content):
    is_home = key == 'index'
    document_title = 'Scout — Build your own robot' if is_home else f'{label} — Scout Builder Guide'
    header = f'<header class="site-header"><div class="header-inner"><a class="brand" href="index.html">SCOUT<span>THE BUILDER GUIDE</span></a>{nav(key)}</div></header>'
    heading = '' if is_home else f'<div class="page-heading"><p class="eyebrow">Scout / {html.escape(label)}</p><h1>{html.escape(title)}</h1><p class="lead">{html.escape(description)}</p><div class="edition"><span>{html.escape(revision)}</span><span>Guide updated {DATE}</span></div></div>'
    if not is_home:
        toc = Contents()
        toc.feed(content)
        seen = set()
        links = ''
        for anchor, text in toc.entries:
            if anchor not in seen:
                links += f'<a href="#{html.escape(anchor)}">{html.escape(text)}</a>'
                seen.add(anchor)
        content = f'<div class="content-layout"><aside class="page-contents" aria-label="On this page"><p>On this page</p>{links}</aside><div class="page-content">{content}</div></div>'
    footer = '<footer class="site-footer"><p>Scout builder documentation · Rev B mechanics · Carrier C v10 · web firmware 19 October 2026 · ScoutFaces 1.0.0 · v8 cases</p><a href="index.html">Back to Scout</a></footer>'
    return f'<!doctype html>\n<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="description" content="{html.escape(description, quote=True)}"><title>{document_title}</title><link rel="stylesheet" href="assets/guide.css"></head><body><a class="skip" href="#main">Skip to content</a>{header}<main class="page-wrap" id="main">{heading}{content}</main>{footer}</body></html>\n'


def guide_pages():
    pages = {}
    for key, label, title, description, revision in [('index','Scout','Build your own Scout.','Four practical guides to print, assemble, wire, program and customize the Scout robot.','')] + PAGES:
        fragment = (DOCS / f'{key}-content.html').read_text(encoding='utf8')
        if key == 'build':
            # Keep the published drawing as an independent, editable SVG asset.
            drawing_section = '<section class="section" id="assembly-drawing"><h2>See how it fits together.</h2><figure class="figure"><a href="assets/build/Scout-Assembly-Overview.svg"><img src="assets/build/Scout-Assembly-Overview.svg" width="1800" height="1300" alt="Annotated Scout exploded assembly drawing with parts and fastener sizes"></a><figcaption>Rev B mechanics. Open the drawing for the full-size view.</figcaption></figure><p><a href="assets/build/Scout-Assembly-Overview.svg" download>Download the assembly drawing · SVG</a></p></section>'
            fragment = fragment.replace('<section class="section" id="orientation">', drawing_section + '<section class="section" id="orientation">', 1)
        if key != 'index':
            index = [p[0] for p in PAGES].index(key)
            following = PAGES[(index + 1) % len(PAGES)]
            fragment += f'<div class="next-page"><a href="index.html">← All four guides</a><a href="{following[0]}.html">Continue to {following[1]} →</a></div>'
        pages[ROOT / f'{key}.html'] = shell(key,label,title,description,revision,fragment).encode('utf8')
    pages[ROOT / 'START-HERE.html'] = pages[ROOT / 'index.html']
    return pages


def write_if_changed(path, data):
    if path.exists() and path.read_bytes() == data:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return True


def update_manifest(outputs):
    """Refresh generated-file metadata without rewriting an unchanged manifest."""
    path = ROOT / 'PROJECT-MANIFEST.json'
    if not path.exists():
        return False
    manifest = json.loads(path.read_text(encoding='utf8'))
    entries = {entry['file']: entry for entry in manifest['files']}
    changed = False
    for target, data in outputs.items():
        relative = target.relative_to(ROOT).as_posix()
        entry = entries.get(relative)
        if entry is None:
            entry = {'file': relative}
            manifest['files'].append(entry)
            entries[relative] = entry
        metadata = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
        if any(entry.get(key) != value for key, value in metadata.items()):
            entry.update(metadata)
            changed = True
    if changed:
        write_if_changed(path, (json.dumps(manifest, indent=2) + '\n').encode('utf8'))
    return changed


def main():
    drawing = ASSETS / 'build/Scout-Assembly-Overview.svg'
    if not drawing.is_file():
        raise FileNotFoundError(f'Missing published assembly drawing: {drawing.relative_to(ROOT)}')
    outputs = guide_pages()
    outputs[ASSETS / 'guide.css'] = (DOCS / 'guide.css').read_bytes()
    for source in sorted((DOCS / 'assets').rglob('*')):
        if source.is_file():
            target = ASSETS / source.relative_to(DOCS / 'assets')
            if target == drawing:
                continue
            outputs[target] = source.read_bytes()
    changed = sum(write_if_changed(path, data) for path, data in outputs.items())
    manifest_changed = update_manifest(outputs)
    print(f'Built 6 guide pages; {changed} guide files changed; manifest updated: {manifest_changed}.')
    print('Preview from the repository root: python -m http.server 8000')
    print('Open http://127.0.0.1:8000/index.html')


if __name__ == '__main__':
    main()
