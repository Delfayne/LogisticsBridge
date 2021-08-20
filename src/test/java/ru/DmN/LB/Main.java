package ru.DmN.LB;

import javassist.ClassPool;
import javassist.CtClass;
import net.minecraft.client.resources.IResource;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.legacydev.MainClient;

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

    public static IResource __F1(ResourceLocation str) {
        return null; // TODO: NEED TO ДОДЕЛАТЬ xD
//        return str.startsWith("appliedenergistics2:models/part/satellite_bus.json");
    }
}