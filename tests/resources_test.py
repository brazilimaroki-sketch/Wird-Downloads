from pathlib import Path
import collections,json,re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent/'app/src/main'
files=list(root.rglob('*'));resources={}
for f in files:
    if not f.is_file() or 'res' not in f.parts:continue
    kind=f.parent.name.split('-')[0]
    if kind=='values':
        for node in ET.parse(f).getroot():
            if node.get('name'):resources.setdefault(node.get('type',node.tag),set()).add(node.get('name'))
    else:resources.setdefault(kind,set()).add(f.stem)
    resources.setdefault('id',set()).update(re.findall(r'@\+id/(\w+)',f.read_text(errors='ignore')))
for f in files:
    if not f.is_file() or f.suffix not in ['.java','.xml']:continue
    s=f.read_text(errors='ignore')
    if f.suffix=='.xml':ET.fromstring(s)
    refs=re.findall(r'(?<!android\.)R\.(\w+)\.(\w+)',s)+re.findall(r'(?<!android:)@(drawable|layout|string|color|font|xml|style)/([\w.]+)',s)
    for kind,name in refs:assert name in resources.get(kind,set()),(f,kind,name)
html=(root/'assets/index.html').read_text()
for asset in re.findall(r'<(?:script src|link rel="stylesheet" href)="([^"]+)"',html):
    if not asset.startswith('https:'):assert (root/'assets'/asset).is_file(),asset
ayahs=json.JSONDecoder().raw_decode(html.split('const AYAHS=',1)[1])[0];expected=collections.defaultdict(set)
for x in ayahs:
    if int(x[1])>0:expected[int(x[0])].add(int(x[1]))
books=list((root/'assets/tafsir').glob('*/*.json'));assert len(books)==570
for f in books:
    rows=json.loads(f.read_text());surah=int(f.stem)
    assert {int(x['ayah']) for x in rows}==expected[surah],f
    assert all(int(x['surah'])==surah and x.get('text','').strip() for x in rows),f
print('XML, native resource references, local scripts/styles, and all 570 tafsir files passed')
