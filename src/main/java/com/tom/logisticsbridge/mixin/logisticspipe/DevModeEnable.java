package com.tom.logisticsbridge.mixin.logisticspipe;

import com.tom.logisticsbridge.annotation.LateMixin;
import logisticspipes.LogisticsPipes;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LogisticsPipes.class)
@LateMixin
public abstract class DevModeEnable {

    // Enable debug mode
    @Inject(method = "isDEBUG", at = @At(value = "HEAD"), cancellable = true, remap = false)
    private static void toggleDebug(CallbackInfoReturnable<Boolean> cir) {
        if (FMLLaunchHandler.isDeobfuscatedEnvironment()) cir.setReturnValue(true);
    }

}
