package com.tom.logisticsbridge.pipe;

import com.tom.logisticsbridge.GuiHandler.GuiIDs;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.module.BufferUpgrade;
import com.tom.logisticsbridge.module.ModuleCrafterExt;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import logisticspipes.LPItems;
import logisticspipes.gui.GuiChassisPipe;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.IPipeServiceProvider;
import logisticspipes.interfaces.IWorldProvider;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.items.ItemModule;
import logisticspipes.logisticspipes.ItemModuleInformationManager;
import logisticspipes.modules.ChassisModule;
import logisticspipes.modules.LogisticsModule;
import logisticspipes.modules.LogisticsModule.ModulePositionType;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.guis.pipe.ChassisGuiProvider;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.pipes.PipeLogisticsChassis;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.IRouter;
import logisticspipes.security.SecuritySettings;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import network.rs485.logisticspipes.connection.LPNeighborTileEntityKt;
import network.rs485.logisticspipes.inventory.IItemIdentifierInventory;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toMap;

public class CraftingManager extends PipeLogisticsChassis implements IIdPipe {
    public static TextureType texture = Textures.empty;
    private String satelliteId;
    private String resultId;
    private final List<List<Pair<UUID, ItemIdentifierStack>>> buffered = new ArrayList<>();
    private UUID satelliteUUID;
    private UUID resultUUID;
    private BlockingMode blockingMode = BlockingMode.OFF;
    private int sendCooldown = 0;

    @SideOnly(Side.CLIENT)
    private IInventory clientInv;

    private boolean readingNBT;
    private boolean canSendNext = true;

    public CraftingManager(Item item) {
        super(item);
    }

    public static boolean isCraftingModule(@Nonnull ItemStack itemStack) {
        return itemStack.getItem() == Item.REGISTRY.getObject(LPItems.modules.get(ModuleCrafter.getName()));
    }

    @Override
    public TextureType getCenterTexture() {
        return texture;
    }

    @Override
    public int getChassisSize() {
        return 27;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void InventoryChanged(IInventory inventory) {
        ChassisModule module = (ChassisModule) getLogisticsModule();
        boolean reInitGui = false;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                if (module.hasModule(i)) {
                    module.removeModule(i);
                    reInitGui = true;
                }
                continue;
            }

            if (stack.getItem() instanceof ItemModule) {
                LogisticsModule next = getModuleForItem(stack, module.getModule(i), this, this);
                next.registerPosition(ModulePositionType.SLOT, i);
                if (module.getModule(i) != next) {
                    module.installModule(i, next);
                    if (!MainProxy.isClient())
                        ItemModuleInformationManager.readInformation(stack, next);
                }
                inventory.setInventorySlotContents(i, stack);
            }
        }

