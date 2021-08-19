package ru.DmN.LB;

import javassist.ClassPool;
import javassist.CtClass;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.legacydev.MainClient;

public class Main {
    public static ClassPool pool = ClassPool.getDefault();

    public static void main(String[] args) throws Exception {
        CtClass clazz = pool.get("net.minecraft.launchwrapper.Launch");
        clazz.getConstructor("()V").insertAfter("ru.DmN.LB.Main.__F0__();");
        clazz.toClass(Main.class.getClassLoader(), Main.class.getProtectionDomain());
        //
        MainClient.main(args);
    }

    public static void __F0__() {
        Launch.classLoader.registerTransformer("ru.DmN.LB.DmNTransformer");
    }
}