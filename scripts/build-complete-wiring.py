"""Build Scout's external wiring poster from the authoritative Rev C board XML.

The unmodified CAM top artwork is embedded as vectors; all wiring endpoints are
calculated from the native footprint, placement, and rotation, then checked
against the native net list. No external images, fonts, or links are required.
"""
from pathlib import Path
import copy
import math
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BOARD = ROOT / '05-Factory-CarrierC-v10/Fusion-Source/C3-Carrier-RevC.brd'
if not BOARD.exists():
    BOARD = ROOT / 'artifacts/gpio-audit/C3-Carrier-RevC.brd'
CAM = ROOT / 'docs/builder-guide/assets/board/board-top.svg'
DEST = ROOT / 'docs/builder-guide/assets/wiring/scout-complete-wiring.svg'
SCALE, BX, BY = 12, 700, 350
NS = 'http://www.w3.org/2000/svg'
ET.register_namespace('', NS)
ET.register_namespace('xlink', 'http://www.w3.org/1999/xlink')
native = ET.parse(BOARD).getroot()
els = {e.get('name'): e for e in native.findall('.//board/elements/element')}
packages = {p.get('name'): p for p in native.findall('.//board/libraries/library/packages/package')}
nets = {(c.get('element'), c.get('pad')): s.get('name')
        for s in native.findall('.//board/signals/signal') for c in s.findall('contactref')}


def pad(element, name):
    e = els[element]
    p = packages[e.get('package')].find(f"pad[@name='{name}']")
    assert p is not None
    rotation = e.get('rot', 'R0')
    assert 'M' not in rotation, 'Poster assumes component side (unmirrored)'
    angle = math.radians(float(rotation.replace('R', '')))
    x, y = float(p.get('x')), float(p.get('y'))
    x, y = (float(e.get('x')) + x * math.cos(angle) - y * math.sin(angle),
            float(e.get('y')) + x * math.sin(angle) + y * math.cos(angle))
    return round(BX + x * SCALE, 2), round(BY + (82 - y) * SCALE, 2)


svg = ET.Element(f'{{{NS}}}svg', {
    'width': '1800', 'height': '1700', 'viewBox': '0 0 1800 1700',
    'role': 'img', 'aria-labelledby': 'wiring-title wiring-desc',
})


def el(tag, attrs=None, content=None, parent=svg, **extra):
    at = {k.rstrip('_').replace('_', '-'): str(v) for k, v in {**(attrs or {}), **extra}.items()}
    item = ET.SubElement(parent, f'{{{NS}}}{tag}', at)
    if content is not None:
        item.text = str(content)
    return item


el('title', {'id': 'wiring-title'}, 'Scout Carrier C v10 — complete external wiring')
el('desc', {'id': 'wiring-desc'},
   'Component-side view with the ESP32-C3 SuperMini USB socket pointing up. '
   'The actual Rev C CAM top artwork is shown. One 18650 holder connects to J1: '
   'positive to lower pad 1 BAT_PLUS, negative to upper pad 2 BAT_MINUS. '
   'Left motor connects to J2 pads 1 and 2; right motor to J3 pads 1 and 2. '
   'A two-wire SPST RUN switch connects J4 lower pad 1 FUSED_BAT to upper pad 2 VMOTOR. '
   'The OLED connects at U1 solder headers: VCC to right third pad 3V3, GND to right '
   'second pad protected GND, SDA to left first pad GPIO5, SCL to left third pad GPIO7. '
   'Battery negative and protected GND are different nets. Follow the labels on your '
   'OLED because its pin order can vary. RUN must be open before ESP USB is connected; '
   'unplug ESP USB before closing RUN. Leave RUN open while charging at J5. '
   'Motor outputs reverse polarity; verify wheel direction with the wheels raised.')
