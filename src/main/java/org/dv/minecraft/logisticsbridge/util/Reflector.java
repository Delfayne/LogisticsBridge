package org.dv.minecraft.logisticsbridge.util;

import org.dv.minecraft.logisticsbridge.LogisticsBridge;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.Optional;

/*
 * Helper class for Reflection nonsense.
 * https://github.com/LoliKingdom/LoliASM/blob/a1e25e1b141778f53185d6ea37d54ac64a8be5b7/src/main/java/zone/rong/loliasm/LoliReflector.java
 */
public class Reflector {

    public static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public static MethodHandle resolveCtor(Class<?> clazz, Class<?>... args) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor(args);
            ctor.setAccessible(true);
            return LOOKUP.unreflectConstructor(ctor);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> Constructor<T> getCtor(Class<T> clazz, Class<?>... args) {
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor(args);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MethodHandle resolveMethod(Class<?> clazz, String methodName, Class<?>... args) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, args);
            method.setAccessible(true);
            return LOOKUP.unreflect(method);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... args) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, args);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MethodHandle resolveMethod(Class<?> clazz, String methodName, String obfMethodName, Class<?>... args) {
        try {
            return LOOKUP.unreflect(ReflectionHelper.findMethod(clazz, methodName, obfMethodName, args));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Method getMethod(Class<?> clazz, String methodName, String obfMethodName, Class<?>... args) {
        return ReflectionHelper.findMethod(clazz, methodName, obfMethodName, args);
    }

    public static MethodHandle resolveFieldGetter(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            if (LogisticsBridge.isVMOpenJ9) {
                fixOpenJ9PrivateStaticFinalRestraint(field);
            }
            return LOOKUP.unreflectGetter(field);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MethodHandle resolveFieldSetter(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            if (LogisticsBridge.isVMOpenJ9) {
                fixOpenJ9PrivateStaticFinalRestraint(field);
            }
            return LOOKUP.unreflectSetter(field);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MethodHandle resolveFieldGetter(Class<?> clazz, String fieldName, String obfFieldName) {
        try {
            return LOOKUP.unreflectGetter(ReflectionHelper.findField(clazz, fieldName, obfFieldName));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MethodHandle resolveFieldSetter(Class<?> clazz, String fieldName, String obfFieldName) {
        try {
            return LOOKUP.unreflectSetter(ReflectionHelper.findField(clazz, fieldName, obfFieldName));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Field getField(Class<?> clazz, String fieldName, String obfFieldName) {
        return ReflectionHelper.findField(clazz, fieldName, obfFieldName);
    }

    public static boolean doesClassExist(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) { }
        return false;
    }

    public static Class<?> getNullableClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) { }
        return null;
    }

    public static Optional<Class<?>> getClass(String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException ignored) { }
        return Optional.empty();
    }

    private static void fixOpenJ9PrivateStaticFinalRestraint(Field field) throws Throwable {
        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        LOOKUP.unreflectSetter(modifiers).invokeExact(field, field.getModifiers() & ~Modifier.FINAL);
    }

}
