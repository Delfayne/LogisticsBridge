package org.dv.minecraft.logisticsbridge.pipe;

import org.dv.minecraft.logisticsbridge.GuiHandler.GuiIDs;
import org.dv.minecraft.logisticsbridge.LogisticsBridge;
import org.dv.minecraft.logisticsbridge.network.SetIDPacket;
import org.dv.minecraft.logisticsbridge.network.SetIDPacket.IIdPipe;
import org.dv.minecraft.logisticsbridge.network.SyncResultNamePacket;
import org.dv.minecraft.logisticsbridge.pipe.CraftingManager.OriginalCrafterInfo;
import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.routing.*;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.LogisticsModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.upgrades.UpgradeManager;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.LogisticsDictPromise;
import logisticspipes.routing.LogisticsExtraDictPromise;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsItemOrderManager;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.BufferMode;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import network.rs485.logisticspipes.SatellitePipe;
import network.rs485.logisticspipes.connection.LPNeighborTileEntityKt;
import network.rs485.logisticspipes.connection.NeighborTileEntity;
import network.rs485.logisticspipes.inventory.IItemIdentifierInventory;
import network.rs485.logisticspipes.property.InventoryProperty;

import javax.annotation.Nonnull;
import java.lang.ref.WeakReference;
import java.util.*;

public class ResultPipe extends CoreRoutedPipe implements IIdPipe, IProvideItems, IChangeListener, SatellitePipe {

    public static final Set<ResultPipe> AllResults = Collections.newSetFromMap(new WeakHashMap<>());
    public static TextureType texture = Textures.empty;
    public final PlayerCollectionList localModeWatchers = new PlayerCollectionList();
    private String resultPipeName = "";
    private WeakReference<TileEntity> lastAccessedCrafter = new WeakReference<>(null);

    public ResultPipe(Item item) {
        super(item);
        _orderItemManager = new LogisticsItemOrderManager(this, this);
    }

    // called only on server shutdown
    public static void cleanup() {
        AllResults.clear();
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Normal;
    }

    @Override
    public TextureType getCenterTexture() {
        return texture;
    }

    @Override
    public LogisticsModule getLogisticsModule() {
        return null;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        if (nbttagcompound.hasKey("resultid")) {
            resultPipeName = Integer.toString(nbttagcompound.getInteger("resultid"));
        } else {
            resultPipeName = nbttagcompound.getString("resultname");
        }

        if (MainProxy.isServer(getWorld())) {
            ensureAllSatelliteStatus();
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        if (resultPipeName != null) nbttagcompound.setString("resultname", resultPipeName);
        super.writeToNBT(nbttagcompound);
    }

    public void ensureAllSatelliteStatus() {
        if (resultPipeName.isEmpty()) {
            ResultPipe.AllResults.remove(this);
        }
        if (!resultPipeName.isEmpty()) {
            ResultPipe.AllResults.add(this);
        }
    }

    @Override
    public void onAllowedRemoval() {
        if (MainProxy.isClient(getWorld())) {
            return;
        }

        ResultPipe.AllResults.remove(this);

        while (_orderItemManager.hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            _orderItemManager.sendFailed();
        }
    }

    @Override
    public void onWrenchClicked(EntityPlayer entityplayer) {
        // Send the satellite id when opening gui
        final ModernPacket packet = PacketHandler.getPacket(SyncResultNamePacket.class).setString(resultPipeName).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
        MainProxy.sendPacketToPlayer(packet, entityplayer);
        entityplayer.openGui(LogisticsBridge.modInstance, GuiIDs.RESULT_PIPE.ordinal(), getWorld(), getX(), getY(), getZ());
    }

    @Override
    public void setPipeID(int fid, String integer, EntityPlayer player) {
        if (player == null) {
            final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(integer).setId(fid).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
            MainProxy.sendPacketToServer(packet);
        } else if (MainProxy.isServer(player.world)) {
            final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(integer).setId(fid).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
            MainProxy.sendPacketToPlayer(packet, player);
        }
        this.resultPipeName = integer;
        ensureAllSatelliteStatus();
    }

    @Override
    public String getPipeID(int fid) {
        return resultPipeName;
    }

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filter) {
        System.out.println("ResultPipe.canProvide()");
    }

