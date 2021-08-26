//package ru.DmN.LB;
//
//import javassist.CtClass;
//import javassist.CtMethod;
//import net.minecraft.launchwrapper.IClassTransformer;
//import net.minecraft.launchwrapper.Launch;
//
//import static ru.DmN.LB.Main.pool;
//
//public class DmNTransformer implements IClassTransformer {
//    @Override
//    public byte[] transform(String name, String transformedName, byte[] basicClass) {
//        if (name.equals("net.minecraft.client.resources.FallbackResourceManager")) {
//            try {
//                CtClass clazz = pool.get("net.minecraft.client.resources.FallbackResourceManager");
//                clazz.getMethod("getResource", "(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/resources/IResource;").setName("__getResource__");
//                clazz.addMethod(CtMethod.make("public net.minecraft.client.resources.IResource getResource(net.minecraft.util.ResourceLocation l) { net.minecraft.client.resources.IResource r = ru.DmN.LB.Main.__F1(this, l); if(r != null) return r; return __getResource__(l); }", clazz));
//                return clazz.toBytecode();
//            } catch (Exception e) {
//                throw new Error(e);
//            }
//        }
//
//        return basicClass;
//    }
//
//    static {
//        try {
//            CtClass clazz0 = pool.get("net.minecraftforge.fml.common.asm.ASMTransformerWrapper$TransformerWrapper");
//            clazz0.removeMethod(clazz0.getMethod("transform", "(Ljava/lang/String;Ljava/lang/String;[B)[B"));
//            clazz0.addMethod(CtMethod.make("public byte[] transform(java.lang.String n0, java.lang.String n1, byte[] b) { try { return parent.transform(n0, n1, b); } catch(java.lang.Throwable e) { return b; } }", clazz0));
//            clazz0.toClass(Launch.classLoader, Launch.class.getProtectionDomain());
//
//            CtClass clazz1 = pool.get("net.minecraftforge.fml.common.asm.transformers.TerminalTransformer");
//            clazz1.getMethod("transform", "(Ljava/lang/String;Ljava/lang/String;[B)[B").setName("__transform__");
//            clazz1.addMethod(CtMethod.make("public byte[] transform(java.lang.String n0, java.lang.String n1, byte[] b) { try { return __transform__(n0, n1, b); } catch(Exception e) { return b; } }", clazz1));
//            clazz1.toClass(Launch.classLoader, Launch.class.getProtectionDomain());
//        } catch(Exception e) {
//            throw new Error(e);
//        }
//    }
//}
