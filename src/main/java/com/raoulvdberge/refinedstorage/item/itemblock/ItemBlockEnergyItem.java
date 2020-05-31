package com.raoulvdberge.refinedstorage.item.itemblock;

import com.raoulvdberge.refinedstorage.block.BlockBase;
import com.raoulvdberge.refinedstorage.item.capprovider.CapabilityProviderEnergy;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public abstract class ItemBlockEnergyItem extends ItemBlockBase {
    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_CREATIVE = 1;

    private final int energyCapacity;

    public ItemBlockEnergyItem(BlockBase block, int energyCapacity) {
        super(block, true);

        this.energyCapacity = energyCapacity;

        setMaxDamage(energyCapacity);
        setMaxStackSize(1);
    }

    @Override
    public ICapabilityProvider initCapabilities(@Nonnull ItemStack stack, NBTTagCompound tag) {
        return new CapabilityProviderEnergy(stack, energyCapacity);
    }

    @Override
    public boolean isDamageable() {
        return true;
    }

    @Override
    public boolean isRepairable() {
        return false;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        IEnergyStorage energy = stack.getCapability(CapabilityEnergy.ENERGY, null);

        return 1D - ((double) energy.getEnergyStored() / (double) energy.getMaxEnergyStored());
    }

    @Override
    public int getRGBDurabilityForDisplay(ItemStack stack) {
        IEnergyStorage energy = stack.getCapability(CapabilityEnergy.ENERGY, null);

        return MathHelper.hsvToRGB(Math.max(0.0F, (float) energy.getEnergyStored() / (float) energy.getMaxEnergyStored()) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean isDamaged(ItemStack stack) {
        return stack.getItemDamage() != TYPE_CREATIVE;
    }

    @Override
    public void setDamage(@Nonnull ItemStack stack, int damage) {
        // NO OP
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) {
            return;
        }

        items.add(new ItemStack(this, 1, TYPE_NORMAL));

        ItemStack fullyCharged = new ItemStack(this, 1, TYPE_NORMAL);

        IEnergyStorage energy = fullyCharged.getCapability(CapabilityEnergy.ENERGY, null);
        energy.receiveEnergy(energy.getMaxEnergyStored(), false);

        items.add(fullyCharged);

        items.add(new ItemStack(this, 1, TYPE_CREATIVE));
    }

    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        if (stack.getItemDamage() != TYPE_CREATIVE) {
            IEnergyStorage energy = stack.getCapability(CapabilityEnergy.ENERGY, null);

            tooltip.add(I18n.format("misc.refinedstorage:energy_stored", energy.getEnergyStored(), energy.getMaxEnergyStored()));
        }
    }
}
