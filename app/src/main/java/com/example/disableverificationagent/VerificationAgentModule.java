/*
 * SPDX-License-Identifier: GPL-2.0-only
 * Derived from LSPosed/CorePatch, revision ddf87bb5fb2bb2eb962992103abf0b225952d738.
 * Modified 2026-11-09: extract only verification-agent hooks into a headless API101 module.
 */
package com.example.disableverificationagent;

import android.os.Build;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class VerificationAgentModule extends XposedModule {
    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        try {
            ClassLoader loader = param.getClassLoader();
            if (Build.VERSION.SDK_INT >= 34) {
                Class<?> sessionClass = loader.loadClass("com.android.server.pm.VerifyingSession");
                Field flags = sessionClass.getDeclaredField("mInstallFlags");
                flags.setAccessible(true);
                Method start = findMethod(sessionClass, "handleStartVerify", void.class);
                Method adb = findMethod(sessionClass, "isAdbVerificationEnabled", boolean.class);
                hook(start).intercept(chain -> {
                    Object session = chain.getThisObject();
                    flags.setInt(session, flags.getInt(session) | 0x00080000);
                    return chain.proceed();
                });
                hook(adb).intercept(chain -> false);
            } else {
                String className = Build.VERSION.SDK_INT == 33
                        ? "com.android.server.pm.VerificationParams"
                        : "com.android.server.pm.PackageManagerService";
                Method enabled = findMethod(loader.loadClass(className), "isVerificationEnabled", boolean.class);
                hook(enabled).intercept(chain -> false);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?> returnType)
            throws NoSuchMethodException {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getReturnType() == returnType) {
                return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }
}