        if (MainProxy.isClient(getWorld())) {
            if (reInitGui && FMLClientHandler.instance().getClient().currentScreen instanceof GuiChassisPipe)
                FMLClientHandler.instance().getClient().currentScreen.initGui();
        } else
            sendSignData();
    }

    private void sendSignData() {
        if (readingNBT)
            return;
        for (int i = 0; i < 6; i++) {
            if (signItem[i] != null) {
                ModernPacket packet = signItem[i].getPacket();
                if (packet != null)
                    MainProxy.sendPacketToAllWatchingChunk(container, packet);
            }
        }
    }

    public LogisticsModule getModuleForItem(@Nonnull ItemStack itemStack, LogisticsModule currentModule, IWorldProvider world, IPipeServiceProvider service) {
        if (!isCraftingModule(itemStack))
            return null;
        if (currentModule != null && ModuleCrafterExt.class.equals(currentModule.getClass()))
            return currentModule;
        ModuleCrafterExt nm = new ModuleCrafterExt();
        nm.registerHandler(world, service);
        return nm;
    }

    @Override
    public IHeadUpDisplayRenderer getRenderer() {
        return null;
    }

    @Override
    public void startWatching() {
    }

    @Override
    public void stopWatching() {
    }

    @Override
    public void playerStartWatching(EntityPlayer player, int mode) {
    }

    @Override
    public void playerStopWatching(EntityPlayer player, int mode) {
    }

    @Override
    public boolean handleClick(EntityPlayer entityplayer, SecuritySettings settings) {
        return handleClick0(entityplayer, settings);
    }

    private boolean handleClick0(EntityPlayer entityplayer, SecuritySettings settings) {
        if (entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).isEmpty()) {
            return false;
        }
        if (!entityplayer.isSneaking() && SimpleServiceLocator.configToolHandler.canWrench(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), container)) {
            if (MainProxy.isServer(getWorld())) {
                if (settings == null || settings.openGui) {
                    openGui(entityplayer);
                } else {
                    entityplayer.sendMessage(new TextComponentTranslation("lp.chat.permissiondenied"));
                }
            }
            SimpleServiceLocator.configToolHandler.wrenchUsed(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), container);
            return true;
        }
        if (entityplayer.isSneaking() && SimpleServiceLocator.configToolHandler.canWrench(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), container)) {
            if (MainProxy.isServer(getWorld())) {
                if (settings == null || settings.openGui) {
                    ((PipeLogisticsChassis) container.pipe).nextOrientation();
                } else {
                    entityplayer.sendMessage(new TextComponentTranslation("lp.chat.permissiondenied"));
                }
            }
            SimpleServiceLocator.configToolHandler.wrenchUsed(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), container);
            return true;
        }

        if (!entityplayer.isSneaking() && entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).getItem() instanceof ItemModule) {
            if (MainProxy.isServer(getWorld())) {
                if (settings == null || settings.openGui) {
                    return tryInsertingModule(entityplayer);
                } else {
                    entityplayer.sendMessage(new TextComponentTranslation("lp.chat.permissiondenied"));
                }
            }
            return true;
        }

        return false;
    }

    private boolean tryInsertingModule(EntityPlayer entityplayer) {
        if (!isCraftingModule(entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND))) return false;
        IInventory mi = getModuleInventory();
        for (int i = 0; i < mi.getSizeInventory(); i++) {
            ItemStack item = mi.getStackInSlot(i);
            if (item.isEmpty()) {
                mi.setInventorySlotContents(i, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).splitStack(1));
                InventoryChanged(mi);
                return true;
            }
        }
        return false;
    }

    public boolean isUpgradeModule(ItemStack itemStack, int slot) {
        return ChassisGuiProvider.checkStack(itemStack, this, slot);
    }

    public void openGui(EntityPlayer entityPlayer) {
        ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class)
                .setName(isBuffered() ? blockingMode.name() : BlockingMode.NULL.name())
                .setId(2)
                .setPosX(getX())
                .setPosY(getY())
                .setPosZ(getZ());
        MainProxy.sendPacketToPlayer(packet, entityPlayer);
        entityPlayer.openGui(LogisticsBridge.modInstance, GuiIDs.CRAFTING_MANAGER.ordinal(), getWorld(), getX(), getY(), getZ());
        packet = PacketHandler.getPacket(SetIDPacket.class).setName(satelliteId).setId(0).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
        MainProxy.sendPacketToPlayer(packet, entityPlayer);
        packet = PacketHandler.getPacket(SetIDPacket.class).setName(resultId).setId(1).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
        MainProxy.sendPacketToPlayer(packet, entityPlayer);
    }

    @Override
    public String getPipeID(int id) {
        if (id == 0)
            return satelliteId;
        if (id == 1)
            return resultId;
        return Integer.toString(blockingMode.customOrdinal);
    }

    @Override
    public void setPipeID(int id, String pipeID, EntityPlayer player) {
        if (player == null) {
            final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
            MainProxy.sendPacketToServer(packet);
        } else if (MainProxy.isServer(player.world)) {
            final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
            MainProxy.sendPacketToPlayer(packet, player);
            satelliteUUID = null;
            resultUUID = null;
        }
        if (id == 0) satelliteId = pipeID;
        else if (id == 1) resultId = pipeID;
        else if (id == 2)
            blockingMode = BlockingMode.valueOf(pipeID);
    }

    @Override
    public void writeToNBT(@Nonnull NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (resultId != null) tag.setString("resultname", resultId);
        if (satelliteId != null) tag.setString("satellitename", satelliteId);
        blockingMode.writeToNbt(tag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        try {
            readingNBT = true;
            super.readFromNBT(tag);
            ChassisModule modules = getModules();

            IItemIdentifierInventory moduleInventory = (IItemIdentifierInventory) getModuleInventory();
            for (int i = 0; i < getChassisSize(); i++) {
                ItemIdentifierStack idStack = moduleInventory.getIDStackInSlot(i);

                ItemModule moduleItem = Optional.ofNullable(idStack)
                        .map(ItemIdentifierStack::getItem)
                        .map(it -> it.item)
                        .filter(it -> it instanceof ItemModule)
                        .map(ItemModule.class::cast)
                        .orElse(null);

                if (moduleItem == null) {
                    continue;
                }

                ModuleCrafterExt crafterExt = new ModuleCrafterExt();
                crafterExt.registerHandler(this, this);
                crafterExt.registerPosition(ModulePositionType.SLOT, i);
                ItemModuleInformationManager.readInformation(moduleInventory.getStackInSlot(i), crafterExt);
                modules.installModule(i, crafterExt);
            }

            resultId = tag.getString("resultname");
            satelliteId = tag.getString("satellitename");
            if (tag.hasKey("resultid")) {
                resultId = Integer.toString(tag.getInteger("resultid"));
                satelliteId = Integer.toString(tag.getInteger("satelliteid"));
            }
            blockingMode = BlockingMode.readFromNbt(tag);
        } finally {
            readingNBT = false;
        }
    }

    @Override
    public void collectSpecificInterests(@Nonnull Collection<ItemIdentifier> c) {
        for (int i = 0; i < getChassisSize(); i++) {
            LogisticsModule module = getSubModule(i);
            if (module != null) {
                module.collectSpecificInterests(c);
            }
        }
    }

    @Override
    public boolean hasGenericInterests() {
        for (int i = 0; i < getChassisSize(); i++) {
            LogisticsModule x = getSubModule(i);

            if (x != null && x.hasGenericInterests()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getName(int id) {
        return null;
    }

    private UUID getUUIDForSatelliteName(String name) {
        for (PipeItemsSatelliteLogistics pipe : PipeItemsSatelliteLogistics.AllSatellites) {
            if (pipe.getSatellitePipeName().equals(name)) {
                return pipe.getRouter().getId();
            }
        }
        return null;
    }

    private UUID getUUIDForResultName(String name) {
        for (ResultPipe pipe : ResultPipe.AllResults) {
            if (pipe.getResultPipeName().equals(name)) {
                return pipe.getRouter().getId();
            }
        }
        return null;
    }

    public UUID getSatelliteUUID() {
        if (satelliteUUID == null) {
            satelliteUUID = getUUIDForSatelliteName(satelliteId);
        }
        return satelliteUUID;
    }

    public UUID getResultUUID() {
        if (resultUUID == null) {
            resultUUID = getUUIDForResultName(resultId);
        }
        return resultUUID;
    }

    @Override
    public List<String> list(int id) {
        if (id == 0) {
            return PipeItemsSatelliteLogistics.AllSatellites.stream()
                    .filter(Objects::nonNull)
                    .filter(it -> it.getRouter() != null)
                    .filter(it -> this.getRouter().getRouteTable().size() > it.getRouterId())
                    .filter(it -> this.getRouter().getRouteTable().get(it.getRouterId()) != null)
                    .filter(it -> !this.getRouter().getRouteTable().get(it.getRouterId()).isEmpty())
                    .sorted(Comparator.comparingDouble(it -> this.getRouter().getRouteTable().get(it.getRouterId()).stream().map(it1 -> it1.distanceToDestination).min(Double::compare).get()))
                    .map(PipeItemsSatelliteLogistics::getSatellitePipeName)
                    .collect(Collectors.toList());
        } else if (id == 1) {
            return ResultPipe.AllResults.stream()
                    .filter(Objects::nonNull)
                    .filter(it -> it.getRouter() != null)
                    .filter(it -> this.getRouter().getRouteTable().size() > it.getRouterId())
                    .filter(it -> this.getRouter().getRouteTable().get(it.getRouterId()) != null)
                    .filter(it -> !this.getRouter().getRouteTable().get(it.getRouterId()).isEmpty())
                    .sorted(Comparator.comparingDouble(it -> this.getRouter().getRouteTable().get(it.getRouterId()).stream().map(it1 -> it1.distanceToDestination).min(Double::compare).get()))
                    .map(ResultPipe::getResultPipeName)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public boolean isBuffered() {
        for (int i = 0; i < 9; i++) {
            if (upgradeManager.getUpgrade(i) instanceof BufferUpgrade)
                return true;
        }
        return false;
    }

    public void addBuffered(List<Pair<UUID, ItemIdentifierStack>> rec) {
        buffered.add(rec);
    }

    public IRouter getSatelliteRouterByID(UUID id) {
        if (id == null) return null;
        int satelliteRouterId = SimpleServiceLocator.routerManager.getIDforUUID(id);
        return SimpleServiceLocator.routerManager.getRouter(satelliteRouterId);
    }

    private boolean checkBlocking() {
        switch (blockingMode) {
            case EMPTY_MAIN_SATELLITE: {
                IRouter defSat = getSatelliteRouterByID(getSatelliteUUID());
                if (defSat == null) return false;
                if (!(defSat.getPipe() instanceof PipeItemsSatelliteLogistics)) return false;

                IInventoryUtil inv = defSat.getPipe().getAvailableAdjacent().inventories().stream().map(LPNeighborTileEntityKt::getInventoryUtil).findFirst().orElse(null);
                if (inv != null) {
                    for (int i = 0; i < inv.getSizeInventory(); i++) {
                        ItemStack stackInSlot = inv.getStackInSlot(i);
                        if (!stackInSlot.isEmpty()) {
                            return false;
                        }
                    }
                }
                return true;
            }

            case REDSTONE_HIGH:
                return getWorld().isBlockPowered(getPos());

            case REDSTONE_LOW:
                return !getWorld().isBlockPowered(getPos());

			/*case WAIT_FOR_RESULT://TODO broken
		{
			IRouter resultR = getResultRouterByID(getResultUUID());
			if(resultR == null)return false;
			if(!(resultR.getPipe() instanceof ResultPipe))return false;
			if(((ResultPipe) resultR.getPipe()).hasRequests())return false;
			return true;
		}*/

            default:
                return true;
        }
    }

    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();

        if (!isNthTick(5)) return;

        if (isBuffered()) {
            if (sendCooldown > 0) {
                spawnParticle(Particles.RedParticle, 1);
                sendCooldown--;
                return;
            }
            if (blockingMode == BlockingMode.NULL) blockingMode = BlockingMode.OFF;
            if (!buffered.isEmpty()) {
                boolean allow = checkBlocking();
                if (!allow) {
                    spawnParticle(Particles.RedParticle, 1);
                    return;
                }

                if (blockingMode == BlockingMode.WAIT_FOR_RESULT && !canSendNext) {
                    return;
                }

                if (canUseEnergy(neededEnergy()) && !getAvailableAdjacent().inventories().isEmpty()) {
                    IInventoryUtil util = getAvailableAdjacent().inventories().stream().map(LPNeighborTileEntityKt::getInventoryUtil).findFirst().orElse(null);
                    for (List<Pair<UUID, ItemIdentifierStack>> map : buffered) {
                        if (map.stream().map(Pair::getValue).allMatch(i -> util.itemCount(i.getItem()) >= i.getStackSize())) {
                            int maxDist = 0;
                            for (Pair<UUID, ItemIdentifierStack> en : map) {
                                ItemIdentifierStack toSend = en.getValue();
                                ItemStack removed = util.getMultipleItems(toSend.getItem(), toSend.getStackSize());
                                if (removed != null && !removed.isEmpty()) {
                                    UUID moved;
                                    if (getSatelliteRouterByID(en.getKey()) != null)
                                        moved = en.getKey();
                                    else
                                        moved = getSatelliteUUID();
                                    sendStack(removed, SimpleServiceLocator.routerManager.getIDforUUID(moved), ItemSendMode.Fast, null, getPointedOrientation());
                                    maxDist = Math.max(maxDist, (int) getSatelliteRouterByID(moved).getPipe().getPos().distanceSq(getPos()));
                                }
                            }
                            useEnergy(neededEnergy(), true);
                            buffered.remove(map);
                            if (blockingMode == BlockingMode.EMPTY_MAIN_SATELLITE) {
                                sendCooldown = Math.min(maxDist, 16);
                            } else if (blockingMode == BlockingMode.WAIT_FOR_RESULT) {
                                canSendNext = false;
                            }

                            break;
                        }
                    }
                }
            }
        }
    }

    /*private int sendStack(ItemIdentifierStack stack, IRequestItems dest, IInventoryUtil util, IAdditionalTargetInformation info, EnumFacing dir) {
        ItemIdentifier item = stack.getItem();

        int available = util.itemCount(item);
        if (available == 0) {
            return 0;
        }

        int wanted = Math.min(available, stack.getStackSize());
        wanted = Math.min(wanted, item.getMaxStackSize());
        SinkReply reply = LogisticsManager.canSink(dest.getRouter(), null, true, stack.getItem(), null, true, false);
        if (reply != null) {// some pipes are not aware of the space in the adjacent inventory, so they return null
            if (reply.maxNumberOfItems < wanted) {
                wanted = reply.maxNumberOfItems;
                if (wanted <= 0) {
                    return 0;
                }
            }
        }
        if (!canUseEnergy(wanted * neededEnergy())) {
            return -1;
        }
        ItemStack removed = util.getMultipleItems(item, wanted);
        if (removed == null || removed.getCount() == 0) {
            return 0;
        }
        int sent = removed.getCount();
        useEnergy(sent * neededEnergy());

        IRoutedItem routedItem = SimpleServiceLocator.routedItemHelper.createNewTravelItem(removed);
        routedItem.setDestination(dest.getID());
        routedItem.setTransportMode(TransportMode.Active);
        routedItem.setAdditionalTargetInformation(info);
        super.queueRoutedItem(routedItem, dir != null ? dir : EnumFacing.UP);
        return sent;
    }
     */
    private int neededEnergy() {
        return 20;
    }

    public void save(int i) {
        ItemStack st = getModuleInventory().getStackInSlot(i);
        if (!st.isEmpty()) {
            ItemModuleInformationManager.saveInformation(st, getModules().getModule(i));
            getModuleInventory().setInventorySlotContents(i, st);
        }

        sendSignData();
    }

    public BlockingMode getBlockingMode() {
        return blockingMode;
    }

    @SideOnly(Side.CLIENT)
    public IInventory getClientModuleInventory() {
        if (clientInv == null) clientInv = new InventoryBasic("", false, 27);
        return clientInv;
    }

    public enum BlockingMode {
        NULL(0, -1),
        OFF(1, 0),
        WAIT_FOR_RESULT(5, 2),
        EMPTY_MAIN_SATELLITE(2, 1),
        REDSTONE_LOW(3, 3),
        REDSTONE_HIGH(4, 4),
        ;

        private static final String TAG_KEY = "blockingMode";

        public static final BlockingMode[] values = Arrays.stream(values())
                .filter(it -> it != NULL)
                .sorted(comparingInt(it -> it.order))
                .toArray(BlockingMode[]::new);

        private static final Map<Integer, BlockingMode> blockingModeOrders = Arrays.stream(values)
                .collect(toMap(BlockingMode::getOrder, it -> it));

        public static final int MAX_ORDER = 3;

        private final int customOrdinal;
        private final int order;

        BlockingMode(int customOrdinal, int order) {
            this.customOrdinal = customOrdinal;
            this.order = order;
        }

        public static BlockingMode blockingModeByOrder(int order) {
            return blockingModeOrders.get(order % (BlockingMode.values.length));
        }

        public int getCustomOrdinal() {
            return customOrdinal;
        }

        public int getOrder() {
            return order;
        }

        @Nonnull
        public static BlockingMode readFromNbt(@Nonnull NBTTagCompound tag) {
            BlockingMode blockingMode = Optional.of(tag)
                    .map(it -> it.getString(BlockingMode.TAG_KEY))
                    .map(String::toUpperCase)
                    .map(string -> EnumUtils.getEnum(BlockingMode.class, string))
                    .orElse(OFF);

            if (blockingMode != OFF) {
                return blockingMode;
            }

            return Optional.of(tag)
                    .map(it -> it.getString(TAG_KEY))
                    .filter(NumberUtils::isParsable)
                    .map(Integer::parseInt)
                    .flatMap(ordinal -> Arrays.stream(BlockingMode.values).filter(it -> it.customOrdinal == ordinal).findFirst())
                    .orElse(OFF);
        }

        public NBTTagCompound writeToNbt(NBTTagCompound tag) {
            tag.setString("blockingMode", name());
            return tag;
        }
    }

    public static class OriginalCrafterInfo implements IAdditionalTargetInformation {
        @Nonnull
        private final CraftingManager manager;
        @Nullable
        private final IAdditionalTargetInformation delegate;

        public OriginalCrafterInfo(@NotNull CraftingManager manager, @Nullable IAdditionalTargetInformation delegate) {
            this.manager = manager;
            this.delegate = delegate;
        }

        public void onSent() {
            manager.canSendNext = true;
        }

        @Nullable
        public IAdditionalTargetInformation getDelegate() {
            return delegate;
        }

        public static IAdditionalTargetInformation wrap(@Nonnull CraftingManager manager, @Nullable IAdditionalTargetInformation delegate) {
            return new OriginalCrafterInfo(manager, delegate);
        }

        public static IAdditionalTargetInformation unwrap(IAdditionalTargetInformation info, boolean doFinish) {
            if (info instanceof OriginalCrafterInfo) {
                OriginalCrafterInfo o = (OriginalCrafterInfo) info;
                if (doFinish) o.onSent();
                return o.delegate;
            }
            return info;
        }
    }
}
