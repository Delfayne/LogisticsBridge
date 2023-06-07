package org.dv.minecraft.logisticsbridge.block;

import com.raoulvdberge.refinedstorage.block.BlockNode;
import com.raoulvdberge.refinedstorage.block.info.BlockInfoBuilder;
import org.dv.minecraft.logisticsbridge.GuiHandler.GuiIDs;
import org.dv.minecraft.logisticsbridge.LogisticsBridge;
import org.dv.minecraft.logisticsbridge.Reference;
import org.dv.minecraft.logisticsbridge.tileentity.TileEntityCraftingManagerRS;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public class BlockCraftingManagerRS extends BlockNode {

    public BlockCraftingManagerRS() {
        super(BlockInfoBuilder.forMod(LogisticsBridge.modInstance, Reference.MOD_ID, "lb.craftingmanager.rs").
                tileEntity(TileEntityCraftingManagerRS::new).create());
    }

    @Override
    public boolean hasConnectedState() {
        return true;
    }

    @Nonnull
    @Override
    public String getTranslationKey() {
        return "tile.lb.craftingmanager.rs";
    }

    @Override
    public boolean onBlockActivated(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer player,
                                    @Nonnull EnumHand hand, @Nonnull EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!canAccessGui(state, world, pos, hitX, hitY, hitZ)) {
            return false;
        }

        return openNetworkGui(GuiIDs.CRAFTING_MANAGER.ordinal(), player, world, pos, side);
    }
}
