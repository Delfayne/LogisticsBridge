package org.dv.minecraft.logisticsbridge.module;

import org.dv.minecraft.logisticsbridge.network.CSignCraftingManagerData;
import org.dv.minecraft.logisticsbridge.pipe.CraftingManager;
import logisticspipes.modules.LogisticsModule.ModulePositionType;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.signs.IPipeSign;
import logisticspipes.renderer.LogisticsRenderPipe;
import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class CraftingManagerPipeSign implements IPipeSign {

    private CoreRoutedPipe pipe;

    @SideOnly(Side.CLIENT)
    private Framebuffer fbo;
    private ItemIdentifierStack oldRenderedStack = null;


    @Override
    public boolean isAllowedFor(CoreRoutedPipe pipe) {
        return pipe instanceof CraftingManager;
    }

    @Override
    public void addSignTo(CoreRoutedPipe pipe, EnumFacing dir, EntityPlayer player) {
        pipe.addPipeSign(dir, new CraftingManagerPipeSign(), player);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
    }

    @Override
    public ModernPacket getPacket() {
        CraftingManager cpipe = (CraftingManager) pipe;
        return PacketHandler.getPacket(CSignCraftingManagerData.class).setInventory(cpipe.getModuleInventory()).setType(ModulePositionType.IN_PIPE).setPosX(cpipe.getX()).setPosY(cpipe.getY()).setPosZ(cpipe.getZ());
    }

    @Override
    public void updateServerSide() {
    }

    @Override
    public void init(CoreRoutedPipe pipe, EnumFacing dir) {
        this.pipe = pipe;
    }

    @Override
    public void activate(EntityPlayer player) {
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void render(CoreRoutedPipe pipe, LogisticsRenderPipe renderer) {
        CraftingManager cpipe = (CraftingManager) pipe;
        //FontRenderer var17 = renderer.getFontRenderer();
        if (cpipe != null) {
			/*GlStateManager.depthMask(false);
			GlStateManager.rotate(-180.0F, 1.0F, 0.0F, 0.0F);
			GlStateManager.translate(0.5F, +0.08F, 0.0F);
			GlStateManager.scale(1.0F / 90.0F, 1.0F / 90.0F, 1.0F / 90.0F);*/
            float s = 0.29f;
            GlStateManager.translate(-0.1F, +0.08F, 0.0F);
            GlStateManager.scale(s, s, 1);

            for (int i = 0; i < 27; i++) {
                int x = i % 9;
                int y = i / 9;

                ItemStack is = cpipe.getClientModuleInventory().getStackInSlot(i);

                if (!is.isEmpty()) {
                    NBTTagCompound output = null;

                    if (is.hasTagCompound()) {
                        NBTTagCompound info = is.getTagCompound().getCompoundTag("moduleInformation");
                        NBTTagList list = info.getTagList("items", 10);
                        for (int j = 0; j < list.tagCount(); j++) {
                            NBTTagCompound tag = list.getCompoundTagAt(j);
                            if (tag.getInteger("index") == 9) {
                                output = tag;
                            }
                        }
                    }

                    ItemStack toRender = is;

                    if (output != null) {
                        toRender = new ItemStack(output);
                    }

                    oldRenderedStack = ItemIdentifierStack.getFromStack(is);

                    GlStateManager.pushMatrix();
                    GlStateManager.translate(x * 0.35f, -y * 0.35f, 0);
                    renderer.renderItemStackOnSign(toRender);
                    GlStateManager.popMatrix();
                }
            }

			/*GlStateManager.depthMask(true);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);*/
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Framebuffer getMCFrameBufferForSign() {
        if (!OpenGlHelper.isFramebufferEnabled()) {
            return null;
        }
        if (fbo == null) {
            fbo = new Framebuffer(256, 256, true);
        }
        return fbo;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean doesFrameBufferNeedUpdating(CoreRoutedPipe pipe, LogisticsRenderPipe renderer) {
        CraftingManager craftingManager = (CraftingManager) pipe;
        ItemIdentifierStack itemStack = getItemIdentifierStack(craftingManager);
        if (itemStack != null && oldRenderedStack != null) {
            return fbo == null || !oldRenderedStack.equals(itemStack);
        } else if (itemStack == null && oldRenderedStack == null) {
            return fbo == null;
        } else {
            return true;
        }
    }

    @Nullable
    private ItemIdentifierStack getItemIdentifierStack(CraftingManager craftingManager) {
        return Optional.ofNullable(craftingManager)
                .map(CraftingManager::getClientModuleInventory)
                .map(it -> it.getStackInSlot(0))
                .filter(it -> !it.isEmpty())
                .map(ItemIdentifierStack::getFromStack)
                .orElse(null);
    }
}