    @Override
    public LogisticsItemOrder fullFill(LogisticsPromise promise, IRequestItems destination, IAdditionalTargetInformation info) {
        if (promise instanceof LogisticsExtraDictPromise) {
            getItemOrderManager().removeExtras(((LogisticsExtraDictPromise) promise).getResource());
        }
        if (promise instanceof LogisticsExtraPromise) {
            getItemOrderManager()
                    .removeExtras(new DictResource(new ItemIdentifierStack(promise.item, promise.numberOfItems), null));
        }
        if (promise instanceof LogisticsDictPromise) {
            spawnParticle(Particles.WhiteParticle, 2);
            return getItemOrderManager().addOrder(((LogisticsDictPromise) promise)
                    .getResource(), destination, ResourceType.CRAFTING, info);
        }
        spawnParticle(Particles.WhiteParticle, 2);
        return getItemOrderManager()
                .addOrder(new ItemIdentifierStack(promise.item, promise.numberOfItems), destination, ResourceType.CRAFTING, info);
    }

    @Override
    public void enabledUpdateEntity() {
        if (getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            if (isNthTick(6)) {
                cacheAreAllOrderesToBuffer();
            }
            if (getItemOrderManager().isFirstOrderWatched()) {
                TileEntity tile = lastAccessedCrafter.get();
                if (tile != null) {
                    getItemOrderManager().setMachineProgress(SimpleServiceLocator.machineProgressProvider.getProgressForTile(tile));
                } else {
                    getItemOrderManager().setMachineProgress((byte) 0);
                }
            }
        }

        if (!isNthTick(6)) {
            return;
        }

        if ((!getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA))) {
            return;
        }

        final List<NeighborTileEntity<TileEntity>> adjacentInventories = getAvailableAdjacent().inventories();

        if (adjacentInventories.isEmpty()) {
            if (getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
                getItemOrderManager().sendFailed();
            }
            return;
        }

        spawnParticle(Particles.VioletParticle, 2);

