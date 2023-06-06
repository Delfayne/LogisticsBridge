package com.tom.logisticsbridge.node;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternProvider;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeCrafter;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerBase;
import com.raoulvdberge.refinedstorage.inventory.listener.ListenerNetworkNode;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import com.tom.logisticsbridge.LB_ItemStore;
import com.tom.logisticsbridge.item.VirtualPatternRS;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.pipe.CraftingManager.BlockingMode;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class NetworkNodeCraftingManager extends NetworkNode implements IIdPipe, ICraftingPatternContainer {
    public static final String ID = "lb.craftingmngr";
    private static final String NAME = "tile.lb.crafingmanager.rs.name";
    private static final String NBT_UUID = "uuid";
    private String supplyID;
    @Nullable
    private UUID uuid = null;
    private final List<ICraftingPattern> patterns = new ArrayList<>();
    private boolean reading;
    private final ItemHandlerBase patternsInventory = new ItemHandlerBase(27, new ListenerNetworkNode(this),
            s -> NetworkNodeCrafter.isValidPatternInSlot(world, s)) {

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);

            if (!reading) {
                if (!world.isRemote) {
                    invalidate();
                }

                if (network != null) {
                    network.getCraftingManager().rebuild();
                }
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };
    private BlockingMode blockingMode = BlockingMode.OFF;
    private final IItemHandler inv = new IItemHandler() {

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (supplyID.isEmpty()) return stack;
            if (!checkBlocking()) return stack;
            NetworkNodeSatellite sat = find(supplyID);
            if (sat == null) return stack;
            if (stack.getItem() == LB_ItemStore.packageItem && stack.hasTagCompound() && stack.getTagCompound().getBoolean("__actStack")) {
                String id = stack.getTagCompound().getString("__pkgDest");
                sat = find(id);
                if (sat == null) return stack;
                if (!simulate) {
                    ItemStack pkgItem = new ItemStack(stack.getTagCompound());
                    pkgItem.setCount(pkgItem.getCount() * stack.getCount());
                    sat.push(pkgItem);
                }
            } else {
                if (!simulate) sat.push(stack);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlots() {
            return 9;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    };

    public NetworkNodeCraftingManager(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return 10;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName(int id) {
        return null;
    }

    @Override
    public String getPipeID(int id) {
        return id == 0 ? supplyID : Integer.toString(blockingMode.getCustomOrdinal());
    }

    @Override
    public void setPipeID(int id, String pipeID, EntityPlayer player) {
        if (pipeID == null) pipeID = "";
        if (player == null) {
            final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setBlockPos(getPos()).setDimension(getWorld());
            MainProxy.sendPacketToServer(packet);
        } else if (MainProxy.isServer(player.world)) {
            final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setBlockPos(getPos()).setDimension(getWorld());
            MainProxy.sendPacketToPlayer(packet, player);
        }
        if (id == 0) supplyID = pipeID;
        else if (id == 2) {
            blockingMode = BlockingMode.valueOf(pipeID);
            if (blockingMode == BlockingMode.WAIT_FOR_RESULT) {
                blockingMode = BlockingMode.blockingModeByOrder(BlockingMode.WAIT_FOR_RESULT.getOrder() + 1);
            }
        }
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        tag.setString("supplyName", supplyID);
        if (uuid != null) {
            tag.setUniqueId(NBT_UUID, uuid);
        }
        StackUtils.writeItems(patternsInventory, 0, tag);
        blockingMode.writeToNbt(tag);
        return super.write(tag);
    }

    @Override
    public void read(NBTTagCompound tag) {
        this.reading = true;
        StackUtils.readItems(patternsInventory, 0, tag);
        this.invalidate();
        this.reading = false;
        supplyID = tag.getString("supplyName");
        if (tag.hasUniqueId(NBT_UUID)) {
            uuid = tag.getUniqueId(NBT_UUID);
        }
        blockingMode = BlockingMode.readFromNbt(tag);
        super.read(tag);
    }

    @Override
    public List<String> list(int id) {
        return network.getNodeGraph().all().stream().filter(n -> n instanceof NetworkNodeSatellite).
                map(n -> ((NetworkNodeSatellite) n).satelliteId).collect(Collectors.toList());
    }

    private NetworkNodeSatellite find(String id) {
        return network.getNodeGraph().all().stream().filter(n -> n instanceof NetworkNodeSatellite).
                map(n -> (NetworkNodeSatellite) n).filter(n -> id.equals(n.satelliteId)).findFirst().
                orElse(null);
    }

    @Override
    public IItemHandler getConnectedInventory() {
        return inv;
    }

    @Override
    public List<ICraftingPattern> getPatterns() {
        return patterns;
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
    public IFluidHandler getConnectedFluidInventory() {
        return null;
    }

    @Override
    public TileEntity getConnectedTile() {
        return null;
    }

    @Override
    public IItemHandlerModifiable getPatternInventory() {
        return patternsInventory;
    }

    private void invalidate() {
        patterns.clear();

        for (int i = 0; i < patternsInventory.getSlots(); ++i) {
            ItemStack patternStack = patternsInventory.getStackInSlot(i);

            if (!patternStack.isEmpty()) {
                ICraftingPattern pattern = ((ICraftingPatternProvider) patternStack.getItem()).create(world, patternStack, this);

                if (pattern.isValid()) {
                    List<NonNullList<ItemStack>> inputs = pattern.getInputs();
                    boolean pkg = false;

                    for (NonNullList<ItemStack> nonNullList : inputs) {
                        if (nonNullList.size() == 1 && nonNullList.get(0).getItem() == LB_ItemStore.packageItem && nonNullList.get(0).hasTagCompound()) {
                            pkg = true;
                            ItemStack is = nonNullList.get(0).copy();
                            is.getTagCompound().setBoolean("__actStack", true);
                            nonNullList.set(0, is);
                            patterns.add(VirtualPatternRS.create(new ItemStack(nonNullList.get(0).getTagCompound()),
                                    is, this));
                        }
                    }

                    patterns.add(pattern);
                }
            }
        }
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

    private boolean checkBlocking() {
        switch (blockingMode) {
            case EMPTY_MAIN_SATELLITE: {
                if (supplyID.isEmpty()) return false;
                NetworkNodeSatellite bus = find(supplyID);
                if (bus == null) return false;
                IItemHandler invent = bus.getHandler();
                if (invent != null) {
                    for (int i = 0; i < invent.getSlots(); i++) {
                        ItemStack stackInSlot = invent.getStackInSlot(i);
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

			/*case WAIT_FOR_RESULT://TODO
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

    public BlockingMode getBlockingMode() {
        return blockingMode;
    }
}