style = '''
text { font-family: Arial, Helvetica, sans-serif; fill: #18283a; }
.title { font-size: 50px; font-weight: 750; letter-spacing: -.8px; }
.sub { font-size: 27px; fill: #506277; }
.heading { font-size: 30px; font-weight: 700; }
.label { font-size: 25px; font-weight: 650; }
.body { font-size: 24px; }
.small { font-size: 21px; }
.tiny { font-size: 18px; }
.wire { fill: none; stroke-width: 8; stroke-linejoin: round; stroke-linecap: round; }
.wire-halo { fill: none; stroke: #fff; stroke-width: 14; stroke-linejoin: round; stroke-linecap: round; }
.device { fill: #fff; stroke: #d0dce8; stroke-width: 2; }
.leader { fill: none; stroke: #74879a; stroke-width: 2.5; stroke-dasharray: 7 6; }
'''
el('style', content=style)
el('rect', width=1800, height=1700, fill='#f5f8fc')
el('text', {'x': 70, 'y': 82, 'class': 'title'}, 'Wire the complete Scout')
el('text', {'x': 73, 'y': 126, 'class': 'sub'}, 'Carrier C v10 · Rev C native layout · component side · ESP USB points up')
el('rect', x=70, y=155, width=1660, height=72, rx=14, fill='#fff1d6', stroke='#e1b85c')
el('text', {'x': 95, 'y': 200, 'class': 'label'},
   'RUN OPEN before ESP USB or charging. Unplug ESP USB before closing RUN.')


def text(x, y, value, cls='body', fill=None, anchor=None, halo=False):
    a = {'x': x, 'y': y, 'class': cls}
    if fill:
        a['fill'] = fill
        a['style'] = f'fill:{fill}'
    if anchor:
        a['text-anchor'] = anchor
    if halo:
        a.update({'paint-order': 'stroke', 'stroke': '#fff', 'stroke-width': '5',
                  'stroke-linejoin': 'round'})
    return el('text', a, value)


def card(x, y, w, h, title, subtitle=None):
    el('rect', x=x, y=y, width=w, height=h, rx=18, class_='device')
    text(x+25, y+43, title, 'heading')
    if subtitle:
        text(x+25, y+77, subtitle, 'small', '#506277')


def line(points, color, width=3, **extra):
    return el('polyline', points=' '.join(f'{x},{y}' for x, y in points),
              fill='none', stroke=color, stroke_width=width, **extra)


def terminal(x, y, color, radius=10, parent=svg):
    el('circle', cx=x, cy=y, r=radius, fill='#fff', stroke=color,
       stroke_width=5, parent=parent)


# This is the exact CAM vector, positioned so native top-view coordinates map
# to (700 + 12*x, 350 + 12*(82-y)). Its source is preserved unchanged.
cam = copy.deepcopy(ET.parse(CAM).getroot())
cam.attrib.update({'x': str(BX-1.127*SCALE), 'y': str(BY-1.127*SCALE),
                   'width': str(34.254*SCALE), 'height': str(84.254*SCALE),
                   'aria-hidden': 'true', 'id': 'native-cam-top'})
svg.append(cam)
el('path', d='M712 350 H1072 L1084 362 V1322 L1072 1334 H712 L700 1322 V362 Z',
   fill='none', stroke='#345269', stroke_width=3)
text(890, 270, 'CARRIER TOP · ESP USB ↑', 'label', anchor='middle')

# A simple module silhouette sits on the exact native header footprints. It is
# explanatory artwork, not a populated MCAD rendering of another revision.
mx, my = BX+8*SCALE, BY+(82-79.51)*SCALE
el('rect', x=mx, y=my, width=18*SCALE, height=22.52*SCALE, rx=12,
   fill='#224767', stroke='#153a56', stroke_width=3)
el('rect', x=mx+67, y=my-12, width=82, height=44, rx=8, fill='#c0cbd5', stroke='#6b7e8d', stroke_width=3)
el('rect', x=mx+77, y=my-5, width=62, height=22, rx=7, fill='#243746')
el('rect', x=mx+57, y=my+83, width=102, height=111, rx=6, fill='#bbc7d3', stroke='#8093a6', stroke_width=2)
text(mx+108, my+126, 'ESP32-C3', 'tiny', '#233b4e', 'middle')
text(mx+108, my+153, 'U1', 'small', '#233b4e', 'middle')
text(mx+108, my+230, 'SuperMini', 'tiny', '#fff', 'middle')
for side, names in [('left', ['IO5','IO6','IO7','IO8','IO9','IO10','IO20','IO21']),
                    ('right', ['5V','GND','3V3','IO4','IO3','IO2','IO1','IO0'])]:
    for name in names:
        px, py = pad('U1', name)
        terminal(px, py, '#b6a777', 9)
        label = name.removeprefix('IO')
        text(px+(15 if side=='left' else -15), py+6, label, 'tiny', '#fff',
             'start' if side=='left' else 'end')

