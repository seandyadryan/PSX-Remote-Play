"""Validate requested Play asset dimensions and sizes, without modifying any image."""
import hashlib
import json
from pathlib import Path
import struct

root = Path(__file__).resolve().parent
records = []


def png(path, expected_size, limit_mib, require_rgb=False):
    raw = path.read_bytes()
    assert raw[:8] == b'\x89PNG\r\n\x1a\n', f'{path.name}: not PNG'
    width, height, depth, color = struct.unpack('>IIBB', raw[16:26])
    assert (width, height) == expected_size, f'{path.name}: wrong dimensions'
    assert len(raw) <= limit_mib * 1024 * 1024, f'{path.name}: too large'
    if require_rgb:
        assert depth == 8 and color == 2, 'Feature graphic must be opaque 24-bit RGB'
    records.append({'file': path.relative_to(root).as_posix(), 'width': width,
                    'height': height, 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()})


for filename, limit in [('short-description.txt', 80), ('full-description.txt', 4000)]:
    path = root / 'en-US' / filename
    text = path.read_text(encoding='utf-8').strip()
    assert 0 < len(text) <= limit, f'{filename}: character limit exceeded'
    records.append({'file': path.relative_to(root).as_posix(), 'characters': len(text), 'limit': limit})

png(root / 'feature-graphic.png', (1024, 500), 15, require_rgb=True)
for category, dimensions in [('phone', (1080, 1920)), ('desktop', (1920, 1080))]:
    images = sorted((root / 'screenshots' / category).glob('*.png'))
    minimum = 2 if category == 'phone' else 4
    assert minimum <= len(images) <= 8, f'{category}: expected {minimum}–8 screenshots'
    for path in images:
        png(path, dimensions, 8)

policy = (root.parent / 'privacy-policy' / 'index.html').read_text(encoding='utf-8')
for required in ['lang="en"', 'PT Deploy Dulu Pulang Nanti', 'seandyadryan2@gmail.com',
                 'com.deploydulupulangnanti.psxremoteplay']:
    assert required in policy, f'Privacy policy is missing {required}'
assert '<script' not in policy.lower(), 'Unexpected privacy-page script'

manifest = {'applicationId': 'com.deploydulupulangnanti.psxremoteplay',
            'versionName': '0.2.1-lan-experimental', 'locale': 'en-US',
            'screenshotSource': 'Actual Android emulator UI; no physical console or streamed gameplay',
            'desktopScope': 'Android landscape previews; native desktop and ChromeOS compatibility unverified',
            'assets': records}
(root / 'asset-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(f'PASS: {len(records)} descriptions/images; privacy identity and contact verified.')
