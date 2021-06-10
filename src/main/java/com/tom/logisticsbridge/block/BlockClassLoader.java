package com.tom.logisticsbridge.block;

import com.tom.logisticsbridge.LogisticsBridge;
import javassist.*;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.net.URL;

public class BlockClassLoader extends LaunchClassLoader {
    private static final String BBAE = "com.tom.logisticsbridge.block.BlockBridgeAE";
    private static final String BCM = "com.tom.logisticsbridge.block.BlockCraftingManager";
    private final ClassLoader parent;

    public BlockClassLoader(ClassLoader parent) {
        super(new URL[]{});
        this.parent = parent;
    }

    public static Class<?> loadBridgeAE() throws NotFoundException, CannotCompileException {
        ClassPool.getDefault().insertClassPath(new ClassClassPath(BlockClassLoader.class));
        CtClass clazz = ClassPool.getDefault().get(BBAE);

        clazz.removeMethod(clazz.getMethod("func_149915_a", "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;"));

        return clazz.toClass();
    }

    public static Class<?> loadBlockCraftingManager() throws NotFoundException, CannotCompileException {
        ClassPool.getDefault().insertClassPath(new ClassClassPath(BlockClassLoader.class));
        CtClass clazz = ClassPool.getDefault().get(BCM);

        clazz.removeMethod(clazz.getMethod("func_149915_a", "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;"));

        return clazz.toClass();
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            return parent.loadClass(name);
        } catch (ClassNotFoundException ignored) {
            try {
                if (name.equals(BBAE))
                    return loadBridgeAE();
                else if (name.equals(BCM))
                    return loadBlockCraftingManager();
            } catch (NotFoundException | CannotCompileException e) {
                e.printStackTrace();
                throw new ClassNotFoundException(e.toString());
            }
        }

        throw new ClassNotFoundException(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        LogisticsBridge.log.warn(name);
        try {
            if (name.equals(BBAE))
                return loadBridgeAE();
            else if (name.equals(BCM))
                return loadBlockCraftingManager();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return parent.loadClass(name);
    }
}
