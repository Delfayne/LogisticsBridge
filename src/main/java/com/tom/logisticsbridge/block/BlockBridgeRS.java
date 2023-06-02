package com.tom.logisticsbridge.block;

import com.raoulvdberge.refinedstorage.block.BlockNode;
import com.raoulvdberge.refinedstorage.block.info.BlockInfoBuilder;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.tileentity.TileEntityBridgeRS;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public class BlockBridgeRS extends BlockNode {

    public BlockBridgeRS() {
        super(BlockInfoBuilder.forMod(LogisticsBridge.modInstance, LogisticsBridge.ID, "lb.bridge.rs").tileEntity(TileEntityBridgeRS::new).create());
    }

    @Override
    public boolean hasConnectedState() {
        return true;
    }

    @Nonnull
    @Override
    public String getTranslationKey() {
        return "tile.lb.bridge.rs";
    }

    @Override
    public boolean onBlockActivated(World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer playerIn,
                                    @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote)
            ((TileEntityBridgeRS) world.getTileEntity(pos)).blockClicked(playerIn);
        return true;
    }
}
