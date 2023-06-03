package com.tom.logisticsbridge.block;

import appeng.block.AEBaseTileBlock;
import com.tom.logisticsbridge.tileentity.TileEntityBridgeAE;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BlockBridgeAE extends AEBaseTileBlock {
    public BlockBridgeAE() {
        super(Material.IRON);
        setHardness(2f);
        setResistance(4f);
        setTileEntity(TileEntityBridgeAE.class);
    }

    @Nonnull
    @Override
    public String getTranslationKey() {
        return "tile.lb.bridge";
    }

    @Override
    public boolean onBlockActivated(World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer player,
                                    @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote)
            ((TileEntityBridgeAE) world.getTileEntity(pos)).blockClicked(player);
        return true;
    }
}
