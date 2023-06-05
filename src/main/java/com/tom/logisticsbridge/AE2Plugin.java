package com.tom.logisticsbridge;

import appeng.api.AEPlugin;
import appeng.api.IAppEngApi;
import appeng.api.config.FuzzyMode;
import appeng.api.definitions.IMaterials;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
import appeng.block.AEBaseItemBlock;
import appeng.bootstrap.FeatureFactory;
import appeng.bootstrap.components.ItemVariantsComponent;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.ItemRepo;
import appeng.core.Api;
import appeng.core.CreativeTab;
import appeng.core.features.AEFeature;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.core.features.ItemStackSrc;
import appeng.integration.IntegrationType;
import appeng.items.parts.ItemPart;
import appeng.items.parts.PartType;
import appeng.tile.AEBaseTile;
import appeng.util.ItemSorters;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.prioritylist.MergedPriorityList;
import com.tom.logisticsbridge.block.BlockBridgeAE;
import com.tom.logisticsbridge.block.BlockCraftingManager;
import com.tom.logisticsbridge.item.VirtualPatternAE;
import com.tom.logisticsbridge.network.RequestIDListPacket;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.part.PartSatelliteBus;
import com.tom.logisticsbridge.tileentity.TileEntityBridgeAE;
import com.tom.logisticsbridge.tileentity.TileEntityCraftingManager;
import com.tom.logisticsbridge.util.Reflector;
import io.netty.buffer.ByteBuf;
import logisticspipes.LPItems;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.ShapedOreRecipe;

import java.lang.invoke.MethodHandle;
import java.util.*;

@AEPlugin
public class AE2Plugin {
    public static AE2Plugin INSTANCE;
    public static VirtualPatternAE virtualPattern;
    public static FakeItemList FAKE_ITEM_PARTITION_LIST;
    public static PartType SATELLITE_BUS;
    public static ItemStackSrc SATELLITE_BUS_SRC;
    public final IAppEngApi api;

    public static final MethodHandle guiMEMonitorableRepoGetter;
    public static final MethodHandle itemRepoMyPartitionListGetter;
    public static final MethodHandle itemRepoMyPartitionListSetter;
    public static final MethodHandle sorterBySizeGetter;
    public static final MethodHandle sorterBySizeSetter;
    public static final MethodHandle bootstrapComponentsF;
    public static final MethodHandle itemVariantsComponentItem;
    public static final MethodHandle itemVariantsComponentResources;


    static {
        try {
            // "I reject performance" - Korewa_Li
            guiMEMonitorableRepoGetter = Reflector.resolveFieldGetter(GuiMEMonitorable.class, "repo");
            itemRepoMyPartitionListGetter = Reflector.resolveFieldGetter(ItemRepo.class,"myPartitionList");
            itemRepoMyPartitionListSetter = Reflector.resolveFieldSetter(ItemRepo.class,"myPartitionList");
            sorterBySizeGetter = Reflector.resolveFieldGetter(ItemSorters.class, "CONFIG_BASED_SORT_BY_SIZE");
            sorterBySizeSetter = Reflector.resolveFieldSetter(ItemSorters.class, "CONFIG_BASED_SORT_BY_SIZE");
            bootstrapComponentsF = Reflector.resolveFieldGetter(FeatureFactory.class, "bootstrapComponents");
            itemVariantsComponentItem = Reflector.resolveFieldGetter(ItemVariantsComponent.class,"item");
            itemVariantsComponentResources = Reflector.resolveFieldGetter(ItemVariantsComponent.class,"resources");
        } catch (SecurityException se) {
            throw new RuntimeException(se);
        }
    }

    public AE2Plugin(IAppEngApi api) {
        this.api = api;
        INSTANCE = this;
    }

    public static void registerBlock(Block block) {
        block.setCreativeTab(CreativeTab.instance);
        LogisticsBridge.registerBlock(block, AEBaseItemBlock::new);
    }