# Device illustrations and terminals are intentionally outside the PCB.
card(1325, 300, 405, 310, 'I²C OLED')
el('rect', x=1438, y=408, width=250, height=148, rx=9, fill='#233a63')
el('rect', x=1453, y=421, width=220, height=117, rx=4, fill='#0a182b')
text(1563, 466, 'SCOUT', 'heading', '#7ce1ff', 'middle')
text(1563, 502, 'READY', 'small', '#7ce1ff', 'middle')
oled = {'VCC': (1345, 375), 'GND': (1345, 425), 'SDA': (1345, 475), 'SCL': (1345, 525)}
for label, (x, y) in oled.items():
    text(x+24, y+8, label, 'label')
text(1350, 584, 'Follow pin labels; order may vary.', 'tiny', '#506277')

card(70, 646, 445, 420, 'ONE 18650 cell', 'Illustrative holder · leads → J1')
el('rect', x=104, y=748, width=204, height=265, rx=16, fill='#263546')
el('rect', x=133, y=774, width=145, height=204, rx=34, fill='#b7d2a0', stroke='#7c9d6e', stroke_width=3)
text(206, 811, '−', 'heading', '#31482d', 'middle')
text(206, 945, '+', 'heading', '#31482d', 'middle')
text(206, 877, '18650', 'label', '#31482d', 'middle')
text(334, 767, '−', 'heading', '#614534')
text(334, 966, '+', 'heading', '#bc2b35')
text(105, 1040, 'Check the holder’s actual polarity.', 'small', '#506277')

card(1300, 704, 430, 280, 'SPST RUN switch', 'Two wires · no polarity')
el('rect', x=1450, y=815, width=150, height=93, rx=13, fill='#293b4f')
el('rect', x=1477, y=825, width=94, height=72, rx=9, fill='#647a91')
text(1524, 870, 'OPEN', 'small', '#fff', 'middle')
text(1325, 954, 'Close only after ESP USB is unplugged.', 'tiny', '#506277')

card(70, 1160, 490, 214, 'LEFT motor', 'Robot’s left, viewed from behind')
card(1250, 1160, 480, 214, 'RIGHT motor', 'Robot’s right, viewed from behind')
for x in (150, 1440):
    el('rect', x=x, y=1261, width=172, height=70, rx=15, fill='#edc853', stroke='#9f7a16', stroke_width=3)
    el('rect', x=x+32, y=1249, width=108, height=25, rx=8, fill='#b3bcc6')
    el('rect', x=x+169, y=1283, width=41, height=20, rx=3, fill='#aeb8c3')

# Wires are ordered so their white halo gives unconnected crossings a gap.
# Every board-targeted endpoint carries machine-readable element/pad/net data.
connections = []


def wire(name, start, element, target_pad, expected_net, color, bends):
    actual_net = nets.get((element, target_pad))
    assert actual_net == expected_net, (element, target_pad, actual_net, expected_net)
    end = pad(element, target_pad)
    points = [start] + bends + [end]
    d = ' '.join(f'{x},{y}' for x, y in points)
    el('polyline', points=d, class_='wire-halo', aria_hidden='true')
    el('polyline', points=d, class_='wire', stroke=color, id=name,
       data_from=name.split('-to-')[0], data_to=f'{element}.{target_pad}',
       data_net=expected_net, data_endpoint=f'{end[0]},{end[1]}')
    terminal(*start, color)
    terminal(*end, color)
    connections.append((name, element, target_pad, expected_net, end))


BAT_PLUS, BAT_MINUS = '#bd2934', '#614534'
RUN_A, RUN_B = '#a6550e', '#dc8d1a'
SDA, SCL, VCC, GND = '#087e8b', '#ce7300', '#28834a', '#4c5865'
wire('holder-negative-to-J1', (515, 750), 'J1', '2', 'BAT_MINUS', BAT_MINUS,
     [(590,750), (590,1025)])
wire('holder-positive-to-J1', (515, 950), 'J1', '1', 'BAT_PLUS', BAT_PLUS,
     [(555,950), (555,1067)])
wire('run-contact-A-to-J4', (1300, 814), 'J4', '2', 'VMOTOR', RUN_B,
     [(1130,814), (1130,833)])
wire('run-contact-B-to-J4', (1300, 889), 'J4', '1', 'FUSED_BAT', RUN_A,
     [(1160,889), (1160,875)])
wire('left-motor-lead-1-to-J2', (560, 1260), 'J2', '1', 'MOTOR_A1', '#00828a',
     [(635,1260), (635,1238)])
wire('left-motor-lead-2-to-J2', (560, 1310), 'J2', '2', 'MOTOR_A2', '#3d6e99',
     [(605,1310), (605,1400), (793,1400)])
