import android.os.Build;
import com.android.server.pm.VerifyingSession;
import com.example.disableverificationagent.VerificationAgentModule;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;

public final class ModuleTest {
    private final Map<Method, XposedInterface.Hooker> hooks = new LinkedHashMap<>();
    private int proceeds;

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(ModuleTest.class.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private void install(int sdk, ClassLoader loader) {
        Build.VERSION.SDK_INT = sdk;
        VerificationAgentModule module = new VerificationAgentModule();
        module.attachFramework(proxy(XposedInterface.class, (self, method, args) -> {
            if (method.getName().equals("hook")) {
                Method target = (Method) args[0];
                return proxy(XposedInterface.HookBuilder.class, (builder, action, values) -> {
                    if (action.getName().equals("intercept")) {
                        hooks.put(target, (XposedInterface.Hooker) values[0]);
                        return null;
                    }
                    throw new AssertionError("Unexpected builder call: " + action);
                });
            }
            throw new AssertionError("Unexpected framework call (including logs): " + method);
        }));
        module.onSystemServerStarting(() -> loader);
    }

    private Object call(String name, Object receiver, Object... args) throws Throwable {
        Map.Entry<Method, XposedInterface.Hooker> entry = hooks.entrySet().stream()
                .filter(e -> e.getKey().getName().equals(name)).findFirst().orElseThrow();
        Method target = entry.getKey();
        return entry.getValue().intercept(proxy(XposedInterface.Chain.class, (self, method, values) -> {
            switch (method.getName()) {
                case "getThisObject": return receiver;
                case "getExecutable": return target;
                case "getArgs": return List.of(args);
                case "getArg": return args[(int) values[0]];
                case "proceed":
                    proceeds++;
                    target.setAccessible(true);
                    try { return target.invoke(receiver, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                default: throw new AssertionError("Unexpected chain operation: " + method);
            }
        }));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Throwable {
        ClassLoader loader = ModuleTest.class.getClassLoader();
        switch (args[0]) {
            case "legacy":
            case "android13":
                int[] versions = args[0].equals("legacy") ? new int[]{28, 29, 30, 31, 32} : new int[]{33};
                for (int sdk : versions) {
                    ModuleTest t = new ModuleTest();
                    t.install(sdk, loader);
                    String owner = sdk == 33 ? "VerificationParams" : "PackageManagerService";
                    check(t.hooks.size() == 1, "Only the verification agent method should be hooked");
                    check(t.hooks.keySet().iterator().next().getDeclaringClass().getSimpleName().equals(owner), "Wrong OS branch");
                    Object receiver = Class.forName("com.android.server.pm." + owner).getConstructor().newInstance();
                    Object result = sdk == 33 ? t.call("isVerificationEnabled", receiver) : t.call("isVerificationEnabled", receiver, 0x40);
                    check(Boolean.FALSE.equals(result) && t.proceeds == 0, "Agent check must return false without proceeding");
                }
                break;
            case "modern":
                for (int sdk : new int[]{34, 35, 36, 37}) {
                    ModuleTest t = new ModuleTest();
                    t.install(sdk, loader);
                    check(t.hooks.size() == 2, "Expected only install-start and ADB hooks");
                    VerifyingSession s = new VerifyingSession(0x40);
                    check(t.call("handleStartVerify", s) == null, "Void result must be preserved");
                    check(s.flags() == 0x80040 && s.observedFlags == 0x80040, "Preserve bits and set disable flag before original");
                    check(s.starts == 1 && t.proceeds == 1, "Original must run exactly once");
                    check(Boolean.FALSE.equals(t.call("isAdbVerificationEnabled", s)), "ADB verification must be disabled");
                    check(t.proceeds == 1, "ADB original must not run");
                    VerifyingSession allBits = new VerifyingSession(-1);
                    t.call("handleStartVerify", allBits);
                    check(allBits.flags() == -1, "Existing bits must never be cleared");
                    t.call("handleStartVerify", s);
                    check(s.flags() == 0x80040 && s.starts == 2, "Flag operation must be idempotent");
                }
                break;
            case "missing":
                for (int sdk : new int[]{28, 33, 34}) {
                    ModuleTest t = new ModuleTest();
                    ClassLoader missing = new ClassLoader(loader) {
                        @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                            if (name.startsWith("com.android.server.pm.")) throw new ClassNotFoundException(name);
                            return super.loadClass(name);
                        }
                    };
                    try { t.install(sdk, missing); throw new AssertionError("Missing target must not be swallowed"); }
                    catch (IllegalStateException e) { check(e.getCause() instanceof ClassNotFoundException, "Preserve original reflection cause"); }
                }
                break;
            case "exception":
                ModuleTest t = new ModuleTest();
                t.install(34, loader);
                VerifyingSession s = new VerifyingSession(0);
                RuntimeException failure = new RuntimeException("synthetic original failure");
                s.failure = failure;
                try { t.call("handleStartVerify", s); throw new AssertionError("Original failure must propagate"); }
                catch (RuntimeException e) { check(e == failure && t.proceeds == 1 && s.starts == 1, "Original exception identity/count must be preserved"); }
                break;
            default: throw new AssertionError("Unknown test case");
        }
    }
}
