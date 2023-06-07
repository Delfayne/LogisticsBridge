package org.dv.minecraft.logisticsbridge;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class LB_ItemStore {
    public static Block bridgeAE;
    public static Block bridgeRS;
    public static Block craftingManager;
    public static Item logisticsFakeItem;
    public static Item packageItem;

    @GameRegistry.ObjectHolder("logisticspipes:pipe_lb.bridgepipe")
    public static Item pipeBridge;

    @GameRegistry.ObjectHolder("logisticspipes:pipe_lb.resultpipe")
    public static Item pipeResult;

    @GameRegistry.ObjectHolder("logisticspipes:pipe_lb.craftingmanager")
    public static Item pipeCraftingManager;

    @GameRegistry.ObjectHolder("logisticspipes:upgrade_lb.buffer_upgrade")
    public static Item upgradeBuffer;

    @GameRegistry.ObjectHolder("logisticspipes:upgrade_lb.adv_extraction_upgrade")
    public static Item upgradeAdvExt;
}
