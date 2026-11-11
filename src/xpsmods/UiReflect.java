package xpsmods;

import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls methods on the game's obfuscated UI classes by name.
 *
 * The game blocks java.lang.reflect in mod code, so java.lang.reflect.Method is looked up by
 * name and used through MethodHandles. LunaLib and Exiled Sector use the same workaround.
 */
final class UiReflect {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Object[] NO_ARGS = new Object[0];

    private static final MethodHandle GET_METHODS;
    private static final MethodHandle METHOD_NAME;
    private static final MethodHandle METHOD_PARAM_COUNT;
    private static final MethodHandle METHOD_INVOKE;
    private static final MethodHandle METHOD_SET_ACCESSIBLE;

    static {
        try {
            Class<?> methodClass = Class.forName("java.lang.reflect.Method", false, Class.class.getClassLoader());
            GET_METHODS = LOOKUP.findVirtual(Class.class, "getMethods", MethodType.methodType(methodClass.arrayType()))
                    .asType(MethodType.methodType(Object[].class, Class.class));
            METHOD_NAME = LOOKUP.findVirtual(methodClass, "getName", MethodType.methodType(String.class))
                    .asType(MethodType.methodType(String.class, Object.class));
            METHOD_PARAM_COUNT = LOOKUP.findVirtual(methodClass, "getParameterCount", MethodType.methodType(int.class))
                    .asType(MethodType.methodType(int.class, Object.class));
            METHOD_INVOKE = LOOKUP.findVirtual(methodClass, "invoke",
                            MethodType.methodType(Object.class, Object.class, Object[].class))
                    .asFixedArity()
                    .asType(MethodType.methodType(Object.class, Object.class, Object.class, Object[].class));
            METHOD_SET_ACCESSIBLE = LOOKUP.findVirtual(methodClass, "setAccessible", MethodType.methodType(void.class, boolean.class))
                    .asType(MethodType.methodType(void.class, Object.class, boolean.class));
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    /** Cache: class -> "name/argCount" -> java.lang.reflect.Method (held as Object). */
    private static final Map<Class<?>, Map<String, Object>> METHODS = new HashMap<Class<?>, Map<String, Object>>();

    private UiReflect() {}

    /** Calls the public method {@code name} with {@code args.length} parameters on {@code target}. */
    static Object call(Object target, String name, Object... args) throws Throwable {
        if (target == null) return null;
        if (args == null) args = NO_ARGS;
        Object method = find(target.getClass(), name, args.length);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "." + name + " with " + args.length + " args");
        }
        return (Object) METHOD_INVOKE.invokeExact(method, target, args);
    }

    static boolean hasMethod(Object target, String name, int argCount) throws Throwable {
        return target != null && find(target.getClass(), name, argCount) != null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> children(Object panel) throws Throwable {
        return (List<Object>) call(panel, "getChildrenNonCopy");
    }

    /** The core UI panel that holds the refit screen, inside or outside an interaction dialog. */
    static Object coreUI(CampaignUIAPI campaignUI) throws Throwable {
        InteractionDialogAPI dialog = campaignUI.getCurrentInteractionDialog();
        if (dialog != null) return call(dialog, "getCoreUI");
        return call(campaignUI, "getCore");
    }

    private static Object find(Class<?> cls, String name, int argCount) throws Throwable {
        Map<String, Object> byName = METHODS.get(cls);
        if (byName == null) {
            byName = new HashMap<String, Object>();
            METHODS.put(cls, byName);
        }
        String key = name + "/" + argCount;
        if (byName.containsKey(key)) return byName.get(key);

        Object found = null;
        for (Object m : (Object[]) GET_METHODS.invokeExact(cls)) {
            if (name.equals((String) METHOD_NAME.invokeExact(m)) && (int) METHOD_PARAM_COUNT.invokeExact(m) == argCount) {
                try {
                    METHOD_SET_ACCESSIBLE.invokeExact(m, true);
                } catch (Throwable ignored) {
                    // Public methods on public classes don't need it.
                }
                found = m;
                break;
            }
        }
        byName.put(key, found);
        return found;
    }
}
