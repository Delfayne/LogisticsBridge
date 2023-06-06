package com.tom.logisticsbridge;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.prioritylist.IPartitionList;

public class FakeItemList implements IPartitionList<IAEItemStack> {
	private final List<IAEItemStack> ITEMS = Collections.singletonList(
			AE2Plugin.INSTANCE.api.storage().getStorageChannel(IItemStorageChannel.class).
			createStack(new ItemStack(LB_ItemStore.logisticsFakeItem, 1)));

	@Override
	public boolean isListed(IAEItemStack input) {
		return !GuiScreen.isCtrlKeyDown() && (input.getItem() == LB_ItemStore.logisticsFakeItem || (input.getItem() == LB_ItemStore.packageItem && input.getStackSize() == 0));
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public Iterable<IAEItemStack> getItems() {
		return ITEMS;
	}

	public Stream<IAEItemStack> getStreams() {
		return ITEMS.stream();
	}

}