wire('right-motor-lead-1-to-J3', (1250, 1310), 'J3', '1', 'MOTOR_B1', '#6f46aa',
     [(1195,1310), (1195,1400), (991,1400)])
wire('right-motor-lead-2-to-J3', (1250, 1260), 'J3', '2', 'MOTOR_B2', '#ad477e',
     [(1155,1260), (1155,1238)])
wire('OLED-SCL-to-U1', oled['SCL'], 'U1', 'IO7', None, SCL,
     [(1250,525), (1250,290), (620,290), (620,469.28)])
wire('OLED-SDA-to-U1', oled['SDA'], 'U1', 'IO5', None, SDA,
     [(1220,475), (1220,320), (650,320), (650,408.32)])
wire('OLED-GND-to-U1', oled['GND'], 'U1', 'GND', 'GND', GND,
     [(1140,425), (1140,438.8)])
wire('OLED-VCC-to-U1', oled['VCC'], 'U1', '3V3', '3V3', VCC,
     [(1180,375), (1180,469.28)])

# Net labels stay alongside endpoints and do not imply a connection to nearby
# artwork. Only open terminal rings are electrical endpoints.
text(695, 968, 'J1 · CELL', 'label', halo=True)
text(772, 1033, '2  − BAT_MINUS', 'small', BAT_MINUS, halo=True)
text(772, 1075, '1  + BAT_PLUS', 'small', BAT_PLUS, halo=True)
text(698, 779, 'J4 · RUN', 'label', halo=True)
text(772, 824, '2  VMOTOR', 'small', RUN_B, halo=True)
text(772, 912, '1  FUSED_BAT', 'small', RUN_A, halo=True)
text(731, 1187, 'J2', 'label', halo=True)
text(1000, 1187, 'J3', 'label', halo=True)
for connector in ('J2','J3'):
    for number in ('1','2'):
        x, y = pad(connector, number)
        text(x+(18 if number=='2' else -20), y+42, number, 'label', anchor='middle', halo=True)
text(682, 390, '5 · SDA', 'small', SDA, halo=True)
text(662, 510, '7 · SCL', 'small', SCL, halo=True)
text(1037, 417, 'GND', 'small', GND, halo=True)
text(1037, 506, '3V3', 'small', VCC, halo=True)
text(1325, 656, 'Solder to U1’s exposed header joints.', 'small', '#506277')

# The charging connector location comes from its native placement as well.
j5 = els['J5']
j5x = BX + float(j5.get('x'))*SCALE
j5y = BY + (82-float(j5.get('y')))*SCALE
line([(j5x+11,j5y), (1180,j5y), (1180,1071), (1300,1071)], '#74879a', 2.5,
     stroke_dasharray='7 6')
text(1300, 1046, 'J5 · charging USB-C', 'label')
text(1300, 1080, 'Leave RUN open while charging.', 'small', '#506277')
text(1300, 1112, 'ESP USB = programming at the top.', 'small', '#506277')

el('rect', x=70, y=1450, width=1660, height=185, rx=16, fill='#e6eef7')
text(95, 1496, 'Before you power it', 'heading')
text(95, 1535, '• Battery − is BAT_MINUS. OLED GND is the protected GND at U1. Keep them separate.', 'body')
text(95, 1573, '• Motor outputs reverse: no fixed + / −. Set motor direction in firmware with the wheels raised.', 'body')
text(95, 1611, '• Colored lines are external wires; open rings are endpoints. Wire crossings do not connect.', 'body')
text(73, 1671, 'Exact pad locations and nets: C3-Carrier-RevC.brd / .sch · Layout illustration, not to scale · 2026-10-25', 'small', '#607187')

# Pad and placement invariants protect against an unnoticed mirror/rotation.
assert pad('J1','1') == (748,1067)
assert pad('J1','2') == (748,1025)
assert pad('J4','1') == (748,875)
assert pad('J4','2') == (748,833)
assert pad('J2','1') == (751,1238)
assert pad('J2','2') == (793,1238)
assert pad('J3','1') == (991,1238)
assert pad('J3','2') == (1033,1238)
assert len(connections) == 12
assert {c[2] for c in connections if c[1]=='U1'} == {'3V3','GND','IO5','IO7'}
DEST.parent.mkdir(parents=True, exist_ok=True)
ET.indent(svg, space='  ')
ET.ElementTree(svg).write(DEST, encoding='utf-8', xml_declaration=True)
ET.parse(DEST)
print(DEST)
for name, element, target_pad, net, endpoint in connections:
    print(f'{name}: {element}.{target_pad} {net or "external I2C"} @ {endpoint}')
