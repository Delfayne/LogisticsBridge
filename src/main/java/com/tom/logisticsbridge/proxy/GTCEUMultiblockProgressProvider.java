package com.tom.logisticsbridge.proxy;

import com.tom.logisticsbridge.util.Reflector;
import gregtech.api.capability.impl.AbstractRecipeLogic;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;
import logisticspipes.proxy.interfaces.IGenericProgressProvider;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

public class GTCEUMultiblockProgressProvider implements IGenericProgressProvider {

    private static final MethodHandle workableGetter;

    static {
        workableGetter = Reflector.resolveFieldGetter(RecipeMapMultiblockController.class, "recipeMapWorkable");
    }

    @Override
    public boolean isType(TileEntity tileEntity) {
        return tileEntity instanceof MetaTileEntityHolder &&
                ((MetaTileEntityHolder) tileEntity).getMetaTileEntity() instanceof MetaTileEntityMultiblockPart &&
                ((MetaTileEntityMultiblockPart) ((MetaTileEntityHolder) tileEntity).getMetaTileEntity()).getController() instanceof RecipeMapMultiblockController;
    }

    @Override
    public byte getProgress(TileEntity tileEntity) {

        MetaTileEntity metaTileEntity = ((MetaTileEntityHolder) tileEntity).getMetaTileEntity();
        MultiblockControllerBase controller = ((MetaTileEntityMultiblockPart) metaTileEntity).getController();
        RecipeMapMultiblockController recipeMapController = (RecipeMapMultiblockController) controller;

        Double progressPercent = Optional.ofNullable(getWorkable(recipeMapController))
                .map(AbstractRecipeLogic::getProgressPercent)
                .orElse(0D);

        return (byte) Math.min(progressPercent * 100, 100);
    }

    @Nullable
    private static MultiblockRecipeLogic getWorkable(RecipeMapMultiblockController controller) {
        try {
            return (MultiblockRecipeLogic) workableGetter.invoke(controller);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
