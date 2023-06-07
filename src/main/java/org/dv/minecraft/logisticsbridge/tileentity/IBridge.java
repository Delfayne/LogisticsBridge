package org.dv.minecraft.logisticsbridge.tileentity;

import org.dv.minecraft.logisticsbridge.api.BridgeStack;
import org.dv.minecraft.logisticsbridge.pipe.BridgePipe.Req;
import net.minecraft.item.ItemStack;

import java.util.List;

public interface IBridge {

    long countItem(ItemStack stack, boolean requestable);

    void craftStack(ItemStack stack, int count, boolean simulate);

    List<BridgeStack<ItemStack>> getItems();

    ItemStack extractStack(ItemStack stack, int count, boolean simulate);

    void setReqAPI(Req req);

}