    public static void preInit() {
        virtualPattern = new VirtualPatternAE();
        LogisticsBridge.bridgeAE = new BlockBridgeAE().setTranslationKey("lb.bridge");
        LogisticsBridge.craftingManager = new BlockCraftingManager().setTranslationKey("lb.crafting_managerAE");
        AE2Plugin.registerBlock(LogisticsBridge.bridgeAE);
        AE2Plugin.registerBlock(LogisticsBridge.craftingManager);
        LogisticsBridge.registerItem(virtualPattern, true);
        AE2Plugin.SATELLITE_BUS = EnumHelper.addEnum(PartType.class, "SATELLITE_BUS", new Class[]{int.class, String.class, Set.class, Set.class, Class.class},
                1024, "satellite_bus", EnumSet.of(AEFeature.CRAFTING_CPU), EnumSet.noneOf(IntegrationType.class), PartSatelliteBus.class);
        Api.INSTANCE.getPartModels().registerModels(AE2Plugin.SATELLITE_BUS.getModels());
        AE2Plugin.SATELLITE_BUS_SRC = ItemPart.instance.createPart(AE2Plugin.SATELLITE_BUS);

        GameRegistry.registerTileEntity(TileEntityBridgeAE.class, new ResourceLocation(Reference.MOD_ID, "bridge"));
        AEBaseTile.registerTileItem(TileEntityBridgeAE.class, new BlockStackSrc(LogisticsBridge.bridgeAE, 0, ActivityState.Enabled));
        GameRegistry.registerTileEntity(TileEntityCraftingManager.class, new ResourceLocation(Reference.MOD_ID, "craftingManagerAE"));
        AEBaseTile.registerTileItem(TileEntityCraftingManager.class, new BlockStackSrc(LogisticsBridge.craftingManager, 0, ActivityState.Enabled));
    }

