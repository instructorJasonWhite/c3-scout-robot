"""Build an Arduino IDE-installable library ZIP, without bundled dependencies."""
from pathlib import Path
import hashlib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LIB = ROOT / 'libraries/ScoutFaces'
TARGET = ROOT / 'artifacts/ScoutFaces-1.0.0.zip'
with zipfile.ZipFile(TARGET, 'w', zipfile.ZIP_DEFLATED) as archive:
    for source in sorted(LIB.rglob('*')):
        if source.is_file() and not {'build', '.git', '__pycache__'}.intersection(source.relative_to(LIB).parts):
            archive.write(source, 'ScoutFaces/' + source.relative_to(LIB).as_posix())
with zipfile.ZipFile(TARGET) as archive:
    assert archive.testzip() is None
    assert 'ScoutFaces/library.properties' in archive.namelist()
    assert 'ScoutFaces/src/ScoutFaces.h' in archive.namelist()
    assert len([n for n in archive.namelist() if n.endswith('.ino')]) == 2
print(TARGET)
print('SHA256:', hashlib.sha256(TARGET.read_bytes()).hexdigest())
