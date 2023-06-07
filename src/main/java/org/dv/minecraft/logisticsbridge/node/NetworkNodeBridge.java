package org.dv.minecraft.logisticsbridge.node;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageProvider;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import org.dv.minecraft.logisticsbridge.LB_ItemStore;
import org.dv.minecraft.logisticsbridge.LogisticsBridge;
import org.dv.minecraft.logisticsbridge.api.BridgeStack;
import org.dv.minecraft.logisticsbridge.api.IDynamicPatternDetailsRS;
import org.dv.minecraft.logisticsbridge.item.VirtualPatternRS;
import org.dv.minecraft.logisticsbridge.pipe.BridgePipe.OpResult;
import org.dv.minecraft.logisticsbridge.pipe.BridgePipe.Req;
import org.dv.minecraft.logisticsbridge.tileentity.IBridge;
import org.dv.minecraft.logisticsbridge.util.DynamicInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.InvWrapper;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

public class NetworkNodeBridge extends NetworkNode implements IStorageProvider, IStorage<ItemStack>, IBridge, IItemHandler, IDynamicPatternDetailsRS, ICraftingPatternContainer {
    public static final String ID = "lb.bridge";
    private static final String NBT_UUID = "BridgeUuid";
    private static final String NAME = "tile.lb.bridge.rs.name";
    private long lastInjectTime;
    private Req req;
    private final IStackList<ItemStack> list = API.instance().createItemStackList();
    private boolean firstTick = true;
    private final DynamicInventory patterns = new DynamicInventory();
    private final DynamicInventory craftingItems = new DynamicInventory();
    private final List<ICraftingPattern> craftingPatterns = new ArrayList<>();
    private final Deque<ItemStack> requestList = new ArrayDeque<>();
    private final InvWrapper craftingItemsWrapper = new InvWrapper(craftingItems) {
        @Nonnull
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            lastInjectTime = world.getTotalWorldTime();
            if (stack.getItem() == LB_ItemStore.logisticsFakeItem) {
                if (pushPattern(stack, simulate))
                    return ItemStack.EMPTY;
                else return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public int getSlots() {
            return super.getSlots() + 16;
        }
    };

    private UUID uuid = null;
    private boolean disableLP;
    private boolean bridgeMode;

    public NetworkNodeBridge(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return 2;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void addItemStorages(List<IStorage<ItemStack>> storages) {
        storages.add(this);
    }

    @Override
    public void addFluidStorages(List<IStorage<FluidStack>> storages) { }

    @Override
    public Collection<ItemStack> getStacks() {
        return list.getStacks();
    }

    @Override
    public ItemStack insert(ItemStack stack, int size, Action action) {
        if (stack.getItem() == LB_ItemStore.logisticsFakeItem) return null;
        ItemStack ret = stack.copy();
        ret.setCount(size);
        return ret;
    }

    @Override
    public ItemStack extract(ItemStack stack, int size, int flags, Action action) {
        if (stack.getItem() == LB_ItemStore.logisticsFakeItem) {
            ItemStack st = list.get(stack, flags);
            int min = Math.min(size, st.getCount());
            ItemStack ret = st.copy();
            st.setCount(min);
            if (action == Action.PERFORM) {
                list.remove(st, min);
            }
            return ret;
        }
        return null;
    }

    @Override
    public int getStored() {
        return 0;
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public AccessType getAccessType() {
        return AccessType.INSERT_EXTRACT;
    }

    @Override
    public int getCacheDelta(int storedPreInsertion, int size, ItemStack remainder) {
        return 0;
    }

    @Override
    public long countItem(ItemStack stack, boolean requestable) {
        if (disableLP && !bridgeMode || network == null)
            return 0;
        ItemStack is = network.getItemStorageCache().getList().get(stack);
        int inRS = is == null ? 0 : is.getCount();
        int inBuf = craftingItems.stream().filter(s -> itemsEquals(s, stack)).mapToInt(ItemStack::getCount).sum();
        return ((long) inRS) + ((long) inBuf);
    }

    @Override
    public void craftStack(ItemStack stack, int count, boolean simulate) {
        ICraftingTask task = network.getCraftingManager().create(stack, count);
        if (task == null) {
            return;
        }

        ICraftingTaskError error = task.calculate();
        if (error == null && !task.hasMissing()) {
            network.getCraftingManager().add(task);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<BridgeStack<ItemStack>> getItems() {
        if (disableLP && !bridgeMode || network == null) return Collections.emptyList();
        return LogisticsBridge.concatStreams(
                network.getItemStorageCache().getList().getStacks().stream().
                        filter(e -> e != null && e.getItem() != LB_ItemStore.logisticsFakeItem).
                        map(s -> new BridgeStack<>(s, s.getCount(), false, 0)),

                network.getCraftingManager().getPatterns().stream().map(ICraftingPattern::getOutputs).flatMap(NonNullList::stream).
                        filter(s -> s != null && !s.isEmpty() && s.getItem() != LB_ItemStore.logisticsFakeItem).
                        map(s -> new BridgeStack<>(s, 0, true, 0)),

                craftingItems.stream().
                        map(s -> new BridgeStack<>(s, s.getCount(), false, 0))
        ).collect(Collectors.toList());
    }

    @Override
    public ItemStack extractStack(ItemStack stack, int count, boolean simulate) {
        if (network == null)
            return ItemStack.EMPTY;
        return network.extractItem(stack, count, simulate ? Action.SIMULATE : Action.PERFORM);
    }

    @Override
    public void setReqAPI(Req req) {
        this.req = req;
    }

    @Override
    public void update() {
        super.update();
        if (!world.isRemote) {
            long wt = world.getTotalWorldTime();
            if (req != null && wt % 20 == 0 && network != null) {
                List<ItemStack> stack = new ArrayList<>(list.getStacks());
                List<ItemStack> pi = req.getProvidedItems();
                list.clear();
                List<ItemStack> crafts = patterns.stream().collect(Collectors.toList());
                patterns.clear();
                craftingPatterns.clear();
                List<ItemStack> ci = req.getCraftedItems();
                TileEntityWrapper wr = new TileEntityWrapper(world, pos);
                pi.forEach(i -> {
                    ItemStack r = i.copy();
                    r.setCount(1);
                    list.add(LogisticsBridge.fakeStack(r, i.getCount()));
                    ICraftingPattern pattern = VirtualPatternRS.create(r, wr, this);
                    patterns.addStack(pattern.getStack());
                    craftingPatterns.add(pattern);
                });
                list.add(new ItemStack(LB_ItemStore.logisticsFakeItem, 65536));
                ci.stream().map(i -> VirtualPatternRS.create(i, wr, this)).forEach(pattern -> {
                    patterns.addStack(pattern.getStack());
                    craftingPatterns.add(pattern);
                    pattern = VirtualPatternRS.create(new ItemStack(LB_ItemStore.logisticsFakeItem),
                            LogisticsBridge.fakeStack(pattern.getOutputs().get(0), 1), this);
                    patterns.addStack(pattern.getStack());
                    craftingPatterns.add(pattern);
                });
                if (firstTick || stack.size() != list.getStacks().size() || crafts.size() != patterns.getSizeInventory() ||
                        stack.stream().anyMatch(s -> {
                            ItemStack st = list.get(s);
                            return st == null || st.isEmpty() || st.getCount() != s.getCount();
                        }) || patterns.stream().anyMatch(p -> crafts.stream().noneMatch(i -> ItemStack.areItemStackTagsEqual(i, p)))) {
                    network.getItemStorageCache().invalidate();
                    network.getCraftingManager().rebuild();
                }
                firstTick = false;
            }
            if (lastInjectTime + 200 < wt && !craftingItems.isEmpty()) {
                lastInjectTime = wt - 20;
                for (int i = 0; i < craftingItems.getSizeInventory(); i++)
                    craftingItems.setInventorySlotContents(i, insertItem(0, craftingItems.getStackInSlot(i), false));
                craftingItems.removeEmpties();
            }
            if (wt % 5 == 0 && !requestList.isEmpty() && req != null) {
                ItemStack toReq = requestList.pop();
                boolean pushed = req.performRequest(toReq, true).missing.isEmpty();
                if (!pushed) {
                    requestList.add(toReq);
                }
            }
        }
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (network == null)
            return stack;
        ItemStack is = simulate ? network.insertItem(stack, stack.getCount(), Action.SIMULATE) : network.insertItemTracked(stack, stack.getCount());
        return is == null ? ItemStack.EMPTY : is;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public IItemHandler getConnectedInventory() {
        return craftingItemsWrapper;
    }

    @Override
    public IFluidHandler getConnectedFluidInventory() {
        return null;
    }

    @Override
    public TileEntity getConnectedTile() {
        return null;
    }

    @Override
    public IItemHandlerModifiable getPatternInventory() {
        return null;
    }

    @Override
    public List<ICraftingPattern> getPatterns() {
        return craftingPatterns;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public BlockPos getPosition() {
        return pos;
    }

    @Override
    public ICraftingPatternContainer getRootContainer() {
        return this;
    }

    @Override
    public UUID getUuid() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
            markDirty();
        }

        return uuid;
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        if (tag.hasUniqueId(NBT_UUID))
            uuid = tag.getUniqueId(NBT_UUID);

        NBTTagList l = tag.getTagList("reqList", 10);
        requestList.clear();
        for (int i = 0; i < l.tagCount(); i++)
            requestList.add(new ItemStack(l.getCompoundTagAt(i)));

        bridgeMode = tag.getBoolean("bridgeMode");

        LogisticsBridge.loadAllItems(tag.getTagList("intInventory", 10), craftingItems);
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        if (uuid != null)
            tag.setUniqueId(NBT_UUID, uuid);

        NBTTagList l = new NBTTagList();
        requestList.stream().map(s -> s.writeToNBT(new NBTTagCompound())).forEach(l::appendTag);
        tag.setTag("reqList", l);

        craftingItems.removeEmpties();
        tag.setTag("intInventory", LogisticsBridge.saveAllItems(craftingItems));

        tag.setBoolean("bridgeMode", bridgeMode);

        return tag;
    }

    @Override
    public NonNullList<ItemStack> getInputs(ItemStack res, NonNullList<ItemStack> def) {
        if (req == null)
            return def;
        try {
            disableLP = true;
            OpResult r = req.simulateRequest(res, 0b0001, true);
            NonNullList<ItemStack> ret = NonNullList.create();
            ret.addAll(r.missing);
            ret.add(LogisticsBridge.fakeStack(res, 1));
            return ret;
        } finally {
            disableLP = false;
        }
    }

    @Override
    public NonNullList<ItemStack> getOutputs(ItemStack res, NonNullList<ItemStack> def) {
        if (req == null)
            return def;
        try {
            disableLP = true;
            OpResult r = req.simulateRequest(res, 0b0110, true);
            NonNullList<ItemStack> ret = NonNullList.create();
            if (def != null || bridgeMode)
                ret.add(res.copy());
            if (!bridgeMode)
                r.extra.forEach(i -> {
                    boolean added = false;
                    for (ItemStack e : ret)
                        if (itemsEquals(e, i)) {
                            e.grow(i.getCount());
                            added = true;
                            break;
                        }
                    if (!added)
                        ret.add(i);
                });
            if (def == null && !bridgeMode) {
                for (ItemStack e : ret)
                    if (itemsEquals(e, res))
                        res.grow(e.getCount());
                return null;
            }
            return ret;
        } finally {
            disableLP = false;
        }
    }

    private boolean itemsEquals(ItemStack e, ItemStack i) {
        return ItemStack.areItemsEqual(e, i) && ItemStack.areItemStackTagsEqual(e, i);
    }

    public void blockClicked(EntityPlayer playerIn) {
        firstTick = true;
        if (playerIn.isSneaking()) {
            bridgeMode = !bridgeMode;
            TextComponentTranslation text = new TextComponentTranslation("chat.logisticsbridge.bridgeMode", "RS",
                    (bridgeMode ? new TextComponentTranslation("chat.logisticsbridge.bridgeMode.simple") :
                            new TextComponentTranslation("chat.logisticsbridge.bridgeMode.smart")
                    ));
            playerIn.sendMessage(text);
        }
    }

    public boolean pushPattern(ItemStack stack, boolean simulate) {
        if (req == null || !stack.hasTagCompound()) return false;
        ItemStack res = new ItemStack(stack.getTagCompound());
        if (res.isEmpty())
            return false;
        if (!simulate)
            requestList.add(res);
        return true;
    }

    @Override
    protected void onConnectedStateChange(INetwork network, boolean state) {
        super.onConnectedStateChange(network, state);

        network.getCraftingManager().rebuild();
    }

    @Override
    public void onDisconnected(INetwork network) {
        super.onDisconnected(network);

        network.getCraftingManager().getTasks().stream()
                .filter(task -> task.getPattern().getContainer().getPosition().equals(pos))
                .forEach(task -> network.getCraftingManager().cancel(task.getId()));
    }
}
