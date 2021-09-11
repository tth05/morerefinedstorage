package com.raoulvdberge.refinedstorage.apiimpl.util;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class Comparer implements IComparer {

    @Override
    public boolean isEqual(@Nullable ItemStack left, @Nullable ItemStack right, int flags) {
        if (left == null && right == null) {
            return true;
        }

        if (left == null || right == null) {
            return false;
        }

        boolean leftEmpty = left.isEmpty();
        boolean rightEmpty = right.isEmpty();
        if (leftEmpty && rightEmpty) {
            return true;
        }

        if (leftEmpty || rightEmpty) {
            return false;
        }

        if (left == right) {
            return true;
        }

        if (left.getItem() != right.getItem()) {
            return false;
        }

        if ((flags & COMPARE_NBT) == COMPARE_NBT) {
            boolean leftTagEmpty = left.getTagCompound() == null || left.getTagCompound().isEmpty();
            boolean rightTagEmpty = right.getTagCompound() == null || right.getTagCompound().isEmpty();
            boolean ignoreCapabilities = (flags & IGNORE_CAPABILITIES) == IGNORE_CAPABILITIES;
            if (leftTagEmpty) {
                if (!rightTagEmpty || !(ignoreCapabilities || left.areCapsCompatible(right)))
                    return false;
            } else {
                if (!left.getTagCompound().equals(right.getTagCompound()) || !(ignoreCapabilities || left.areCapsCompatible(right)))
                    return false;
            }
        }

        if ((flags & COMPARE_DAMAGE) == COMPARE_DAMAGE && left.getItemDamage() != right.getItemDamage()) {
            return false;
        }

        return (flags & COMPARE_QUANTITY) != COMPARE_QUANTITY || left.getCount() == right.getCount();
    }

    @Override
    public boolean isEqual(@Nullable FluidStack left, @Nullable FluidStack right, int flags) {
        if (left == null && right == null) {
            return true;
        }

        if (left == null || right == null) {
            return false;
        }

        if (left.getFluid() != right.getFluid()) {
            return false;
        }

        if (left == right) {
            return true;
        }

        if ((flags & COMPARE_NBT) == COMPARE_NBT && !FluidStack.areFluidStackTagsEqual(left, right)) {
            return false;
        }

        if ((flags & COMPARE_QUANTITY) == COMPARE_QUANTITY && left.amount != right.amount) {
            return false;
        }

        return true;
    }
}
