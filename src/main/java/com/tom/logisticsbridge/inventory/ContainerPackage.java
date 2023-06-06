package com.tom.logisticsbridge.inventory;

import com.tom.logisticsbridge.LB_ItemStore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class ContainerPackage extends Container implements Consumer<String> {

    public static final int PHANTOM_SLOT_CHANGE = 4;
    public String id;
    public Runnable update;
    private final EnumHand hand;
    private final InventoryBasic inv;
    public ContainerPackage(EntityPlayer player, EnumHand hand) {
        ItemStack is = player.getHeldItem(hand);
        inv = new InventoryBasic("", false, 1);
        if (is.hasTagCompound()) {
            inv.setInventorySlotContents(0, new ItemStack(is.getTagCompound()));
            id = is.getTagCompound().getString("__pkgDest");
        }
        addSlotToContainer(new SlotPhantom(inv, 0, 44, 20));
        addPlayerSlotsExceptHeldItem(player.inventory, 8, 51);
        this.hand = hand;
    }

    @Override
    public void onContainerClosed(@Nonnull EntityPlayer player) {
        super.onContainerClosed(player);
        ItemStack is = player.getHeldItem(hand);
        if (is.getItem() == LB_ItemStore.packageItem && (!is.hasTagCompound() || !is.getTagCompound().getBoolean("__actStack"))) {
            is.setTagCompound(inv.getStackInSlot(0).writeToNBT(new NBTTagCompound()));
            if (id != null)
                is.getTagCompound().setString("__pkgDest", id);
            is.getTagCompound().setBoolean("__actStack", false);
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    protected void addPlayerSlotsExceptHeldItem(InventoryPlayer playerInventory, int x, int y) {
        for (int i = 0; i < 3; ++i)
            for (int j = 0; j < 9; ++j)
                addSlotToContainer(new Slot(playerInventory, j + i * 9 + 9, x + j * 18, y + i * 18));
        for (int i = 0; i < 9; ++i)
            if (playerInventory.currentItem == i)
                addSlotToContainer(new SlotLocked(playerInventory, i, x + i * 18, y + 58));
            else
                addSlotToContainer(new Slot(playerInventory, i, x + i * 18, y + 58));
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, @Nonnull ClickType ct, @Nonnull EntityPlayer player) {
        Slot slot = slotId > -1 && slotId < inventorySlots.size() ? inventorySlots.get(slotId) : null;
        if (slot instanceof SlotPhantom) {
            ItemStack s = player.inventory.getItemStack().copy();
            if (!s.isEmpty())
                slot.putStack(dragType == 1 ? s.splitStack(1) : s);
            else if (dragType != 1)
                slot.putStack(ItemStack.EMPTY);
            else if (!slot.getStack().isEmpty()) {
                    int c = 1;
                    if (ct == ClickType.PICKUP_ALL)
                        c = -1;
                    else if (ct == ClickType.QUICK_CRAFT)
                        c = -PHANTOM_SLOT_CHANGE;
                    else if (ct == ClickType.CLONE)
                        c = PHANTOM_SLOT_CHANGE;
                    if (slot.getStack().getMaxStackSize() >= slot.getStack().getCount() + c && slot.getStack().getCount() + c > 0) {
                        slot.getStack().grow(c);
                    }
                }
            return player.inventory.getItemStack();
        } else
            return super.slotClick(slotId, dragType, ct, player);
    }

    @Override
    public void accept(String t) {
        id = t;
        if (update != null) update.run();
    }

    public static class SlotLocked extends Slot {
        public SlotLocked(IInventory inventoryIn, int index, int xPosition, int yPosition) {
            super(inventoryIn, index, xPosition, yPosition);
        }

        @Override
        public boolean canTakeStack(@Nonnull EntityPlayer player) {
            return false;
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return false;
        }
    }

    public static class SlotPhantom extends Slot {
        public int maxStackSize;

        public SlotPhantom(IInventory inv, int slotIndex, int posX, int posY) {
            this(inv, slotIndex, posX, posY, 1);
        }

        public SlotPhantom(IInventory inv, int slotIndex, int posX, int posY, int maxStackSize) {
            super(inv, slotIndex, posX, posY);
            this.maxStackSize = maxStackSize;
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @Override
        public boolean canTakeStack(@Nonnull EntityPlayer player) {
            return false;
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return true;
        }
    }
}
