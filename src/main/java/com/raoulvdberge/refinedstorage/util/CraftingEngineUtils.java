package com.raoulvdberge.refinedstorage.util;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Input;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Output;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.RestockableInput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class CraftingEngineUtils {

    @Nullable
    public static Output findMatchingItemOutput(@Nonnull List<Output> outputs, @Nonnull ItemStack toMatch) {
        for (int i = 0; i < outputs.size(); i++) {
            Output o = outputs.get(i);
            if (!o.isFluid() &&
                API.instance().getComparer().isEqualNoQuantity(o.getCompareableItemStack(), toMatch)) {
                return o;
            }
        }

        return null;
    }

    @Nullable
    public static Output findMatchingFluidOutput(@Nonnull List<Output> outputs, @Nonnull FluidStack toMatch) {
        for (int i = 0; i < outputs.size(); i++) {
            Output o = outputs.get(i);
            if (o.isFluid() &&
                API.instance().getComparer().isEqual(o.getFluidStack(), toMatch, IComparer.COMPARE_NBT)) {
                return o;
            }
        }

        return null;
    }

    @Nullable
    public static RestockableInput findMatchingRestockableItemInput(@Nonnull List<Input> inputs, @Nonnull ItemStack toMatch) {
        for (int i = 0; i < inputs.size(); i++) {
            Input input = inputs.get(i);
            if (input instanceof RestockableInput && !input.isFluid() &&
                API.instance().getComparer().isEqualNoQuantity(input.getCompareableItemStack(), toMatch)) {
                return (RestockableInput) input;
            }
        }

        return null;
    }

    @Nullable
    public static RestockableInput findMatchingRestockableFluidInput(@Nonnull List<Input> inputs, @Nonnull FluidStack toMatch) {
        for (int i = 0; i < inputs.size(); i++) {
            Input input = inputs.get(i);
            if (input instanceof RestockableInput && input.isFluid() &&
                API.instance().getComparer().isEqual(input.getFluidStack(), toMatch, IComparer.COMPARE_NBT)) {
                return (RestockableInput) input;
            }
        }

        return null;
    }
}
