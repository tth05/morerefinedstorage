package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.tile.TileDetector;
import com.raoulvdberge.refinedstorage.tile.config.FilterType;
import com.raoulvdberge.refinedstorage.tile.config.IRSFilterConfigProvider;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig;
import com.raoulvdberge.refinedstorage.tile.config.RedstoneMode;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;

public class NetworkNodeDetector extends NetworkNode implements IRSFilterConfigProvider {
    public static final String ID = "detector";

    private static final int SPEED = 5;

    public static final int MODE_UNDER = 0;
    public static final int MODE_EQUAL = 1;
    public static final int MODE_ABOVE = 2;

    private static final String NBT_MODE = "Mode";
    private static final String NBT_AMOUNT = "Amount";

    private final FilterConfig config = new FilterConfig.Builder(this)
            .allowedFilterModeWhitelist()
            .allowedFilterTypeItemsAndFluids()
            .filterTypeItems()
            .filterSizeOne()
            .compareDamageAndNbt()
            .customFilterTypeSupplier((ft) -> world.isRemote ? FilterType.values()[TileDetector.TYPE.getValue()] : ft).build();
    private int mode = MODE_EQUAL;
    private int amount = 0;

    private boolean powered = false;
    private boolean wasPowered;

    public NetworkNodeDetector(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.detectorUsage;
    }

    @Override
    public void updateNetworkNode() {
        super.updateNetworkNode();

        if (powered != wasPowered) {
            wasPowered = powered;

            world.notifyNeighborsOfStateChange(pos, RSBlocks.DETECTOR, true);

            WorldUtils.updateBlock(world, pos);
        }

        if (network != null && canUpdate() && ticks % SPEED == 0) {
            if (this.config.isFilterTypeItem()) {
                ItemStack slot = this.config.getItemHandler().getStackInSlot(0);

                if (!slot.isEmpty()) {
                    StackListEntry<ItemStack> stack = network.getItemStorageCache().getList().getEntry(slot, this.config.getCompare());

                    powered = isPowered(stack == null ? null : (int)stack.getCount());
                } else {
                    powered = isPowered(network.getItemStorageCache().getList().getStacks().stream().mapToInt(e -> e.getStack().getCount()).sum());
                }
            } else if (this.config.isFilterTypeFluid()) {
                FluidStack slot = this.config.getFluidHandler().getFluid(0);

                if (slot != null) {
                    StackListEntry<FluidStack> stack = network.getFluidStorageCache().getList().getEntry(slot, this.config.getCompare());

                    powered = isPowered(stack == null ? null : (int)stack.getCount());
                } else {
                    powered = isPowered(network.getFluidStorageCache().getList().getStacks().stream().mapToInt(e -> e.getStack().amount).sum());
                }
            }
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void onConnectedStateChange(INetwork network, boolean state) {
        super.onConnectedStateChange(network, state);

        if (!state) {
            powered = false;
        }
    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean isPowered(Integer size) {
        if (size != null) {
            switch (mode) {
                case MODE_UNDER:
                    return size < amount;
                case MODE_ABOVE:
                    return size > amount;
                case MODE_EQUAL:
                    return size == amount;
                default:
                    return false;
            }
        } else {
            if (mode == MODE_UNDER && amount != 0) {
                return true;
            } else return mode == MODE_EQUAL && amount == 0;
        }
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public NBTTagCompound writeConfiguration(NBTTagCompound tag) {
        super.writeConfiguration(tag);

        tag.setInteger(NBT_MODE, mode);
        tag.setInteger(NBT_AMOUNT, amount);

        tag.setTag("config", this.config.writeToNBT(new NBTTagCompound()));

        return tag;
    }

    @Override
    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);

        if (tag.hasKey(NBT_MODE)) {
            mode = tag.getInteger(NBT_MODE);
        }

        if (tag.hasKey(NBT_AMOUNT)) {
            amount = tag.getInteger(NBT_AMOUNT);
        }

        this.config.readFromNBT(tag.getCompoundTag("config"));
    }

    @Override
    public void setRedstoneMode(RedstoneMode mode) {
        // NO OP
    }

    @Nonnull
    @Override
    public FilterConfig getConfig() {
        return this.config;
    }
}
