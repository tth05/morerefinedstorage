package com.raoulvdberge.refinedstorage.apiimpl.autocrafting;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.registry.CraftingTaskFactory;
import com.raoulvdberge.refinedstorage.item.ItemPattern;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class CraftingPattern implements ICraftingPattern {
    private final ItemStack stack;
    private final boolean processing;
    private final boolean oredict;
    private boolean valid;
    private IRecipe recipe;
    private final List<NonNullList<ItemStack>> inputs = new ArrayList<>();
    private final NonNullList<ItemStack> outputs = NonNullList.create();
    private NonNullList<ItemStack> byproducts = NonNullList.create();
    private final NonNullList<FluidStack> fluidInputs = NonNullList.create();
    private final NonNullList<FluidStack> fluidOutputs = NonNullList.create();

    //Blacklisted items which this pattern cannot craft. 1x Wood -> 1x Wood (wood cannot be crafted because it gets used
    // up in the next iteration, it doesn't multiply). This is only relevant for processing patterns (hopefully)
    private final NonNullList<ItemStack> blacklistedItems = NonNullList.create();
    private final NonNullList<FluidStack> blacklistedFluids = NonNullList.create();

    //cache the hash code because crafting patterns are immutable and the calculation is slow
    private final int cachedHashCode;

    public CraftingPattern(World world, ItemStack stack) {
        this.stack = stack.copy();
        this.processing = ItemPattern.isProcessing(stack);
        this.oredict = ItemPattern.isOredict(stack);

        //How much of each input is in the input slots
        IStackList<ItemStack> itemInputCounts = API.instance().createItemStackList();
        IStackList<FluidStack> fluidInputCounts = API.instance().createFluidStackList();

        //How much of each output is in the output slots
        IStackList<ItemStack> itemOutputCounts = API.instance().createItemStackList();
        IStackList<FluidStack> fluidOutputCounts = API.instance().createFluidStackList();

        if (processing) {
            for (int i = 0; i < 9; ++i) {
                ItemStack input = ItemPattern.getInputSlot(stack, i);

                if (input != null && !input.isEmpty())
                    itemInputCounts.add(input);

                if (input == null) {
                    inputs.add(NonNullList.create());
                } else if (oredict) {
                    NonNullList<ItemStack> ores = NonNullList.create();

                    int[] oreIDs = OreDictionary.getOreIDs(input);

                    if (oreIDs.length == 0)
                        ores.add(input.copy());

                    for (int id : oreIDs) {
                        String name = OreDictionary.getOreName(id);

                        for (ItemStack ore : OreDictionary.getOres(name)) {
                            if (ore.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                                ore.getItem().getSubItems(CreativeTabs.SEARCH, ores);
                            } else {
                                ores.add(ore.copy());
                            }
                        }
                    }

                    // Fix item count
                    for (ItemStack ore : ores) {
                        ore.setCount(input.getCount());
                    }

                    inputs.add(ores);
                } else {
                    inputs.add(NonNullList.from(ItemStack.EMPTY, input));
                }

                ItemStack output = ItemPattern.getOutputSlot(stack, i);
                if (output != null) {
                    this.valid = true; // As soon as we have one output, we are valid.

                    itemOutputCounts.add(output);
                    outputs.add(output);
                }

                FluidStack fluidInput = ItemPattern.getFluidInputSlot(stack, i);
                if (fluidInput != null) {
                    this.valid = true;

                    fluidInputCounts.add(fluidInput);
                    fluidInputs.add(fluidInput);
                }

                FluidStack fluidOutput = ItemPattern.getFluidOutputSlot(stack, i);
                if (fluidOutput != null) {
                    this.valid = true;

                    fluidOutputCounts.add(fluidOutput);
                    fluidOutputs.add(fluidOutput);
                }
            }
        } else {
            InventoryCrafting inv = new InventoryCraftingDummy();

            for (int i = 0; i < 9; ++i) {
                ItemStack input = ItemPattern.getInputSlot(stack, i);

                inputs.add(input == null ? NonNullList.create() : NonNullList.from(ItemStack.EMPTY, input));

                if (input != null) {
                    inv.setInventorySlotContents(i, input);
                }
            }

            for (IRecipe r : CraftingManager.REGISTRY) {
                if (r.matches(inv, world)) {
                    this.recipe = r;

                    this.byproducts = recipe.getRemainingItems(inv);

                    ItemStack output = recipe.getCraftingResult(inv);

                    if (!output.isEmpty()) {
                        this.valid = true;

                        outputs.add(output);

                        if (oredict) {
                            if (!recipe.getIngredients().isEmpty()) {
                                inputs.clear();

                                for (int i = 0; i < recipe.getIngredients().size(); ++i) {
                                    inputs.add(i, NonNullList
                                            .from(ItemStack.EMPTY, recipe.getIngredients().get(i).getMatchingStacks()));
                                }
                            } else {
                                this.valid = false;
                            }
                        }
                    }

                    break;
                }
            }
        }

        //if any input is found in the outputs with exactly the same amount, then blacklist that output
        itemInputCounts.getStacks().forEach(e -> {
            StackListEntry<ItemStack> entry = itemOutputCounts.getEntry(e.getStack(), IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);
            if (entry != null && e.getCount() >= entry.getCount())
                this.blacklistedItems.add(e.getStack());
        });

        fluidInputCounts.getStacks().forEach(e -> {
            StackListEntry<FluidStack> entry = fluidOutputCounts.getEntry(e.getStack(), IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);
            if (entry != null && e.getCount() >= entry.getCount())
                this.blacklistedFluids.add(e.getStack());
        });

        if (!isProcessing()) {
            this.blacklistedItems.clear();
            this.blacklistedFluids.clear();
        }

        //if all items and fluids are blacklisted, then this pattern is useless
        if (blacklistedFluids.size() == fluidOutputs.size() && blacklistedItems.size() == inputs.size())
            this.valid = false;

        this.cachedHashCode = getHashCode();
    }

    @Override
    public ItemStack getStack() {
        return stack;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public boolean isProcessing() {
        return processing;
    }

    @Override
    public boolean isOredict() {
        return oredict;
    }

    @Override
    public List<NonNullList<ItemStack>> getInputs() {
        return inputs;
    }

    @Override
    public NonNullList<ItemStack> getOutputs() {
        return outputs;
    }

    public ItemStack getOutput(NonNullList<ItemStack> took) {
        if (processing) {
            throw new IllegalStateException("Cannot get crafting output from processing pattern");
        }

        if (took.size() != inputs.size()) {
            throw new IllegalArgumentException(
                    "The items that are taken (" + took.size() + ") should match the inputs for this pattern (" +
                    inputs.size() + ")");
        }

        InventoryCrafting inv = new InventoryCraftingDummy();

        for (int i = 0; i < took.size(); ++i) {
            inv.setInventorySlotContents(i, took.get(i));
        }

        ItemStack result = recipe.getCraftingResult(inv);
        if (result.isEmpty()) {
            throw new IllegalStateException("Cannot have empty result");
        }

        return result;
    }

    @Override
    public NonNullList<ItemStack> getByproducts() {
        if (processing) {
            throw new IllegalStateException("Cannot get byproduct outputs from processing pattern");
        }

        return byproducts;
    }

    @Override
    public NonNullList<ItemStack> getByproducts(NonNullList<ItemStack> took) {
        if (processing) {
            throw new IllegalStateException("Cannot get byproduct outputs from processing pattern");
        }

        if (took.size() != inputs.size()) {
            throw new IllegalArgumentException(
                    "The items that are taken (" + took.size() + ") should match the inputs for this pattern (" +
                    inputs.size() + ")");
        }

        InventoryCrafting inv = new InventoryCraftingDummy();

        for (int i = 0; i < took.size(); ++i) {
            inv.setInventorySlotContents(i, took.get(i));
        }

        NonNullList<ItemStack> remainingItems = recipe.getRemainingItems(inv);
        NonNullList<ItemStack> sanitized = NonNullList.create();

        for (ItemStack item : remainingItems) {
            if (!item.isEmpty()) {
                sanitized.add(item);
            }
        }

        return sanitized;
    }

    @Override
    public NonNullList<FluidStack> getFluidInputs() {
        return fluidInputs;
    }

    @Override
    public NonNullList<FluidStack> getFluidOutputs() {
        return fluidOutputs;
    }

    @Override
    public NonNullList<ItemStack> getBlacklistedItems() {
        return blacklistedItems;
    }

    @Override
    public NonNullList<FluidStack> getBlacklistedFluids() {
        return blacklistedFluids;
    }

    @Override
    public String getId() {
        return CraftingTaskFactory.ID;
    }

    @Override
    public boolean canBeInChainWith(ICraftingPattern other) {
        if (other.isProcessing() != processing || other.isOredict() != oredict) {
            return false;
        }

        if ((other.getInputs().size() != inputs.size()) ||
            (other.getFluidInputs().size() != fluidInputs.size()) ||
            (other.getOutputs().size() != outputs.size()) ||
            (other.getFluidOutputs().size() != fluidOutputs.size())) {
            return false;
        }

        if (!processing && other.getByproducts().size() != byproducts.size()) {
            return false;
        }

        for (int i = 0; i < inputs.size(); ++i) {
            List<ItemStack> inputPossibilities = this.inputs.get(i);
            List<ItemStack> otherInputs = other.getInputs().get(i);

            if (inputPossibilities.size() != otherInputs.size()) {
                return false;
            }

            for (int j = 0; j < inputPossibilities.size(); ++j) {
                if (!API.instance().getComparer().isEqual(inputPossibilities.get(j), otherInputs.get(j))) {
                    return false;
                }
            }
        }

        for (int i = 0; i < fluidInputs.size(); ++i) {
            if (!API.instance().getComparer().isEqual(fluidInputs.get(i), other.getFluidInputs().get(i),
                    IComparer.COMPARE_NBT | IComparer.COMPARE_QUANTITY)) {
                return false;
            }
        }

        for (int i = 0; i < outputs.size(); ++i) {
            if (!API.instance().getComparer().isEqual(outputs.get(i), other.getOutputs().get(i))) {
                return false;
            }
        }

        for (int i = 0; i < fluidOutputs.size(); ++i) {
            if (!API.instance().getComparer().isEqual(fluidOutputs.get(i), other.getFluidOutputs().get(i),
                    IComparer.COMPARE_NBT | IComparer.COMPARE_QUANTITY)) {
                return false;
            }
        }

        if (!processing) {
            for (int i = 0; i < byproducts.size(); ++i) {
                if (!API.instance().getComparer().isEqual(byproducts.get(i), other.getByproducts().get(i))) {
                    return false;
                }
            }
        }

        return true;
    }

    public int getHashCode() {
        int result = (processing ? 1 : 0);

        result = 31 * result + (oredict ? 1 : 0);

        for (List<ItemStack> inputPossibilities : this.inputs) {
            for (ItemStack input : inputPossibilities) {
                result = 31 * result + API.instance().getItemStackHashCode(input);
            }
        }

        for (FluidStack input : this.fluidInputs) {
            result = 31 * result + API.instance().getFluidStackHashCode(input);
        }

        for (ItemStack output : this.outputs) {
            result = 31 * result + API.instance().getItemStackHashCode(output);
        }

        for (FluidStack output : this.fluidOutputs) {
            result = 31 * result + API.instance().getFluidStackHashCode(output);
        }

        for (ItemStack byproduct : this.byproducts) {
            result = 31 * result + API.instance().getItemStackHashCode(byproduct);
        }

        return result;
    }

    @Override
    public int hashCode() {
        return this.cachedHashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CraftingPattern) {
            return canBeInChainWith((CraftingPattern) obj);
        }
        return false;
    }

    @Override
    public String toString() {
        return "CraftingPattern{" +
               "processing=" + processing +
               ", oredict=" + oredict +
               ", valid=" + valid +
               ", recipe=" + recipe +
               ", inputs=" + inputs +
               ", outputs=" + outputs +
               ", byproducts=" + byproducts +
               ", fluidInputs=" + fluidInputs +
               ", fluidOutputs=" + fluidOutputs +
               ", blacklistedItems=" + blacklistedItems +
               ", blacklistedFluids=" + blacklistedFluids +
               '}';
    }

    private static class InventoryCraftingDummy extends InventoryCrafting {
        public InventoryCraftingDummy() {
            super(new Container() {
                @Override
                public boolean canInteractWith(@Nonnull EntityPlayer player) {
                    return true;
                }
            }, 3, 3);
        }
    }
}
