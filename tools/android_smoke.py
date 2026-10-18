"""Exercise the signed APK on a clean Android 9 emulator against the real feed."""
import pathlib,re,subprocess,time,xml.etree.ElementTree as ET
out=pathlib.Path('smoke-output');out.mkdir(exist_ok=True)
def adb(*args, binary=False):
    return subprocess.check_output(['adb',*args],text=not binary)
def hierarchy(name):
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    adb('pull','/sdcard/window.xml',str(out/name))
    return ET.parse(out/name).getroot()
adb('install','-r','app-release.apk')
adb('logcat','-c')
adb('shell','am','start','-W','-n','it.affareradar.worker/.MainActivity')
home=hierarchy('home.xml')
nodes=list(home.iter('node'))
assert any(n.get('text')=='AFFARERADAR' for n in nodes),'Home did not open'
target=next(n for n in nodes if n.get('text')=='Apri il radar')
b=list(map(int,re.findall(r'\d+',target.get('bounds'))))
adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
deadline=time.monotonic()+100
while True:
    radar=hierarchy('radar.xml');texts=[n.get('text','') for n in radar.iter('node')]
    if 'Stato della ricerca' in texts and any('CANDIDATO' in t or 'FOTO ESAMINATE' in t or 'OPPORTUNITÀ DA VERIFICARE' in t or 'Nessun elemento' in t for t in texts):break
    if time.monotonic()>deadline:raise AssertionError('Radar did not finish loading: '+repr(texts))
    time.sleep(3)
assert any('opportunità' in t and 'elementi' in t for t in texts),'Summary missing'
with (out/'radar.png').open('wb') as f:f.write(adb('exec-out','screencap','-p',binary=True))
crashes=adb('logcat','-d','-s','AndroidRuntime:E')
(out/'crashes.txt').write_text(crashes)
assert 'FATAL EXCEPTION' not in crashes,crashes
assert 'it.affareradar.worker' in adb('shell','dumpsys','activity','activities')
print('Signed APK: installation, cold launch, navigation, live feed and crash check passed')
