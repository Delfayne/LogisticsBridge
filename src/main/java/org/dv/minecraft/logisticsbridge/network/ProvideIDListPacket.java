package org.dv.minecraft.logisticsbridge.network;

import org.dv.minecraft.logisticsbridge.gui.GuiSelectIDPopup;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.utils.StaticResolve;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SubGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import network.rs485.logisticspipes.util.LPDataInput;
import network.rs485.logisticspipes.util.LPDataOutput;

import java.util.List;

@StaticResolve
public class ProvideIDListPacket extends ModernPacket {

    private List<String> list;

    public ProvideIDListPacket(int id) {
        super(id);
    }

    @Override
    public void readData(LPDataInput input) {
        super.readData(input);
        list = input.readArrayList(LPDataInput::readUTF);
    }

    @Override
    public void writeData(LPDataOutput output) {
        super.writeData(output);
        output.writeCollection(list, LPDataOutput::writeUTF);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (Minecraft.getMinecraft().currentScreen instanceof LogisticsBaseGuiScreen) {
            SubGuiScreen subGUI = ((LogisticsBaseGuiScreen) Minecraft.getMinecraft().currentScreen).getSubGui();
            if (subGUI instanceof GuiSelectIDPopup)
                ((GuiSelectIDPopup) subGUI).handleList(list);
        }
    }

    @Override
    public ModernPacket template() {
        return new ProvideIDListPacket(getId());
    }

    public ModernPacket setList(List<String> list2) {
        this.list = list2;
        return this;
    }
}
