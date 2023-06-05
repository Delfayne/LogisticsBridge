package com.tom.logisticsbridge.proxy;

import com.tom.logisticsbridge.util.Reflector;
import gregtech.api.capability.impl.AbstractRecipeLogic;
import gregtech.api.capability.impl.RecipeLogicEnergy;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.WorkableTieredMetaTileEntity;
import logisticspipes.proxy.interfaces.IGenericProgressProvider;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

public class GTCEUProgressProvider implements IGenericProgressProvider {

    private static final MethodHandle workableGetter;

    static {
        workableGetter = Reflector.resolveFieldGetter(WorkableTieredMetaTileEntity.class, "workable");
    }

    @Override
    public boolean isType(TileEntity tileEntity) {
        return tileEntity instanceof MetaTileEntityHolder &&
                ((MetaTileEntityHolder) tileEntity).getMetaTileEntity() instanceof WorkableTieredMetaTileEntity;
    }

    @Override
    public byte getProgress(TileEntity tileEntity) {

        MetaTileEntityHolder mte = (MetaTileEntityHolder) tileEntity;
        WorkableTieredMetaTileEntity wte = (WorkableTieredMetaTileEntity) mte.getMetaTileEntity();

        Double progressPercent = Optional.ofNullable(getWorkable(wte))
                .map(AbstractRecipeLogic::getProgressPercent)
                .orElse(0D);

        return (byte) Math.min(progressPercent * 100, 100);
    }

    @Nullable
    private static RecipeLogicEnergy getWorkable(WorkableTieredMetaTileEntity wte) {
        try {
            return (RecipeLogicEnergy) workableGetter.invoke(wte);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
