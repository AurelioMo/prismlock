"""Exercise the real Hook against a small simulated Samsung/Xposed boundary.

Requires Java 17 and ANDROID_HOME. This is a host regression check, not a device test.
No application or fingerprint state on a phone is accessed.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    'android/net/Uri.java': '''package android.net;
public class Uri { public static Uri parse(String s) { return null; } }''',
    'android/os/SystemClock.java': '''package android.os;
public class SystemClock { public static long elapsedRealtime() { return 0; } }''',
    'android/app/AndroidAppHelper.java': '''package android.app;
public class AndroidAppHelper { public static Application currentApplication() { return null; } }''',
    'de/robv/android/xposed/IXposedHookLoadPackage.java': '''package de.robv.android.xposed;
public interface IXposedHookLoadPackage { void handleLoadPackage(
de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam p); }''',
    'de/robv/android/xposed/callbacks/XC_LoadPackage.java': '''package de.robv.android.xposed.callbacks;
public class XC_LoadPackage { public static class LoadPackageParam {
public String packageName; public ClassLoader classLoader; } }''',
    'de/robv/android/xposed/XC_MethodHook.java': '''package de.robv.android.xposed;
public class XC_MethodHook {
 protected void beforeHookedMethod(MethodHookParam p) throws Throwable { }
 public static class MethodHookParam {
  public Object thisObject; public Object[] args; public boolean returned; public Object result;
  public void setResult(Object r) { result=r; returned=true; }
 }
}''',
    'de/robv/android/xposed/XposedBridge.java': '''package de.robv.android.xposed;
import java.lang.reflect.*; import java.util.*;
public class XposedBridge {
 public static final Map<Method,XC_MethodHook> hooks=new HashMap<>();
 public static final List<String> logs=new ArrayList<>();
 public static void log(String s) { logs.add(s); }
 public static Set<Method> hookAllMethods(Class<?> c,String name,XC_MethodHook h) {
  Set<Method> found=new HashSet<>();
  for(Method m:c.getDeclaredMethods()) if(m.getName().equals(name)) { hooks.put(m,h); found.add(m); }
  return found;
 }
 public static Object invoke(Object target,String name,Object... args) throws Throwable {
  for(Method m:target.getClass().getMethods()) {
   if(!m.getName().equals(name) || m.getParameterCount()!=args.length) continue;
   boolean match=true;
   for(int i=0;i<args.length;i++) if(args[i]!=null && !m.getParameterTypes()[i].isInstance(args[i])) match=false;
   if(!match) continue;
   XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam(); p.thisObject=target; p.args=args;
   if(hooks.containsKey(m)) hooks.get(m).beforeHookedMethod(p);
   if(p.returned) return p.result;
   try { return m.invoke(target,p.args); } catch(InvocationTargetException e) { throw e.getCause(); }
  }
  throw new NoSuchMethodException(name);
 }
}''',
    'de/robv/android/xposed/XposedHelpers.java': '''package de.robv.android.xposed;
import java.lang.reflect.*; import java.util.*;
public class XposedHelpers {
 static final Map<Object,Map<String,Object>> fields=new IdentityHashMap<>();
 public static Class<?> findClass(String n,ClassLoader l) throws Exception { return Class.forName(n,true,l); }
 public static void findAndHookMethod(String n,ClassLoader l,String m,Object... a) throws Exception {
  findAndHookMethod(findClass(n,l),m,a);
 }
 public static void findAndHookMethod(Class<?> c,String n,Object... a) throws Exception {
  Class<?>[] types=new Class<?>[a.length-1];
  for(int i=0;i<types.length;i++) types[i]=(Class<?>)a[i];
  XposedBridge.hooks.put(c.getDeclaredMethod(n,types),(XC_MethodHook)a[a.length-1]);
 }
 public static Object getStaticObjectField(Class<?> c,String n) throws Exception { return c.getField(n).get(null); }
 public static Object getObjectField(Object o,String n) {
  try { return o.getClass().getField(n).get(o); } catch(Exception e) { throw new RuntimeException(e); }
 }
 public static void setObjectField(Object o,String n,Object v) {
  try { o.getClass().getField(n).set(o,v); } catch(Exception e) { throw new RuntimeException(e); }
 }
 public static Object getAdditionalInstanceField(Object o,String n) { return fields.getOrDefault(o,Map.of()).get(n); }
 public static Object setAdditionalInstanceField(Object o,String n,Object v) {
  return fields.computeIfAbsent(o,k->new HashMap<>()).put(n,v);
 }
 public static Object callMethod(Object o,String n,Object... a) throws Throwable { return XposedBridge.invoke(o,n,a); }
}''',
    'com/airbnb/lottie/LottieProperty.java': '''package com.airbnb.lottie;
public class LottieProperty { public static final Object COLOR_FILTER=new Object(); }''',
    'com/airbnb/lottie/LottieDrawable.java': '''package com.airbnb.lottie;
public class LottieDrawable {
 public Object callback;
 public void addValueCallback(Object key,Object property,Object value) { callback=value; }
}''',
    'com/airbnb/lottie/LottieAnimationView.java': '''package com.airbnb.lottie;
import java.io.*; import java.util.*;
public class LottieAnimationView {
 public final LottieDrawable lottieDrawable=new LottieDrawable();
 public final Map<String,String> cache=new HashMap<>();
 public String shown, lastKey; public boolean fail;
 public void setAnimation(String name) { shown=cache.computeIfAbsent("asset_"+name,k->"stock"); }
 public void setAnimation(InputStream input,String key) throws Exception {
  if(fail) throw new IOException("simulated unavailable loader");
  lastKey=key; String data=new String(input.readAllBytes()); shown=cache.computeIfAbsent(key,k->data);
 }
}''',
    'com/samsung/android/biometrics/app/setting/fingerprint/UdfpsKeyguardSensorWindow.java': '''
package com.samsung.android.biometrics.app.setting.fingerprint;
public class UdfpsKeyguardSensorWindow {
 public String mCurrentAnimationType="same_theme"; public int loads;
 public void setAnimationTypeIfNeeded() {
  if(mCurrentAnimationType==null) { loads++; mCurrentAnimationType="same_theme"; }
 }
}''',
    'io/github/aureliomo/prismlock/BuildConfig.java': '''package io.github.aureliomo.prismlock;
public class BuildConfig {
 public static final String APPLICATION_ID="io.github.aureliomo.prismlock";
 public static final int VERSION_CODE=101;
}''',
    'io/github/aureliomo/prismlock/IconLoaderCheck.java': '''package io.github.aureliomo.prismlock;
import com.airbnb.lottie.*;
import com.samsung.android.biometrics.app.setting.fingerprint.UdfpsKeyguardSensorWindow;
import de.robv.android.xposed.*; import java.lang.reflect.*;
public class IconLoaderCheck {
 static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
 static byte[] bytes(String s) { return s.getBytes(); }
 static void snapshot(long version,boolean enabled,String imported) throws Exception {
  RuntimeConfig c=RuntimeConfig.create(version,50,26,enabled,imported==null?"":"json","",
   imported==null?null:bytes(imported),null,bytes("dark-"+version),bytes("light-"+version),bytes("aod-"+version),bytes("effect"));
  Field f=Hook.class.getDeclaredField("config"); f.setAccessible(true); f.set(null,c);
 }
 public static void main(String[] args) throws Throwable {
  Method install=Hook.class.getDeclaredMethod("installIconLoader",ClassLoader.class);
  install.setAccessible(true); install.invoke(null,IconLoaderCheck.class.getClassLoader());
  check(XposedBridge.logs.isEmpty(),"Hook installation failed: "+XposedBridge.logs);
  LottieAnimationView view=new LottieAnimationView();
  String dark="ic_fingerprint_dark_theme.json";
  view.setAnimation(dark); check(view.shown.equals("stock"),"stock cache seed");
  snapshot(1,true,null); XposedBridge.invoke(view,"setAnimation",dark);
  check(view.shown.equals("dark-1"),"stock cache must not bypass custom data"); String first=view.lastKey;
  snapshot(2,true,null); XposedBridge.invoke(view,"setAnimation",dark);
  check(view.shown.equals("dark-2") && !first.equals(view.lastKey),"selection must replace cached previous icon");
  XposedBridge.invoke(view,"setAnimation","ic_fingerprint_light_theme_non_alpha.json");
  check(view.shown.equals("light-2"),"light variant");
  XposedBridge.invoke(view,"setAnimation","ic_fingerprint_aod.json");
  check(view.shown.equals("aod-2"),"AOD variant");
  Object filter=new Object(), other=new Object();
  XposedBridge.invoke(view.lottieDrawable,"addValueCallback",other,LottieProperty.COLOR_FILTER,filter);
  check(view.lottieDrawable.callback==null,"Samsung tint must not recolor custom artwork");
  XposedBridge.invoke(view.lottieDrawable,"addValueCallback",other,other,filter);
  check(view.lottieDrawable.callback==filter,"non-color callbacks preserved");
  LottieAnimationView effects=new LottieAnimationView();
  XposedBridge.invoke(effects,"setAnimation","indisplay_fingerprint_touch_effect_ripple.json");
  XposedBridge.invoke(effects.lottieDrawable,"addValueCallback",other,LottieProperty.COLOR_FILTER,filter);
  check(effects.shown.equals("stock") && effects.lottieDrawable.callback==filter,"touch effects untouched");
  snapshot(3,true,"imported"); XposedBridge.invoke(view,"setAnimation",dark);
  check(view.shown.equals("imported"),"imports retain precedence");
  snapshot(4,false,null); XposedBridge.invoke(view,"setAnimation",dark);
  XposedBridge.invoke(view.lottieDrawable,"addValueCallback",other,LottieProperty.COLOR_FILTER,filter);
  check(view.shown.equals("stock") && view.lottieDrawable.callback==filter,"disable restores stock and tint");
  snapshot(5,true,null); view.fail=true; XposedBridge.invoke(view,"setAnimation",dark);
  check(view.shown.equals("stock") && !XposedBridge.logs.isEmpty(),"failed optional loader falls back");
  UdfpsKeyguardSensorWindow window=new UdfpsKeyguardSensorWindow();
  XposedBridge.invoke(window,"setAnimationTypeIfNeeded");
  check(window.loads==1,"initial keyguard load");
  XposedBridge.invoke(window,"setAnimationTypeIfNeeded");
  check(window.loads==1,"unchanged configuration does not reload");
  snapshot(6,true,null); XposedBridge.invoke(window,"setAnimationTypeIfNeeded");
  check(window.loads==2,"new selection reloads even with unchanged theme");
  System.out.println("PASS: real icon hooks; stale cache, variants, imports, tint scope, stock restoration, optional-loader failure and keyguard refresh (simulated host boundary)");
 }
}''',
}


def main():
    sdk = Path(os.environ['ANDROID_HOME'])
    android = sdk / 'platforms/android-36/android.jar'
    with tempfile.TemporaryDirectory(prefix='prismlock-icon-check-') as directory:
        temporary = Path(directory)
        fixtures = []
        for name, source in SOURCES.items():
            path = temporary / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
            fixtures.append(str(path))
        main = ROOT / 'app/src/main/java/io/github/aureliomo/prismlock'
        production = [str(main / (name + '.java')) for name in
                      ('Hook', 'RuntimeConfig', 'Icons', 'Catalog', 'CatalogScale', 'AssetPolicy', 'IconCache')]
        classes = temporary / 'classes'
        subprocess.run(['javac', '-cp', str(android), '-d', str(classes), *fixtures, *production], check=True)
        subprocess.run(['java', '-cp', os.pathsep.join((str(classes), str(android))),
                        'io.github.aureliomo.prismlock.IconLoaderCheck'], check=True)


if __name__ == '__main__':
    main()
