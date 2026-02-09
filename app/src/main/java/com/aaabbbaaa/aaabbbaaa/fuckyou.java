package com.aaabbbaaa.aaabbbaaa;

import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class fuckyou implements IXposedHookLoadPackage {
    // 移除了 TAG，因为不再需要打印日志
    private static final String GSA = "com.google.android.googlequicksearchbox";
    private static int contextualSearchConfigId = 0;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // 1. 全局机型伪装：让 GSA 觉得自己跑在 S25 Ultra 上
        spoofToSamsung();

        // 2. 劫持 System Server：爆破安卓 16 的系统级封印
        if ("android".equals(lpparam.packageName)) {
            try {
                hookSystemConfig(lpparam);
                hookContextualSearchService(lpparam);
            } catch (Throwable t) {
                // 静默处理，不输出错误
            }
        }

        // 3. 劫持 SystemUI：拦截 ColorOS 原生识屏手势，注入震动并唤起一圈即搜
        if ("com.android.systemui".equals(lpparam.packageName)) {
            try {
                hookColorOSBusiness(lpparam);
            } catch (Throwable t) {
                // 静默处理，不输出错误
            }
        }
    }

    private void hookSystemConfig(LoadPackageParam lpparam) {
        // 伪装系统配置，让系统认为支持 Contextual Search
        Class<?> rString = XposedHelpers.findClass("com.android.internal.R$string", lpparam.classLoader);
        contextualSearchConfigId = XposedHelpers.getStaticIntField(rString, "config_defaultContextualSearchPackageName");

        XposedHelpers.findAndHookMethod("com.android.server.SystemServer", lpparam.classLoader, 
            "deviceHasConfigString", Context.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if ((int)param.args[1] == contextualSearchConfigId) {
                    param.setResult(true); 
                }
            }
        });
    }

    private void hookContextualSearchService(LoadPackageParam lpparam) {
        Class<?> serviceClass = XposedHelpers.findClass("com.android.server.contextualsearch.ContextualSearchManagerService", lpparam.classLoader);
        
        // 绕过安卓 16 的签名和系统权限校验
        XposedHelpers.findAndHookMethod(serviceClass, "enforcePermission", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(null);
            }
        });

        // 强制把搜索包名指向 Google App
        XposedHelpers.findAndHookMethod(serviceClass, "getContextualSearchPackageName", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(GSA);
            }
        });
    }

    private void hookColorOSBusiness(LoadPackageParam lpparam) {
        Class<?> ocrClass = XposedHelpers.findClass("com.oplus.systemui.navigationbar.ocrscreen.OplusOcrScreenBusiness", lpparam.classLoader);

        XposedHelpers.findAndHookMethod(ocrClass, "onLongPressed", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // 移除了 Log.d

                Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "context");
                
                // --- 1. 注入震动反馈 (已修正编译错误) ---
                performSexyVibration(context);
                
                // --- 2. 状态重置：解决三指截屏冲突的关键 ---
                XposedHelpers.setBooleanField(param.thisObject, "isCalledLongPress", false);
                XposedHelpers.setBooleanField(param.thisObject, "isCancelLongPress", true);

                // --- 3. 强行启动一圈即搜 ---
                triggerSystemOmni();

                // --- 4. 爆破原有逻辑：禁止小布弹出和原厂震动 ---
                param.setResult(null); 
            }
        });
    }

    private void performSexyVibration(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                // 这里是修正点：不再重复嵌套 createPredefined
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                // 移除了震动成功日志
            }
        } catch (Exception e) {
            // 静默处理震动失败
        }
    }

    private void triggerSystemOmni() {
        try {
            IBinder b = (IBinder) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.os.ServiceManager", null), "getService", "contextual_search");
            Object service = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.contextualsearch.IContextualSearchManager$Stub", null), "asInterface", b);
            
            // 2 代表 Navigation Bar 触发路径
            XposedHelpers.callMethod(service, "startContextualSearch", 2);
        } catch (Throwable e) {
            // 静默处理 Binder 调用失败
        }
    }

    private void spoofToSamsung() {
        XposedHelpers.setStaticObjectField(Build.class, "MANUFACTURER", "samsung");
        XposedHelpers.setStaticObjectField(Build.class, "BRAND", "samsung");
        XposedHelpers.setStaticObjectField(Build.class, "MODEL", "SM-S938B");
        XposedHelpers.setStaticObjectField(Build.class, "PRODUCT", "sun3");
        XposedHelpers.setStaticObjectField(Build.class, "DEVICE", "sun3");
    }
}
