package com.tom.logisticsbridge.proxy;

import appeng.bootstrap.FeatureFactory;
import appeng.bootstrap.IBootstrapComponent;
import appeng.bootstrap.components.IModelRegistrationComponent;
import appeng.bootstrap.components.ItemVariantsComponent;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.ItemRepo;
import appeng.core.Api;
import appeng.items.parts.ItemPart;
import com.tom.logisticsbridge.AE2Plugin;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.RSPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ClientProxy extends CommonProxy {
    public static Field GuiMEMonitorable_Repo;
    public static Field ItemRepo_myPartitionList;
    private List<Item> renderers = new ArrayList<>();

    private static void addRenderToRegistry(Item item, int meta, String name) {
        ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(new ResourceLocation(LogisticsBridge.ID, name), "inventory"));
    }

    //public static Field
    @SuppressWarnings("unchecked")
    @Override
    public void registerRenderers() {
        LogisticsBridge.log.info("Loading Renderers");
        for (Item item : renderers) {
            addRenderToRegistry(item, 0, item.getUnlocalizedName().substring(5));
        }
        renderers = null;
        if (LogisticsBridge.aeLoaded) {
            try {
                FeatureFactory ff = Api.INSTANCE.definitions().getRegistry();
                Field bootstrapComponentsF = FeatureFactory.class.getDeclaredField("bootstrapComponents");
                bootstrapComponentsF.setAccessible(true);
                Map<Class<? extends IBootstrapComponent>, List<IBootstrapComponent>> bootstrapComponents = (Map<Class<? extends IBootstrapComponent>, List<IBootstrapComponent>>) bootstrapComponentsF.get(ff);
                List<IBootstrapComponent> itemRegComps = bootstrapComponents.get(IModelRegistrationComponent.class);
                ItemVariantsComponent partReg = null;
                Field ItemVariantsComponent_item = ItemVariantsComponent.class.getDeclaredField("item");
                ItemVariantsComponent_item.setAccessible(true);
                for (IBootstrapComponent iBootstrapComponent : itemRegComps) {
                    if (iBootstrapComponent instanceof ItemVariantsComponent) {
                        Item item = (Item) ItemVariantsComponent_item.get(iBootstrapComponent);
                        if (item == ItemPart.instance) {
                            partReg = (ItemVariantsComponent) iBootstrapComponent;
                            break;
                        }
                    }
                }
                Field ItemVariantsComponent_resources = ItemVariantsComponent.class.getDeclaredField("resources");
                ItemVariantsComponent_resources.setAccessible(true);
                ((HashSet<ResourceLocation>) ItemVariantsComponent_resources.get(partReg)).addAll(AE2Plugin.SATELLITE_BUS.getItemModels());
            } catch (Exception e) {
                throw new RuntimeException("Error registering part model", e);
            }
        }
        //OBJLoader.INSTANCE.addDomain(LogisticsBridge.ID);
    }

    @Override
    public void addRenderer(ItemStack is, String name) {
        addRenderToRegistry(is.getItem(), is.getItemDamage(), name);
    }

    @Override
    public void addRenderer(Item item) {
        renderers.add(item);
    }

    @Override
    public void registerTextures() {
        LogisticsBridge.registerTextures(Minecraft.getMinecraft().getTextureMapBlocks());
    }

    @Override
    public void init() {
        try {
            if (LogisticsBridge.aeLoaded) {
                GuiMEMonitorable_Repo = GuiMEMonitorable.class.getDeclaredField("repo");
                GuiMEMonitorable_Repo.setAccessible(true);
                ItemRepo_myPartitionList = ItemRepo.class.getDeclaredField("myPartitionList");
                ItemRepo_myPartitionList.setAccessible(true);
            }
        } catch (SecurityException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onDrawBackgroundEventPost(GuiScreenEvent.BackgroundDrawnEvent event) {
        if (LogisticsBridge.aeLoaded) {
            AE2Plugin.hideFakeItems(event);
        }
        if (LogisticsBridge.rsLoaded) {
            RSPlugin.hideFakeItems(event);
        }
    }

    @SubscribeEvent
    public void loadModels(ModelRegistryEvent ev) {
        if (LogisticsBridge.aeLoaded) {
            AE2Plugin.loadModels();
        }
    }
}
