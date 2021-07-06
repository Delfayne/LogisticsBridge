package com.tom.logisticsbridge;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import com.tom.logisticsbridge.gui.GuiCraftingManager;
import com.tom.logisticsbridge.gui.GuiCraftingManagerU;
import com.tom.logisticsbridge.gui.GuiPackage;
import com.tom.logisticsbridge.gui.GuiResultPipe;
import com.tom.logisticsbridge.inventory.ContainerCraftingManager;
import com.tom.logisticsbridge.inventory.ContainerCraftingManagerU;
import com.tom.logisticsbridge.inventory.ContainerPackage;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.part.PartSatelliteBus;
import com.tom.logisticsbridge.pipe.CraftingManager;
import com.tom.logisticsbridge.tileentity.ICraftingManager;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.gui.DummyContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import network.rs485.logisticspipes.SatellitePipe;

public class GuiHandler implements IGuiHandler {
    private static CoreUnroutedPipe getPipe(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        LogisticsTileGenericPipe pipe = null;
        if (tile instanceof LogisticsTileGenericPipe)
            pipe = (LogisticsTileGenericPipe) tile;
        if (pipe != null)
            return pipe.pipe;
        return null;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id >= 100 && id < 120) {
            AEPartLocation side = AEPartLocation.fromOrdinal(id - 100);
            final TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof IPartHost) {
                final IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartSatelliteBus)
                    return new DummyContainer(player.inventory, null);
            }
            return null;
        }
        if (id == 5) {
            final TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te != null && te instanceof IIdPipe)
                return new DummyContainer(player.inventory, null);
            return null;
        }
        switch (GuiIDs.values[id]) {
            case RESULT_PIPE: {
                CoreUnroutedPipe pipe = getPipe(world, x, y, z);
                if (pipe != null && pipe instanceof IIdPipe)
                    return new DummyContainer(player.inventory, null);
            }
            break;
            case CRAFTING_MANAGER: {
                CoreUnroutedPipe pipe = getPipe(world, x, y, z);
                if (pipe != null && pipe instanceof CraftingManager)
                    return new ContainerCraftingManager(player, (CraftingManager) pipe);
                else {
                    TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
                    if (tile instanceof ICraftingManager)
                        return new ContainerCraftingManagerU(player, (ICraftingManager) tile);
                }
            }
            break;
            case TEMPLATE_PKG: {
                EnumHand hand = EnumHand.values()[x];
                ItemStack is = player.getHeldItem(hand);
                if (is.getItem() == LogisticsBridge.packageItem)
                    return new ContainerPackage(player, hand);
            }
            break;
            default:
                break;
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID >= 100 && ID < 120) {
            AEPartLocation side = AEPartLocation.fromOrdinal(ID - 100);
            final TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof IPartHost) {
                final IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof IIdPipe)
                    return new GuiResultPipe((IIdPipe) part, player, 0));
            }
            return null;
        }
        if (ID == 5) {
            final TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te != null && te instanceof IIdPipe)
                return new GuiResultPipe((IIdPipe) te, player, 0));
            return null;
        }
        switch (GuiIDs.values[ID]) {
            case RESULT_PIPE: {
                CoreUnroutedPipe pipe = getPipe(world, x, y, z);
                if (pipe != null && pipe instanceof SatellitePipe) {
                    return new GuiResultPipe((SatellitePipe) pipe);
                }
            }
            break;
            case CRAFTING_MANAGER: {
                CoreUnroutedPipe pipe = getPipe(world, x, y, z);
                if (pipe != null && pipe instanceof CraftingManager)
                    return new GuiCraftingManager(player, (CraftingManager) pipe);
                else {
                    TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
                    if (tile instanceof ICraftingManager)
                        return new GuiCraftingManagerU(player, (ICraftingManager) tile);
                }
            }
            break;
            case TEMPLATE_PKG: {
                EnumHand hand = EnumHand.values()[x];
                ItemStack is = player.getHeldItem(hand);
                if (is.getItem() == LogisticsBridge.packageItem)
                    return new GuiPackage(player, hand);
            }
            break;
            default:
                break;
        }
        return null;
    }

    public enum GuiIDs {
        RESULT_PIPE,
        CRAFTING_MANAGER,
        TEMPLATE_PKG,

        ;
        protected static final GuiIDs[] values = values();
    }
}
