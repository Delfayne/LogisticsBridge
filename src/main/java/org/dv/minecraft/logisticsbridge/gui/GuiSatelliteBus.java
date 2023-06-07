package org.dv.minecraft.logisticsbridge.gui;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import org.dv.minecraft.logisticsbridge.network.SetIDPacket.IIdPipe;

import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.InputBar;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;
import network.rs485.logisticspipes.util.TextUtil;

public class GuiSatelliteBus extends LogisticsBaseGuiScreen {

	private IIdPipe _satellite;
	private InputBar input;

	public GuiSatelliteBus(IIdPipe ae_sate) {
		super(new Container() {

			@Override
			public boolean canInteractWith(EntityPlayer player) {
				return true;
			}
		});
		_satellite = ae_sate;
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
			_satellite.setPipeID(0, input.getText(), null);
		} else {
			super.actionPerformed(guibutton);
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int par1, int par2) {
		super.drawGuiContainerForegroundLayer(par1, par2);
		mc.fontRenderer.drawString(TextUtil.translate(_satellite.getName(0)), 33, 10, 0x404040);
		String name = TextUtil.getTrimmedString(_satellite.getPipeID(0), 100, mc.fontRenderer, "...");
		mc.fontRenderer.drawString(name, 59 - mc.fontRenderer.getStringWidth(name) / 2, 24, 0x404040);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
		super.drawGuiContainerBackgroundLayer(f, x, y);
		GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
		input.drawTextBox();
	}

	@Override
	protected void mouseClicked(int x, int y, int k) throws IOException {
		if(!input.handleClick(x, y, k)) {
			super.mouseClicked(x, y, k);
		}
	}

	@Override
	public void keyTyped(char c, int i) throws IOException {
		if(!input.handleKey(c, i)) {
			super.keyTyped(c, i);
		}
	}
}
