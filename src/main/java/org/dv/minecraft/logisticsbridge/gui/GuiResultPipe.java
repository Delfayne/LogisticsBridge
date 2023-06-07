package org.dv.minecraft.logisticsbridge.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.satpipe.SatelliteSetNamePacket;
import logisticspipes.pipes.SatelliteNamingResult;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.InputBar;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;

import network.rs485.logisticspipes.SatellitePipe;
import network.rs485.logisticspipes.util.TextUtil;

import org.lwjgl.input.Keyboard;

import javax.annotation.Nonnull;
import java.io.IOException;

public class GuiResultPipe extends LogisticsBaseGuiScreen {

    private final SatellitePipe resultPipe;

    private InputBar input;

    public GuiResultPipe(@Nonnull SatellitePipe result) {
        super(new Container() {
            @Override
            public boolean canInteractWith(@Nonnull EntityPlayer player) {
                return true;
            }
        });
	    resultPipe = result;
        xSize = 116;
        ySize = 77;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        super.initGui();
        buttonList.add(new SmallGuiButton(0, (width / 2) - (30 / 2) + 35, (height / 2) + 20, 30, 10, TextUtil.translate("gui.popup.addchannel.save")));
        input = new InputBar(fontRenderer, this, guiLeft + 8, guiTop + 40, 100, 16);
    }

    @Override
    public void closeGui() throws IOException {
        super.closeGui();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) throws IOException {
        if (guibutton.id == 0) {
            final TileEntity container = resultPipe.getContainer();
            if (container != null) {
                MainProxy.sendPacketToServer(PacketHandler.getPacket(SatelliteSetNamePacket.class).setString(input.getText()).setTilePos(container));
            }
        } else {
            super.actionPerformed(guibutton);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
		drawCenteredString(TextUtil.translate("gui.resultPipe"), 59, 7, 0x404040);
		String name = TextUtil.getTrimmedString(resultPipe.getSatellitePipeName(), 100, mc.fontRenderer, "...");
		drawCenteredString(name, xSize / 2, 20, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
		super.drawGuiContainerBackgroundLayer(f, x, y);
        GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
        input.drawTextBox();
    }

    @Override
    protected void mouseClicked(int x, int y, int k) throws IOException {
        if (!input.handleClick(x, y, k)) {
            super.mouseClicked(x, y, k);
        }
    }

    @Override
    public void keyTyped(char c, int i) throws IOException {
        if (!input.handleKey(c, i)) {
            super.keyTyped(c, i);
        }
    }

    public void handleResponse(SatelliteNamingResult result, String newName) {
        if (result == SatelliteNamingResult.SUCCESS) {
            resultPipe.setSatellitePipeName(newName);
        }
    }
}

