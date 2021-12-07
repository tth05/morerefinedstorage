package com.raoulvdberge.refinedstorage.integration.oc;

import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import li.cil.oc.api.driver.Converter;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Map;

public class ConverterCraftingTask implements Converter {

    @Override
    public void convert(Object value, Map<Object, Object> output) {
        if (value instanceof ICraftingTask) {
            ICraftingTask task = (ICraftingTask) value;

            List<Map<String, Object>> missingItemStacks = new ObjectArrayList<>();
            for (StackListEntry<ItemStack> entry : task.getMissing().getStacks()) {
                missingItemStacks.add(EnvironmentNetwork.serializeItemStackStackListEntry(entry));
            }

            List<Map<String, Object>> missingFluidStacks = new ObjectArrayList<>();
            for (StackListEntry<FluidStack> entry : task.getMissingFluids().getStacks()) {
                missingFluidStacks.add(EnvironmentNetwork.serializeFluidStackListEntry(entry));
            }

            output.put("stack", task.getRequested());
            output.put("missing", missingItemStacks);
            output.put("missingFluids", missingFluidStacks);
            output.put("pattern", task.getPattern());
            output.put("quantity", task.getQuantity());
        }
    }
}
