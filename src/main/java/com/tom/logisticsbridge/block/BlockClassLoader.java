package com.tom.logisticsbridge.block;

import com.tom.logisticsbridge.LogisticsBridge;
import javassist.*;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.net.URL;

public class BlockClassLoader extends LaunchClassLoader {
    private ClassLoader parent;
    public BlockClassLoader(ClassLoader parent) {
        super(new URL[]{});
        this.parent = parent;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        LogisticsBridge.log.warn(name);
        try {
            if (name.equals("com.tom.logisticsbridge.block.BlockBridgeAE"))
                return loadBridgeAE();
            else if (name.equals("com.tom.logisticsbridge.block.BlockCraftingManager"))
                return loadBlockCraftingManager();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return parent.loadClass(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        LogisticsBridge.log.warn(name);
        try {
            if (name.equals("com.tom.logisticsbridge.block.BlockBridgeAE"))
                return loadBridgeAE();
            else if (name.equals("com.tom.logisticsbridge.block.BlockCraftingManager"))
                return loadBlockCraftingManager();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return parent.loadClass(name);
    }

    public static Class<BlockBridgeAE> loadBridgeAE() throws NotFoundException, CannotCompileException {
        ClassPool.getDefault().insertClassPath(new ClassClassPath(BlockClassLoader.class));
        CtClass clazz = ClassPool.getDefault().get("com.tom.logisticsbridge.block.BlockBridgeAE");

        clazz.removeMethod(clazz.getMethod("func_149915_a", "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;"));

        return clazz.toClass();
    }

    public static Class<BlockCraftingManager> loadBlockCraftingManager() throws NotFoundException, CannotCompileException {
        ClassPool.getDefault().insertClassPath(new ClassClassPath(BlockClassLoader.class));
        CtClass clazz = ClassPool.getDefault().get("com.tom.logisticsbridge.block.BlockCraftingManager");

        clazz.removeMethod(clazz.getMethod("func_149915_a", "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;"));

        return clazz.toClass();
    }
}
