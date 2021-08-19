package ru.DmN.LB;

import net.minecraft.launchwrapper.IClassTransformer;

public class DmNTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        return basicClass;
    }
}
