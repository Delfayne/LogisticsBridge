package org.dv.minecraft.logisticsbridge.api;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

public interface IDynamicPatternDetailsAE {
    Map<String, Function<NBTTagCompound, IDynamicPatternDetailsAE>> FACTORIES = new HashMap<>();
    WeakHashMap<NBTTagCompound, IDynamicPatternDetailsAE> CACHE = new WeakHashMap<>();
    String ID_TAG = "_id";

    static IDynamicPatternDetailsAE load(NBTTagCompound tag) {
        return CACHE.computeIfAbsent(tag, t -> {
            String id = t.getString(ID_TAG);
            Function<NBTTagCompound, IDynamicPatternDetailsAE> fac = FACTORIES.get(id);
            return fac.apply(t);
        });
    }

    static NBTTagCompound save(IDynamicPatternDetailsAE det) {
        String id = det.getId();
        NBTTagCompound tag = new NBTTagCompound();
        det.storeToNBT(tag);
        tag.setString(ID_TAG, id);
        return tag;
    }

    default String getId() {
        throw new AbstractMethodError("Missing impl: " + getClass());
    }

    default void storeToNBT(NBTTagCompound tag) {
        throw new AbstractMethodError("Missing impl: " + getClass());
    }

    IAEItemStack[] getInputs(ItemStack res, IAEItemStack[] def, boolean condensed);

    IAEItemStack[] getOutputs(ItemStack res, IAEItemStack[] def, boolean condensed);

    class TileEntityWrapper implements IDynamicPatternDetailsAE {
        private final int dim;
        private final BlockPos pos;
        private IDynamicPatternDetailsAE tile;

        public TileEntityWrapper(int dim, BlockPos pos) {
            this.dim = dim;
            this.pos = pos;
        }

        public TileEntityWrapper(TileEntity te) {
            this(te.getWorld().provider.getDimension(), te.getPos());
            tile = (IDynamicPatternDetailsAE) te;
        }

        public static TileEntityWrapper create(NBTTagCompound tag) {
            return new TileEntityWrapper(tag.getInteger("dim"), new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")));
        }

        @Override
        public String getId() {
            return "te";
        }

        @Override
        public void storeToNBT(NBTTagCompound tag) {
            tag.setInteger("dim", dim);
            tag.setInteger("x", pos.getX());
            tag.setInteger("y", pos.getY());
            tag.setInteger("z", pos.getZ());
        }

        public void load() {
            if (tile == null) {
                World w = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(dim);
                if (w != null) {
                    TileEntity te = w.getTileEntity(pos);
                    if (te instanceof IDynamicPatternDetailsAE) {
                        this.tile = (IDynamicPatternDetailsAE) te;
                    }
                }
            }
        }

        @Override
        public IAEItemStack[] getInputs(ItemStack res, IAEItemStack[] def, boolean condensed) {
            load();
            return tile != null ? tile.getInputs(res, def, condensed) : def;
        }

        @Override
        public IAEItemStack[] getOutputs(ItemStack res, IAEItemStack[] def, boolean condensed) {
            load();
            return tile != null ? tile.getOutputs(res, def, condensed) : def;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + dim;
            result = prime * result + ((pos == null) ? 0 : pos.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            TileEntityWrapper other = (TileEntityWrapper) obj;
            if (dim != other.dim)
                return false;
            if (pos == null)
                return other.pos == null;
            else 
                return pos.equals(other.pos);
        }
    }
}
