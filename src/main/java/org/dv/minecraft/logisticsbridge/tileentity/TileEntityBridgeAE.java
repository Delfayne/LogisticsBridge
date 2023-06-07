package org.dv.minecraft.logisticsbridge.tileentity;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.crafting.CraftingLink;
import appeng.me.GridAccessException;
import appeng.me.storage.MEMonitorIInventory;
import appeng.tile.grid.AENetworkInvTile;
import appeng.util.inv.IMEAdaptor;
import appeng.util.inv.InvOperation;
import com.google.common.collect.ImmutableSet;
import org.dv.minecraft.logisticsbridge.AE2Plugin;
import org.dv.minecraft.logisticsbridge.LB_ItemStore;
import org.dv.minecraft.logisticsbridge.LogisticsBridge;
import org.dv.minecraft.logisticsbridge.api.BridgeStack;
import org.dv.minecraft.logisticsbridge.api.IDynamicPatternDetailsAE;
import org.dv.minecraft.logisticsbridge.item.VirtualPatternAE;
import org.dv.minecraft.logisticsbridge.pipe.BridgePipe.OpResult;
import org.dv.minecraft.logisticsbridge.pipe.BridgePipe.Req;
import org.dv.minecraft.logisticsbridge.util.DynamicInventory;
import org.dv.minecraft.logisticsbridge.util.TileProfiler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TileEntityBridgeAE extends AENetworkInvTile implements IGridHost, ITickable, IActionSource,
        ICraftingRequester, ICellContainer, ICraftingProvider, ICraftingCallback, IMEInventoryHandler<IAEItemStack>,
        IItemHandlerModifiable, IDynamicPatternDetailsAE, IBridge {
    //private MEMonitorIInventory intInv = new MEMonitorIInventory(new AdaptorItemHandler(wrapper));
    private static final IItemStorageChannel ITEMS = AE2Plugin.INSTANCE.api.storage().getStorageChannel(IItemStorageChannel.class);
    private static final IAEItemStack[] OVERFLOW;

    static {
        ItemStack is = new ItemStack(Blocks.BARRIER);
        is.setTranslatableName("tooltip.logisticsbridge.smartModeOverflow");
        OVERFLOW = new IAEItemStack[]{ITEMS.createStack(is)};
    }

    private final IAEItemStack fakeItem = ITEMS.createStack(new ItemStack(LB_ItemStore.logisticsFakeItem, 1));
    boolean readingFromNBT;
    private final Optional<IActionHost> machine = Optional.of(this);
    private final Set<ICraftingLink> links = new HashSet<>();
    private final Set<ICraftingPatternDetails> craftings = new HashSet<>();
    private Req req;
    private final MEMonitorIInventory meInv = new MEMonitorIInventory(new IMEAdaptor(this, this));
    @SuppressWarnings("rawtypes")
    private final List<IMEInventoryHandler> cellArray = Collections.singletonList(this);
    private final DynamicInventory dynInv = new DynamicInventory();
    private final InvWrapper wrapper = new InvWrapper(dynInv);
    private final IItemList<IAEItemStack> fakeItems = ITEMS.createList();
    private final Map<ItemStack, Integer> toCraft = new HashMap<>();
    private final List<ItemStack> insertingStacks = new ArrayList<>();
    private long lastInjectTime = -1;
    private OpResult lastPush;
    private long lastPushTime;
    private boolean disableLP;
    private boolean bridgeMode;
    private final TileProfiler profiler = new TileProfiler("AE Bridge");

    public TileEntityBridgeAE() {
        getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @Override
    public void update() {
        if (!world.isRemote) {
            try {
                long wt = world.getTotalWorldTime();
                profiler.startProfiling();
                if (wt % 40 == 0 && this.getProxy().getNode() != null) {
                    profiler.startSection("Net update (every 40 ticks)");
                    profiler.startSection("Tick AE Inv");
                    boolean changed = meInv.onTick() == TickRateModulation.URGENT;
                    if (changed) {
                        profiler.endStartSection("Refresh AE item list");
                        this.getProxy().getNode().getGrid().postEvent(new MENetworkCellArrayUpdate());
                    }
                    if (req != null) {
                        profiler.endStartSection("Detect crafting changes");
                        changed = req.detectChanged();
                        if (changed) {
                            profiler.endStartSection("Refresh AE");
                            this.getProxy().getNode().getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.getProxy().getNode()));
                        }
                    }
                    profiler.endStartSection("Updating queued crafting");
                    synchronized (toCraft) {
                        toCraft.forEach((key, value) -> craftStack(key, value, false));
                        toCraft.clear();
                    }
                    dynInv.removeEmpties();
                    profiler.endSection();
                    profiler.endSection();
                }
                if (lastInjectTime == -1) lastInjectTime = wt;
                else {
                    if (lastInjectTime + 200 < wt && this.getProxy().getNode() != null && !dynInv.isEmpty()) {
                        profiler.startSection("Empting internal inventory");
                        emptyInternalInventory();
                        profiler.endSection();
                    }
                    profiler.startSection("Emitting fake items");
                    try {
                        ICraftingGrid cg = this.getProxy().getGrid().getCache(ICraftingGrid.class);
                        if (cg.isRequesting(fakeItem)) {
                            insertItem(0, LogisticsBridge.fakeStack(1), false);
                        }
                    } catch (GridAccessException ignored) { }
                    profiler.endSection();
                }
                profiler.finishProfiling();
            } catch (RuntimeException e) {
                e.addSuppressed(new RuntimeException("Profiler stage: " + profiler.lastSection));
                throw e;
            }
        }
    }

    private void emptyInternalInventory() {
        lastInjectTime = world.getTotalWorldTime() - 20;
        for (int i = 0; i < dynInv.getSizeInventory(); i++) {
            dynInv.setInventorySlotContents(i, insertItem(0, dynInv.getStackInSlot(i), false));
        }
        dynInv.removeEmpties();
    }

    @Override
    public AECableType getCableConnectionType(AEPartLocation aePartLocation) {
        return AECableType.SMART;
    }

    @Override
    public List<BridgeStack<ItemStack>> getItems() {
        if (this.getProxy().getNode() == null || (disableLP && !bridgeMode)) return Collections.emptyList();
        profiler.startSection("getItems");
        IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
        IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
        IItemList<IAEItemStack> items = i.getAvailableItems(ITEMS.createList());
        List<BridgeStack<ItemStack>> list = Stream.concat(
                StreamSupport.stream(Spliterators.spliteratorUnknownSize(items.iterator(), Spliterator.ORDERED), false).
                        map(ae -> new BridgeStack<>(ae.asItemStackRepresentation(), ae.getStackSize(), ae.isCraftable(), ae.getCountRequestable())).
                        filter(s -> s.obj.getItem() != LB_ItemStore.logisticsFakeItem && s.obj.getItem() != LB_ItemStore.packageItem),

                Stream.concat(insertingStacks.stream(), dynInv.stream())
                        .map(s -> new BridgeStack<>(s, s.getCount(), false, 0))
        ).collect(Collectors.toList());
        profiler.endSection();
        return list;
    }

    @Override
    public long countItem(ItemStack stack, boolean requestable) {
        if (this.getProxy().getNode() == null || (disableLP && !bridgeMode)) return 0;
        profiler.startSection("countItems");
        int buffered = 0;
        for (int i = 0; i < dynInv.getSizeInventory(); i++) {
            ItemStack is = dynInv.getStackInSlot(i);
            if (ItemStack.areItemsEqual(stack, is) && ItemStack.areItemStackTagsEqual(stack, is)) {
                buffered += is.getCount();
            }
        }
        for (ItemStack is : insertingStacks) {
            if (ItemStack.areItemsEqual(stack, is) && ItemStack.areItemStackTagsEqual(stack, is)) {
                buffered += is.getCount();
            }
        }
        IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
        IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
        IItemList<IAEItemStack> items = i.getAvailableItems(ITEMS.createList());
        IAEItemStack is = items.findPrecise(ITEMS.createStack(stack));
        if (is == null) {
            profiler.endSection();
            return buffered;
        }
        long result = requestable ? is.getStackSize() + is.getCountRequestable() : is.getStackSize() + buffered;
        profiler.endSection();
        return result;
    }

    @Override
    public ItemStack extractStack(ItemStack stack, int count, boolean simulate) {
        if (this.getProxy().getNode() == null) return ItemStack.EMPTY;
        for (int i = 0; i < dynInv.getSizeInventory(); i++) {
            ItemStack is = dynInv.getStackInSlot(i);
            if (ItemStack.areItemsEqual(stack, is) && ItemStack.areItemStackTagsEqual(stack, is)) {
                return wrapper.extractItem(i, count, simulate);
            }
        }
        IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
        IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
        IAEItemStack st = ITEMS.createStack(stack);
        st.setStackSize(count);
        IAEItemStack is = i.extractItems(st, simulate ? Actionable.SIMULATE : Actionable.MODULATE, this);
        return is == null ? ItemStack.EMPTY : is.createItemStack();
    }

    @Override
    public void craftStack(ItemStack stack, int count, boolean simulate) {
        if (this.getProxy().getNode() == null)
            return;
        IAEItemStack st = ITEMS.createStack(stack);
        st.setStackSize(count);
        ((ICraftingGrid) this.getProxy().getNode().getGrid().getCache(ICraftingGrid.class)).beginCraftingJob(world, this.getProxy().getNode().getGrid(), this, st, this);
    }

    @Override
    public Optional<EntityPlayer> player() {
        return Optional.empty();
    }

    @Override
    public Optional<IActionHost> machine() {
        return machine;
    }

    @Override
    public <T> Optional<T> context(@Nonnull Class<T> key) {
        return Optional.empty();
    }

    @Override
    public IGridNode getActionableNode() {
        return this.getProxy().getNode();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<IMEInventoryHandler> getCellArray(IStorageChannel<?> channel) {
        return channel == ITEMS ? cellArray : Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void blinkCell(int slot) {
    }

    @Override
    public void calculationComplete(@Nonnull ICraftingJob job) {
        if (this.getProxy().getNode() == null) return;
        if (job.isSimulation()) return;
        ICraftingGrid g = this.getProxy().getNode().getGrid().getCache(ICraftingGrid.class);
        ICraftingLink link = g.submitJob(job, this, null, false, this);
        if (link == null) {
            synchronized (toCraft) {
                toCraft.put(job.getOutput().asItemStackRepresentation(), (int) job.getOutput().getStackSize());
            }
        }
        if (link != null) links.add(link);
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return ImmutableSet.copyOf(links);
    }

    @Override
    public IAEItemStack injectCraftedItems(ICraftingLink link, IAEItemStack items, Actionable mode) {
        if (mode == Actionable.MODULATE) {
            lastInjectTime = world.getTotalWorldTime();
            dynInv.insert(items.createItemStack());
        }
        return null;
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        links.remove(link);
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList lst = new NBTTagList();
        compound.setTag("links", lst);
        links.stream().filter(Objects::nonNull).map(l -> {
            NBTTagCompound t = new NBTTagCompound();
            l.writeToNBT(t);
            return t;
        }).forEach(lst::appendTag);
        NBTTagList lst2 = new NBTTagList();
        compound.setTag("toCraft", lst2);
        toCraft.entrySet().stream().filter(Objects::nonNull).map(l -> {
            NBTTagCompound t = new NBTTagCompound();
            l.getKey().writeToNBT(t);
            t.setInteger("Count", l.getValue());
            return t;
        }).forEach(lst2::appendTag);
        dynInv.removeEmpties();
        compound.setTag("intInventory", LogisticsBridge.saveAllItems(dynInv));
        compound.setBoolean("bridgeMode", bridgeMode);
        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        try {
            readingFromNBT = true;
            super.readFromNBT(compound);
        } finally {
            readingFromNBT = false;
        }
        LogisticsBridge.loadAllItems(compound.getTagList("intInventory", 10), dynInv);
        NBTTagList lst = compound.getTagList("links", 10);
        toCraft.clear();
        links.clear();
        for (int i = 0; i < lst.tagCount(); i++) {
            NBTTagCompound tag = lst.getCompoundTagAt(i);
            links.add(new CraftingLink(tag, this));
        }
        lst = compound.getTagList("toCraft", 10);
        for (int i = 0; i < lst.tagCount(); i++) {
            NBTTagCompound tag = lst.getCompoundTagAt(i);
            int count = tag.getInteger("Count");
            toCraft.put(new ItemStack(tag), count);
        }
        bridgeMode = compound.getBoolean("bridgeMode");
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        if (input.asItemStackRepresentation().getItem() == LB_ItemStore.logisticsFakeItem) return null;
        return input;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable type, IActionSource src) {
        if (request.createItemStack().getItem() == LB_ItemStore.logisticsFakeItem) {
            IAEItemStack stack = fakeItems.findPrecise(request);
            if (stack == null) return null;
            long min = Math.min(stack.getStackSize(), request.getStackSize());
            IAEItemStack ret = stack.copy();
            ret.setStackSize(min);
            if (type == Actionable.MODULATE) stack.decStackSize(min);
            return ret;
        }

        return null;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        if (req == null) return out;
		/*if(!updatingAECache) {
			updatingAECache = true;
			meInv.getAvailableItems(out);
			updatingAECache = false;
			return out;
		}*/
        profiler.startSection("getAvailableItems");
        craftings.clear();
        fakeItems.resetStatus();
        try {
            if (Loader.instance().getLoaderState() == LoaderState.SERVER_STOPPING) return out;
            profiler.startSection("List LP");
            List<ItemStack> pi = req.getProvidedItems();
            List<ItemStack> ci = req.getCraftedItems();
            profiler.endStartSection("Wrap AE");
            pi.stream().map(ITEMS::createStack).filter(Objects::nonNull).peek(s -> {
                s.setCraftable(true);
                s.setCountRequestable(s.getStackSize());
                s.setStackSize(0);
            }).forEach(out::add);
            ci.stream().map(ITEMS::createStack).forEach(out::addCrafting);
            TileEntityWrapper wr = new TileEntityWrapper(this);
            profiler.endStartSection("Wrap VP item");
            pi.stream().map(i -> {
                ItemStack r = i.copy();
                r.setCount(1);
                fakeItems.add(ITEMS.createStack(LogisticsBridge.fakeStack(r, i.getCount())));
                return VirtualPatternAE.create(ITEMS.createStack(r), wr);
            }).forEach(craftings::add);
            profiler.endStartSection("Wrap VP craft");
            ci.stream().map(i -> VirtualPatternAE.create(ITEMS.createStack(i), wr)).forEach(craftings::add);
            profiler.endStartSection("Mount Fake Items");
            fakeItems.forEach(out::addStorage);
            profiler.endSection();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            profiler.endSection();
        }
        return out;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return ITEMS;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        return input.asItemStackRepresentation().getItem() == LB_ItemStore.logisticsFakeItem;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        return input.asItemStackRepresentation().getItem() == LB_ItemStore.logisticsFakeItem;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return i == 1;
    }

    @Override
    public int getSlots() {
        return wrapper.getSlots() + 1;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return wrapper.getStackInSlot(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (this.getProxy().getNode() == null || stack.isEmpty()) return stack;
        IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
        IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
        IAEItemStack st = ITEMS.createStack(stack);
        IAEItemStack r = i.injectItems(st, simulate ? Actionable.SIMULATE : Actionable.MODULATE, this);
        return r == null ? ItemStack.EMPTY : r.createItemStack();
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return wrapper.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        wrapper.setStackInSlot(slot, stack);
    }

    @Override
    public void saveChanges(ICellInventory<?> arg0) { }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        if (req == null) return false;
        insertingStacks.clear();
        for (int i = 0; i < table.getSizeInventory(); i++) {
            if (table.getStackInSlot(i).getItem() != LB_ItemStore.logisticsFakeItem)
                insertingStacks.add(table.getStackInSlot(i));
        }
        OpResult or = req.performRequest(patternDetails.getOutputs()[0].createItemStack(), true);
        boolean pushed = or.missing.isEmpty();
        insertingStacks.clear();
        if (pushed) {
            lastInjectTime = world.getTotalWorldTime();
            for (int i = 0; i < table.getSizeInventory(); i++) {
                ItemStack stack = table.getStackInSlot(i);
                if (stack.getItem() != LB_ItemStore.logisticsFakeItem)
                    dynInv.insert(stack.copy());
            }
        } else {
            lastPush = or;
            lastPushTime = System.currentTimeMillis();
        }
        return pushed;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        craftings.forEach(c -> craftingTracker.addCraftingOption(this, c));
        craftingTracker.setEmitable(fakeItem);
    }

    @Override
    public IAEItemStack[] getInputs(ItemStack res, IAEItemStack[] def, boolean condensed) {
        if (req == null) return def;
        try {
            disableLP = true;
            boolean craftable = req.getCraftedItems().stream().anyMatch(s -> res.isItemEqual(s));
            OpResult r = req.simulateRequest(res, 0b0001, true);
            IAEItemStack[] inputs = Stream.concat(r.missing.stream(), Stream.of(LogisticsBridge.fakeStack(craftable ? null : res, 1))).map(ITEMS::createStack).toArray(IAEItemStack[]::new);
            if (inputs.length > 9) {
                return OVERFLOW;
            }
            return inputs;
        } finally {
            disableLP = false;
        }
    }

    @Override
    public IAEItemStack[] getOutputs(ItemStack res, IAEItemStack[] def, boolean condensed) {
        if (req == null || !req.isDefaultRoute() || Loader.instance().getLoaderState() == LoaderState.SERVER_STOPPING)
            return def;
        try {
            disableLP = true;
            OpResult r = req.simulateRequest(res, 0b0110, true);
            List<IAEItemStack> ret = new ArrayList<>();
            IAEItemStack resAE = ITEMS.createStack(res);
			/*if(def != null)
				for (int i = 0; i < def.length; i++) ret.add(def[i].copy());*/
            if (def != null || bridgeMode) ret.add(resAE.copy());
            if (!bridgeMode) {
                r.extra.forEach(i -> {
                    IAEItemStack is = ITEMS.createStack(i);
                    boolean added = false;
                    for (IAEItemStack e : ret) {
                        if (e.equals(is)) {
                            e.add(is);
                            added = true;
                            break;
                        }
                    }
                    if (!added) ret.add(is);
                });
            }
            if (def == null && !bridgeMode) {
                for (IAEItemStack e : ret) {
                    if (e.equals(resAE)) {
                        res.setCount(((int) e.getStackSize()) + res.getCount());
                    }
                }
                return new IAEItemStack[] {};
            }
            return ret.toArray(new IAEItemStack[ret.size()]);
        } finally {
            disableLP = false;
        }
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Nonnull
    @Override
    public IItemHandler getInternalInventory() {
        return readingFromNBT ? EmptyHandler.INSTANCE : this;
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) { }

    public String infoString() {
        StringBuilder b = new StringBuilder();
        if (disableLP) b.append("  disableLP flag is stuck\n");
        b.append("Mode: ");
        b.append(bridgeMode ? "Simple Mode" : "Smart Mode");
        b.append('\n');
        if (lastPush != null) b.append("  Missing items:\n");
        return b.toString();
    }

    public void blockClicked(EntityPlayer playerIn) {
        if (playerIn.isSneaking()) {
            bridgeMode = !bridgeMode;
            TextComponentTranslation text = new TextComponentTranslation("chat.logisticsbridge.bridgeMode", "AE",
                    (bridgeMode ? new TextComponentTranslation("chat.logisticsbridge.bridgeMode.simple") :
                            new TextComponentTranslation("chat.logisticsbridge.bridgeMode.smart")
                    ));
            playerIn.sendMessage(text);
        } else {
            if (playerIn.getHeldItemMainhand().getItem() == Items.STICK) {
                if (profiler.resultPlayer != null) return;
                TextComponentString text = new TextComponentString("Bridge diagnostics started, testing for 200 ticks (10 sec)...");
                playerIn.sendMessage(text);
                profiler.setResultPlayer(playerIn, 200);
            } else {
                String info = infoString();
                if (info.isEmpty()) info = "  No problems";
                TextComponentString text = new TextComponentString("AE Bridge\n" + info);
                if (lastPush != null) {
                    for (ItemStack i : lastPush.missing) {
                        text.appendText("    ");
                        text.appendSibling(i.getTextComponent());
                        text.appendText(" * " + i.getCount() + "\n");
                    }
                    long ago = System.currentTimeMillis() - lastPushTime;
                    text.appendText(String.format("  %1$tH %1$tM,%1$tS ago\n", ago));
                }
                if (dynInv.getSizeInventory() > 0) {
                    text.appendText("\nStored items:\n");
                    for (int i = 0; i < dynInv.getSizeInventory(); i++) {
                        ItemStack is = dynInv.getStackInSlot(i);
                        text.appendText("    ");
                        text.appendSibling(is.getTextComponent());
                        text.appendText(" * " + is.getCount() + "\n");
                    }
                }
                playerIn.sendMessage(text);
            }
        }
    }

    @Override
    public void setReqAPI(Req req) {
        this.req = req;
    }
}
