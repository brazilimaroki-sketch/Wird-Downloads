from pathlib import Path
import tempfile,subprocess
source=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/wird/app/MainActivity.java').read_text()
def method(signature):
 start=source.index(signature);body=source.index('{',start);depth=1;end=body+1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
resume=method('protected void onResume()');pause=method('protected void onPause()');bridge=method('public void setReadingScreenOn(boolean enabled)')
code='''class Base {protected void onResume(){} protected void onPause(){}}
class View {boolean awake;void setKeepScreenOn(boolean v){awake=v;}}
class PrayerScheduler {static void rescheduleSaved(Object o){}}
class MainActivity extends Base {
 View webView=new View();boolean keepReadingScreenOn,activityResumed;
 void applyAppSystemBars(){} void refreshPrayerPermissionStatus(){} void runOnUiThread(Runnable r){r.run();}
'''+resume+pause+'''
 static class Bridge {MainActivity activity;Bridge(MainActivity a){activity=a;}MainActivity get(){return activity;}
'''+bridge+'''} }
class Test {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){MainActivity a=new MainActivity();MainActivity.Bridge b=new MainActivity.Bridge(a);
 b.setReadingScreenOn(true);check(!a.webView.awake);a.onResume();check(a.webView.awake);a.onPause();check(!a.webView.awake);b.setReadingScreenOn(true);check(!a.webView.awake);a.onResume();check(a.webView.awake);b.setReadingScreenOn(false);check(!a.webView.awake);a.onPause();a.onResume();check(!a.webView.awake);b.activity=null;b.setReadingScreenOn(true);System.out.println("Native screen flag, background release, resume and null Activity passed");}
}'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/'Test.java';p.write_text(code)
 subprocess.run(['java','com.sun.tools.javac.Main','-d',tmp,str(p)],check=True)
 subprocess.run(['java','-cp',tmp,'Test'],check=True)