    @SuppressWarnings("unchecked")
    public static void patchSorter() {
        try {
            Comparator<IAEItemStack> old = (Comparator<IAEItemStack>) sorterBySizeGetter.invokeExact();
            IAEItemStack s1 = new AE2Plugin.StackSize().setStackSize(1);
            IAEItemStack s2 = new AE2Plugin.StackSize().setStackSize(2);
            sorterBySizeSetter.invoke(new Comparator<IAEItemStack>() {
                @Override
                public int compare(IAEItemStack o1, IAEItemStack o2) {
                    final int cmp = Long.compare(o2.getStackSize() + o2.getCountRequestable(), o1.getStackSize() + o1.getCountRequestable());
                    return applyDirection(cmp);
                }

                private int applyDirection(int cmp) {
                    int dir = old.compare(s1, s2);
                    return dir * cmp;
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void loadRecipes(ResourceLocation group) {
        IMaterials mat = AE2Plugin.INSTANCE.api.definitions().materials();
        ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(LogisticsBridge.bridgeAE), "iei", "bIb", "ici",
                'i', "ingotIron",
                'b', LPItems.pipeBasic,
                'I', AE2Plugin.INSTANCE.api.definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY),
                'c', mat.calcProcessor().maybeStack(1).orElse(ItemStack.EMPTY),
                'e', mat.engProcessor().maybeStack(1).orElse(ItemStack.EMPTY)).
                setRegistryName(new ResourceLocation(Reference.MOD_ID, "recipes/bridge")));
        ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, AE2Plugin.SATELLITE_BUS_SRC.stack(1), " c ", "ifi", " p ",
                'p', Blocks.PISTON,
                'f', mat.formationCore().maybeStack(1).orElse(ItemStack.EMPTY),
                'i', "ingotIron",
                'c', mat.calcProcessor().maybeStack(1).orElse(ItemStack.EMPTY)).
                setRegistryName(new ResourceLocation(Reference.MOD_ID, "recipes/satellite_bus")));
        ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(LogisticsBridge.craftingManager), "IlI", "cec", "ili",
                'I', AE2Plugin.INSTANCE.api.definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY),
                'l', mat.logicProcessor().maybeStack(1).orElse(ItemStack.EMPTY),
                'i', "ingotIron",
                'e', mat.engProcessor().maybeStack(1).orElse(ItemStack.EMPTY),
                'c', mat.calcProcessor().maybeStack(1).orElse(ItemStack.EMPTY)).
                setRegistryName(new ResourceLocation(Reference.MOD_ID, "recipes/crafting_manager_ae")));
    }

    public static IIdPipe processReqIDList(EntityPlayer player, RequestIDListPacket pck) {
        AEPartLocation side = AEPartLocation.fromOrdinal(pck.side - 1);
        IPartHost ph = pck.getTileAs(player.world, IPartHost.class);
        if (ph == null) return null;
        IPart p = ph.getPart(side);
        if (p instanceof IIdPipe)
            return (IIdPipe) p;
        return null;
    }

    public static void processResIDMod(EntityPlayer player, SetIDPacket pck) {
        AEPartLocation side = AEPartLocation.fromOrdinal(pck.side - 1);
        IPartHost ph = pck.getTileAs(player.world, IPartHost.class);
        if (ph == null)
            return;
        IPart p = ph.getPart(side);
        if (p instanceof IIdPipe)
            ((IIdPipe) p).setPipeID(pck.id, pck.pid, player);
    }

    @SuppressWarnings("unchecked")
    @SideOnly(Side.CLIENT)
    public static void hideFakeItems(GuiScreenEvent.BackgroundDrawnEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiMEMonitorable) {
            GuiMEMonitorable gui = (GuiMEMonitorable) mc.currentScreen;
            if (AE2Plugin.FAKE_ITEM_PARTITION_LIST == null)
                AE2Plugin.FAKE_ITEM_PARTITION_LIST = new FakeItemList();
            try {
                ItemRepo guiItemRepo = (ItemRepo) guiMEMonitorableRepoGetter.invoke(gui);
                IPartitionList<IAEItemStack> partList = (IPartitionList<IAEItemStack>) itemRepoMyPartitionListGetter.invoke(guiItemRepo);
                if (partList instanceof MergedPriorityList) {
                    MergedPriorityList<IAEItemStack> ml = (MergedPriorityList<IAEItemStack>) partList;
                    if (AE2Plugin.FAKE_ITEM_PARTITION_LIST.getStreams().allMatch(ml::isListed)) {
                        ml.addNewList(AE2Plugin.FAKE_ITEM_PARTITION_LIST, false);
                        guiItemRepo.updateView();
                    }
                } else {
                    MergedPriorityList<IAEItemStack> newMList = new MergedPriorityList<>();
                    itemRepoMyPartitionListSetter.invoke(guiItemRepo, newMList);
                    if (partList != null) newMList.addNewList(partList, true);
                    newMList.addNewList(AE2Plugin.FAKE_ITEM_PARTITION_LIST, false);
                    guiItemRepo.updateView();
                }
            } catch (Throwable ignored) { }
        }
    }

    @SideOnly(Side.CLIENT)
    public static void loadModels() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getRenderItem().getItemModelMesher().register(ItemPart.instance, 1024, AE2Plugin.SATELLITE_BUS.getItemModels().get(0));
        ModelLoader.setCustomModelResourceLocation(ItemPart.instance, 1024, AE2Plugin.SATELLITE_BUS.getItemModels().get(0));
    }

    static class StackSize implements IAEItemStack {
        private long ss;

        @Override
        public long getStackSize() {
            return ss;
        }

        @Override
        public IAEItemStack setStackSize(long stackSize) {
            this.ss = stackSize;
            return this;
        }

        @Override
        public long getCountRequestable() {
            return 0;
        }

        @Override
        public IAEItemStack setCountRequestable(long countRequestable) {
            return this;
        }

        @Override
        public boolean isCraftable() {
            return false;
        }

        @Override
        public IAEItemStack setCraftable(boolean isCraftable) {
            return this;
        }

        @Override
        public IAEItemStack reset() {
            return this;
        }

        @Override
        public boolean isMeaningful() {
            return false;
        }

        @Override
        public void incStackSize(long i) {
            ss += i;
        }

        @Override
        public void decStackSize(long i) {
            ss -= i;
        }

        @Override
        public void incCountRequestable(long i) { }

        @Override
        public void decCountRequestable(long i) { }

        @Override
        public void writeToNBT(NBTTagCompound i) { }

        @Override
        public boolean fuzzyComparison(IAEItemStack other, FuzzyMode mode) {
            return false;
        }

        @Override
        public void writeToPacket(ByteBuf data) { }

        @Override
        public IAEItemStack empty() {
            return this;
        }

        @Override
        public boolean isItem() {
            return false;
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public IStorageChannel<IAEItemStack> getChannel() {
            return null;
        }

        @Override
        public ItemStack asItemStackRepresentation() {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack createItemStack() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean hasTagCompound() {
            return false;
        }

        @Override
        public void add(IAEItemStack option) { }

        @Override
        public IAEItemStack copy() {
            return new StackSize().setStackSize(ss);
        }

        @Override
        public Item getItem() {
            return Items.AIR;
        }

        @Override
        public int getItemDamage() {
            return 0;
        }

        @Override
        public boolean sameOre(IAEItemStack is) {
            return false;
        }

        @Override
        public boolean isSameType(IAEItemStack otherStack) {
            return false;
        }

        @Override
        public boolean isSameType(ItemStack stored) {
            return false;
        }

        @Override
        public ItemStack getDefinition() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean equals(ItemStack itemStack) {
            return false; // TODO
        }

        @Override
        public ItemStack getCachedItemStack(long l) {
            return null; // TODO
        }

        @Override
        public void setCachedItemStack(ItemStack itemStack) {
            // TODO
        }
    }
}
