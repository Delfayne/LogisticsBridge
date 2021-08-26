package ru.DmN.LB;

import javassist.ClassPool;
import javassist.CtClass;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.*;
import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.legacydev.MainClient;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;

public class Main {
    public static ClassPool pool = ClassPool.getDefault();

    public static void main(String[] args) throws Exception {
        CtClass clazz = pool.get("net.minecraft.launchwrapper.Launch");
        clazz.getConstructor("()V").insertAfter("ru.DmN.LB.Main.__F0();");
        clazz.toClass(Main.class.getClassLoader(), Main.class.getProtectionDomain());
        //
        MainClient.main(args);
    }

    public static void __F0() {
        Launch.classLoader.registerTransformer("ru.DmN.LB.DmNTransformer");
    }

    static boolean init = false;
    public static IResource __F1(FallbackResourceManager instance, ResourceLocation res) {
        if (!init) {
            ModelLoaderRegistry.registerLoader(new DmNML());
            init = true;
        }
        return null; // TODO: NEED TO ДОДЕЛАТЬ xD
    }

    public static class DmNML implements ICustomModelLoader {
        @Override
        public void onResourceManagerReload(@NotNull IResourceManager resourceManager) { }

        @Override
        public boolean accepts(@NotNull ResourceLocation modelLocation) {
            return modelLocation.getResourceDomain().equals("logisticsbridge");
        }

        @NotNull
        @Override
        public IModel loadModel(@NotNull ResourceLocation modelLocation) throws Exception {
            String models = new File("../src/main/resources/assets/logisticsbridge/models").getAbsolutePath();
            if (modelLocation.getResourcePath().equals("models/part/satellite_bus_on")) {
                Field f = Class.forName("net.minecraftforge.client.model.ModelLoader$VanillaLoader").getDeclaredField("INSTANCE");
                f.setAccessible(true);
                ModelBlock m = ModelBlock.deserialize(models + "part/satellite_bus_on.json");;
                return (IModel) Class.forName("net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper").getConstructors()[0].newInstance(f.get(null), modelLocation, m, false, null);
            }
            throw new Error();
        }
    }
}