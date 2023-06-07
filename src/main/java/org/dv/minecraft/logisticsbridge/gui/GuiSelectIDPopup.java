package org.dv.minecraft.logisticsbridge.gui;

import org.dv.minecraft.logisticsbridge.network.RequestIDListPacket;
import logisticspipes.network.PacketHandler;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.SmallGuiButton;
import logisticspipes.utils.gui.SubGuiScreen;
import logisticspipes.utils.gui.TextListDisplay;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.math.BlockPos;
import network.rs485.logisticspipes.util.TextUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class GuiSelectIDPopup extends SubGuiScreen {
    private final Consumer<String> handleResult;
    private final TextListDisplay textList;
    public static final String GUI_LANG_KEY = "gui.popup.selectsatellite.";
    private List<String> pipeList = Collections.emptyList();

    public GuiSelectIDPopup(BlockPos pos, int id, int side, Consumer<String> handleResult) {
        super(150, 170, 0, 0);
        this.handleResult = handleResult;
        this.textList = new TextListDisplay(this, 6, 16, 6, 30, 12, new TextListDisplay.List() {

            @Override
            public int getSize() {
                return pipeList.size();
            }

            @Override
            public String getTextAt(int index) {
                return pipeList.get(index);
            }

            @Override
            public int getTextColor(int index) {
                return 0xFFFFFF;
            }
        });
        MainProxy.sendPacketToServer(PacketHandler.getPacket(RequestIDListPacket.class).setId(id).setSide(side).setBlockPos(pos));
    }

    protected void drawTitle() {
        mc.fontRenderer.drawStringWithShadow(TextUtil.translate(GUI_LANG_KEY + "title"), xCenter - (mc.fontRenderer.getStringWidth(TextUtil.translate(GUI_LANG_KEY + "title")) / 2f), guiTop + 6f, 0xFFFFFF);
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        buttonList.add(new SmallGuiButton(0, xCenter + 16, bottom - 27, 50, 10, TextUtil.translate(GUI_LANG_KEY + "select")));
        buttonList.add(new SmallGuiButton(1, xCenter + 16, bottom - 15, 50, 10, TextUtil.translate(GUI_LANG_KEY + "exit")));
        buttonList.add(new SmallGuiButton(2, xCenter - 66, bottom - 27, 50, 10, TextUtil.translate(GUI_LANG_KEY + "unset")));
        buttonList.add(new SmallGuiButton(4, xCenter - 12, bottom - 27, 25, 10, "/\\"));
        buttonList.add(new SmallGuiButton(5, xCenter - 12, bottom - 15, 25, 10, "\\/"));
    }

    @Override
    protected void renderGuiBackground(int mouseX, int mouseY) {
        GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
        drawTitle();

        textList.renderGuiBackground(mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int i, int j, int k) throws IOException {
        textList.mouseClicked(i, j, k);
        super.mouseClicked(i, j, k);
    }

    @Override
    public void handleMouseInputSub() throws IOException {
        int wheel = org.lwjgl.input.Mouse.getDWheel() / 120;
        if (wheel == 0)
            super.handleMouseInputSub();
        if (wheel < 0)
            textList.scrollUp();
        else if (wheel > 0)
            textList.scrollDown();
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) throws IOException {
        if (guibutton.id == 0) { // Select
            int selected = textList.getSelected();
            if (selected >= 0) {
                handleResult.accept(pipeList.get(selected));
                exitGui();
            }
        } else if (guibutton.id == 1) // Exit
            exitGui();
        else if (guibutton.id == 2) { // UnSet
            handleResult.accept(null);
            exitGui();
        } else if (guibutton.id == 4)
            textList.scrollDown();
        else if (guibutton.id == 5)
            textList.scrollUp();
        else
            super.actionPerformed(guibutton);
    }

    public void handleList(List<String> list) {
        pipeList = list;
    }
}
