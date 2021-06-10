package com.tom.logisticsbridge.block;

import appeng.block.AEBaseTileBlock;
import com.tom.logisticsbridge.tileentity.TileEntityCraftingManager;
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

public class BlockCraftingManager extends AEBaseTileBlock {

    public BlockCraftingManager() {
        super(Material.IRON);
        setHardness(2f);
        setResistance(4f);
        setTileEntity(TileEntityCraftingManager.class);
    }

    @Override
    public boolean onBlockActivated(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer player, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ) && world.isRemote)
            return true;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityCraftingManager)
            ((TileEntityCraftingManager) te).openGui(player);
        return true;
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(@Nonnull World world, int meta) {
        return new TileEntityCraftingManager();
    }
}
