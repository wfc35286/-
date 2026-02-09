package com.aaabbbaaa.aaabbbaaa;

import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class fuckyou implements IXposedHookLoadPackage {

    private static final String GSA_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String TAG = "CircleToSearchHook";
    private static int contextualSearchConfigId = 0;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // 1. GSA 伪装：让 Google App 认为自己在 Pixel 或 Samsung 旗舰上
        if (lpparam.packageName.equals(GSA_PACKAGE)) {
            spoofDeviceProperties();
        }

        // 2. Android 框架层劫持：欺骗系统认为支持 Contextual Search
        if ("android".equals(lpparam.packageName)) {
            try {
                hookSystemConfig(lpparam);
                hookContextualSearchService(lpparam);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Hook SystemServer failed - " + t.getMessage());
            }
        }

        // 3. SystemUI 劫持：拦截长按手势
        if ("com.android.systemui".equals(lpparam.packageName)) {
            try {
                hookSystemUI(lpparam);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Hook SystemUI failed - " + t.getMessage());
            }
        }
    }

    /**
     * 核心逻辑：Hook SystemUI 的长按事件
     * 采用了 MethodReplacement 模式，完全规避了对私有字段的依赖
     */
    private void hookSystemUI(LoadPackageParam lpparam) {
        // 目标类名：根据 smali 源码 [cite: 1]
        final String TARGET_CLASS = "com.oplus.systemui.navigationbar.ocrscreen.OplusOcrScreenBusiness";
        
        Class<?> ocrClass = XposedHelpers.findClassIfExists(TARGET_CLASS, lpparam.classLoader);
        if (ocrClass == null) {
            XposedBridge.log(TAG + ": Target class not found. Update might have changed the class name.");
            return;
        }

        // 使用 replaceHookedMethod 替换原方法逻辑
        // 目标方法：onLongPressed [cite: 31]
        XposedHelpers.findAndHookMethod(ocrClass, "onLongPressed", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                Context context = null;

                // --- 步骤 1：安全地获取 Context ---
                // 策略 A：尝试调用 public getContext() 方法 
                // 这种方式比直接反射字段更安全，因为 public 方法名在混淆中通常更稳定
                try {
                    Method getContextMethod = param.thisObject.getClass().getMethod("getContext");
                    context = (Context) getContextMethod.invoke(param.thisObject);
                } catch (Throwable t) {
                    // 策略 B：如果方法获取失败，回退到字段反射 
                    try {
                        context = (Context) XposedHelpers.getObjectField(param.thisObject, "context");
                    } catch (Throwable t2) {
                        // 彻底失败，无法获取 Context，但仍可尝试唤起搜索
                    }
                }

                // --- 步骤 2：执行震动 ---
                if (context != null) {
                    performHapticFeedback(context);
                }

                // --- 步骤 3：唤起 Contextual Search ---
                // 这里不再依赖 SystemUI 的任何内部逻辑，完全独立
                triggerContextualSearch();

                // --- 关键：返回 null ---
                // 这意味着原有的 onLongPressed 逻辑（包括设置 boolean 标志位、调用小布助手等）
                // 统统不会执行。因此我们不需要去处理 isCalledLongPress  等混淆字段。
                return null;
            }
        });
    }

    private void performHapticFeedback(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                // 使用 EFFECT_TICK 模拟 Pixel 的手感
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            }
        } catch (Throwable ignored) {
            // 震动失败不应该影响功能
        }
    }

    private void triggerContextualSearch() {
        try {
            // 通过 ServiceManager 获取服务，绕过 SystemUI 的封装
            IBinder b = (IBinder) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.os.ServiceManager", null), 
                    "getService", 
                    "contextual_search");
            
            if (b == null) return;

            Object service = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.contextualsearch.IContextualSearchManager$Stub", null), 
                    "asInterface", 
                    b);
            
            // 调用 startContextualSearch，参数 2 代表 ENTRYPOINT_NAV_HANDLE
            XposedHelpers.callMethod(service, "startContextualSearch", 2);
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Failed to trigger service: " + e.getMessage());
        }
    }

    // --- 以下是 Framework 层的欺骗逻辑 (保持相对稳定) ---

    private void hookSystemConfig(LoadPackageParam lpparam) {
        try {
            // 获取 config_defaultContextualSearchPackageName 的资源 ID
            Class<?> rString = XposedHelpers.findClass("com.android.internal.R$string", lpparam.classLoader);
            contextualSearchConfigId = XposedHelpers.getStaticIntField(rString, "config_defaultContextualSearchPackageName");

            // 欺骗 SystemServer：由于我们修改不了 framework-res.apk，
            // 我们Hook deviceHasConfigString 让它认为系统配置了 GSA 作为搜索包
            XposedHelpers.findAndHookMethod("com.android.server.SystemServer", lpparam.classLoader, 
                "deviceHasConfigString", Context.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if ((int)param.args[1] == contextualSearchConfigId) {
                        param.setResult(true); 
                    }
                }
            });
        } catch (Throwable t) {
            // 资源 ID 可能在不同安卓版本中变动，但这部分通常不需要经常维护
        }
    }

    private void hookContextualSearchService(LoadPackageParam lpparam) {
        String serviceClass = "com.android.server.contextualsearch.ContextualSearchManagerService";
        
        // 1. 强制返回 GSA 包名
        try {
            XposedHelpers.findAndHookMethod(serviceClass, lpparam.classLoader, 
                "getContextualSearchPackageName", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return GSA_PACKAGE;
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook getContextualSearchPackageName failed");
        }

        // 2. 绕过权限检查 (针对 Android 15/16)
        try {
            XposedHelpers.findAndHookMethod(serviceClass, lpparam.classLoader, 
                "enforcePermission", String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    // 直接返回 null 表示权限检查通过 (void 方法)
                    return null;
                }
            });
        } catch (Throwable t) {
            // 方法名可能混淆，但这属于 System Server，混淆概率低
        }
    }

    private void spoofDeviceProperties() {
        // 伪装成 Galaxy S24 Ultra，这是目前支持 Circle to Search 最好的机型之一
        XposedHelpers.setStaticObjectField(Build.class, "MANUFACTURER", "samsung");
        XposedHelpers.setStaticObjectField(Build.class, "BRAND", "samsung");
        XposedHelpers.setStaticObjectField(Build.class, "MODEL", "SM-S928B");
        XposedHelpers.setStaticObjectField(Build.class, "PRODUCT", "e3s");
        XposedHelpers.setStaticObjectField(Build.class, "DEVICE", "e3s");
    }
}
