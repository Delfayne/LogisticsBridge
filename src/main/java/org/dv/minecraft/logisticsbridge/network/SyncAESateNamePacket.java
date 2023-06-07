package org.dv.minecraft.logisticsbridge.network;

import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.packets.satpipe.SyncSatelliteNamePacket;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.StaticResolve;
import net.minecraft.entity.player.EntityPlayer;
import network.rs485.logisticspipes.SatellitePipe;
import org.jetbrains.annotations.NotNull;

@StaticResolve
public class SyncAESateNamePacket extends SyncSatelliteNamePacket {
    public SyncAESateNamePacket(int id) {
        super(id);
    }

    @Override
    public void processPacket(@NotNull EntityPlayer player) {
        final LogisticsTileGenericPipe pipe = getPipe(player.world, LTGPCompletionCheck.PIPE);
        if (pipe.pipe == null)
            return;

        if (pipe.pipe instanceof SatellitePipe)
            ((SatellitePipe) pipe.pipe).setSatellitePipeName(getString());
    }

    @Override
    public ModernPacket template() {
        return new SyncAESateNamePacket(getId());
    }
}