        int il = itemsToExtract();
        int sl = stacksToExtract();
        while (il > 0 && sl > 0 && (getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA))) {
            LogisticsItemOrder nextOrder = getItemOrderManager().peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA); // fetch but not remove.
            int mts = Math.min(il, nextOrder.getResource().stack.getStackSize());
            mts = Math.min(nextOrder.getResource().getItem().getMaxStackSize(), mts);
            // retrieve the new crafted items
            ItemStack extracted = ItemStack.EMPTY;
            NeighborTileEntity<TileEntity> adjacent = null; // there has to be at least one adjacentCrafter at this point; adjacent wont stay null
            for (NeighborTileEntity<TileEntity> adjacentCrafter : adjacentInventories) {
                adjacent = adjacentCrafter;
                extracted = extract(adjacent, nextOrder.getResource(), mts);
                if (!extracted.isEmpty()) {
                    break;
                }
            }
            if (extracted.isEmpty()) {
                getItemOrderManager().deferSend();
                break;
            }
            getCacheHolder().trigger(CacheTypes.Inventory);
            Objects.requireNonNull(adjacent);
            lastAccessedCrafter = new WeakReference<>(adjacent.getTileEntity());
            // send the new crafted items to the destination
            ItemIdentifier extractedID = ItemIdentifier.get(extracted);
            while (!extracted.isEmpty()) {
                if (isExtractedMismatch(nextOrder, extractedID)) {
                    LogisticsItemOrder startOrder = nextOrder;
                    if (getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
                        do {
                            getItemOrderManager().deferSend();
                            nextOrder = getItemOrderManager().peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA);
                        } while (isExtractedMismatch(nextOrder, extractedID) && startOrder != nextOrder);
                    }
                    if (startOrder == nextOrder) {
                        int nts = Math.min(extracted.getCount(), extractedID.getMaxStackSize());
                        if (nts == 0) {
                            break;
                        }
                        sl -= 1;
                        il -= nts;
                        ItemStack stackToSend = extracted.splitStack(nts);
                        //Route the unhandled item

                        sendStack(stackToSend, -1, ItemSendMode.Normal, null, adjacent.getDirection());
                        continue;
                    }
                }
                int nts = Math.min(extracted.getCount(), extractedID.getMaxStackSize());
                nts = Math.min(nts, nextOrder.getResource().stack.getStackSize());
                if (nts == 0) {
                    break;
                }
                sl -= 1;
                il -= nts;
                ItemStack stackToSend = extracted.splitStack(nts);
                if (nextOrder.getDestination() != null) {
                    SinkReply reply = LogisticsManager.canSink(stackToSend, nextOrder.getDestination().getRouter(), null, true, ItemIdentifier.get(stackToSend), null, true, false);
                    boolean ds = reply == null || reply.bufferMode != BufferMode.NONE || reply.maxNumberOfItems < 1;
                    IRoutedItem item = SimpleServiceLocator.routedItemHelper.createNewTravelItem(stackToSend);
                    item.setDestination(nextOrder.getDestination().getRouter().getSimpleID());
                    item.setTransportMode(TransportMode.Active);
                    item.setAdditionalTargetInformation(OriginalCrafterInfo.unwrap(nextOrder.getInformation(), true));
                    queueRoutedItem(item, adjacent.getDirection());
                    getItemOrderManager().sendSuccessfull(stackToSend.getCount(), ds, item);
                } else {
                    sendStack(stackToSend, -1, ItemSendMode.Normal, OriginalCrafterInfo.unwrap(nextOrder.getInformation(), true), adjacent.getDirection());
                    getItemOrderManager().sendSuccessfull(stackToSend.getCount(), false, null);
                }
                if (getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
                    nextOrder = getItemOrderManager().peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA); // fetch but not remove.
                }
            }
        }

    }

    private boolean isExtractedMismatch(LogisticsItemOrder nextOrder, ItemIdentifier extractedID) {
        return !nextOrder.getResource().getItem().equals(extractedID) && (!getUpgradeManager().isFuzzyUpgrade() || (nextOrder.getResource().getBitSet().nextSetBit(0) == -1) || !nextOrder.getResource().matches(extractedID, IResource.MatchSettings.NORMAL));
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) { }

    @Override
    public void listenedChanged() { }

    public boolean cacheAreAllOrderesToBuffer() {
        for (LogisticsItemOrder order : getItemOrderManager()) {
            if (order.getDestination() instanceof IItemSpaceControl) {
                SinkReply reply = LogisticsManager.canSink(order.getResource().getItemStack().makeNormalStack(), order.getDestination().getRouter(), null, true, order.getResource().getItem(), null, true, false);
                if (reply == null || reply.bufferMode != BufferMode.NONE || reply.maxNumberOfItems < 1)
                    continue;
            }

            return false;
        }

        return true;
    }

    protected int neededEnergy() {
        return (int) (10 * Math.pow(1.1, getUpgradeManager().getItemExtractionUpgrade()) * Math.pow(1.2, getUpgradeManager().getItemStackExtractionUpgrade()));
    }

    protected int itemsToExtract() {
        return (int) Math.pow(2, getUpgradeManager().getItemExtractionUpgrade());
    }

    protected int stacksToExtract() {
        return 1 + getUpgradeManager().getItemStackExtractionUpgrade();
    }

    @Override
    public UpgradeManager getUpgradeManager() {
        return upgradeManager;
    }

    @Nonnull
    private ItemStack extract(NeighborTileEntity<TileEntity> adjacent, IResource item, int amount) {
        final IInventoryUtil invUtil = LPNeighborTileEntityKt.getInventoryUtil(adjacent);
        if (invUtil == null) return ItemStack.EMPTY;
        return extractFromInventory(invUtil, item, amount);
    }

    @Nonnull
    private ItemStack extractFromInventory(@Nonnull IInventoryUtil invUtil, IResource wanteditem, int count) {
        ItemIdentifier itemToExtract = null;
        if (wanteditem instanceof ItemResource)
            itemToExtract = ((ItemResource) wanteditem).getItem();
        else if (wanteditem instanceof DictResource) {
            int max = Integer.MIN_VALUE;
            ItemIdentifier toExtract = null;
            for (Map.Entry<ItemIdentifier, Integer> content : invUtil.getItemsAndCount().entrySet())
                if (wanteditem.matches(content.getKey(), IResource.MatchSettings.NORMAL) && content.getValue() > max) {
                    max = content.getValue();
                    toExtract = content.getKey();
                }
            if (toExtract == null)
                return ItemStack.EMPTY;
            itemToExtract = toExtract;
        }
        if (itemToExtract == null)
            return ItemStack.EMPTY;
        int available = invUtil.itemCount(itemToExtract);
        if (available == 0 || !canUseEnergy(neededEnergy() * Math.min(count, available)))
            return ItemStack.EMPTY;
        ItemStack extracted = invUtil.getMultipleItems(itemToExtract, Math.min(count, available));
        useEnergy(neededEnergy() * extracted.getCount());
        return extracted;
    }

    @Nonnull
    private ItemStack extractFromInventoryFiltered(@Nonnull IInventoryUtil invUtil, IItemIdentifierInventory filter, boolean isExcluded, int filterInvLimit) {
        ItemIdentifier wi = null;
        boolean found = false;
        for (ItemIdentifier item : invUtil.getItemsAndCount().keySet()) {
            found = isFiltered(filter, filterInvLimit, item, found);
            if (isExcluded != found) {
                wi = item;
                break;
            }
        }
        if (wi == null)
            return ItemStack.EMPTY;
        int available = invUtil.itemCount(wi);
        if (available == 0 || !canUseEnergy(neededEnergy() * Math.min(64, available)))
            return ItemStack.EMPTY;
        ItemStack extracted = invUtil.getMultipleItems(wi, Math.min(64, available));
        useEnergy(neededEnergy() * extracted.getCount());
        return extracted;
    }

    private boolean isFiltered(IItemIdentifierInventory filter, int filterInvLimit, ItemIdentifier item, boolean found) {
        for (int i = 0; i < filter.getSizeInventory() && i < filterInvLimit; i++) {
            ItemIdentifierStack identStack = filter.getIDStackInSlot(i);
            if (identStack != null && identStack.getItem().equalsWithoutNBT(item)) {
                found = true;
                break;
            }
        }

        return found;
    }

    @Override
    public String getName(int id) {
        return "gui.resultPipe.id";
    }

    public void registerExtras(IPromise promise) {
        if (promise instanceof LogisticsDictPromise)
            getItemOrderManager().addExtra(((LogisticsDictPromise) promise).getResource());
        else {
            ItemIdentifierStack stack = new ItemIdentifierStack(promise.getItemType(), promise.getAmount());
            getItemOrderManager().addExtra(new DictResource(stack, null));
        }
    }

    public String getResultPipeName() {
        return resultPipeName;
    }

    public void extractCleanup(InventoryProperty cleanupInventory, boolean cleanupModeIsExclude, int i) {
        final List<NeighborTileEntity<TileEntity>> adjacentInventories = getAvailableAdjacent().inventories();

        if (!getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            final ISlotUpgradeManager upgradeManager = Objects.requireNonNull(getUpgradeManager());
            if (upgradeManager.getCrafterCleanup() > 0)
                adjacentInventories.stream()
                        .map(neighbor -> extractFiltered(neighbor, cleanupInventory, cleanupModeIsExclude,
                                upgradeManager.getCrafterCleanup() * 3)).filter(stack -> !stack.isEmpty()).findFirst()
                        .ifPresent(extracted -> {
                            queueRoutedItem(
                                    SimpleServiceLocator.routedItemHelper.createNewTravelItem(extracted),
                                    EnumFacing.UP);
                            getCacheHolder().trigger(CacheTypes.Inventory);
                        });
        }
    }

    @Nonnull
    private ItemStack extractFiltered(NeighborTileEntity<TileEntity> neighbor, IItemIdentifierInventory inv, boolean isExcluded, int filterInvLimit) {
        final IInventoryUtil invUtil = LPNeighborTileEntityKt.getInventoryUtil(neighbor);
        if (invUtil == null)
            return ItemStack.EMPTY;
        return extractFromInventoryFiltered(invUtil, inv, isExcluded, filterInvLimit);
    }

    @Override
    public void playerStartWatching(EntityPlayer player, int mode) {
        if (mode == 1) {
            localModeWatchers.add(player);
            final ModernPacket packet = PacketHandler.getPacket(SyncResultNamePacket.class).setString((this).resultPipeName).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
            MainProxy.sendPacketToPlayer(packet, player);
        } else
            super.playerStartWatching(player, mode);
    }

    @Override
    public void playerStopWatching(EntityPlayer player, int mode) {
        super.playerStopWatching(player, mode);
        localModeWatchers.remove(player);
    }

    @Override
    public List<ItemIdentifierStack> getItemList() {
        return Collections.emptyList();
    }

    @Override
    public String getSatellitePipeName() {
        return resultPipeName;
    }

    @Override
    public void setSatellitePipeName(@Nonnull String s) {
        this.resultPipeName = s;
    }

    @Override
    public Set<SatellitePipe> getSatellitesOfType() {
        return Collections.unmodifiableSet(AllResults);
    }

    @Override
    public void updateWatchers() {
        CoordinatesPacket packet = PacketHandler.getPacket(SyncResultNamePacket.class).setString(resultPipeName).setTilePos(this.getContainer());
        MainProxy.sendToPlayerList(packet, localModeWatchers);
        MainProxy.sendPacketToAllWatchingChunk(this.getContainer(), packet);
    }
}
