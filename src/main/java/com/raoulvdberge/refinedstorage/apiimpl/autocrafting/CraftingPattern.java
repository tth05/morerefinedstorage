package com.raoulvdberge.refinedstorage.apiimpl.autocrafting;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.CraftingTaskFactory;
import com.raoulvdberge.refinedstorage.apiimpl.util.OneSixMigrationHelper;
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
    private ICraftingPatternContainer container;
    private ItemStack stack;
    private boolean processing;
    private boolean oredict;
    private boolean valid;
    private IRecipe recipe;
    private final List<NonNullList<ItemStack>> inputs = new ArrayList<>();
    private final NonNullList<ItemStack> outputs = NonNullList.create();
    private NonNullList<ItemStack> byproducts = NonNullList.create();
    private final NonNullList<FluidStack> fluidInputs = NonNullList.create();
    private final NonNullList<FluidStack> fluidOutputs = NonNullList.create();

    //blacklisted items which this pattern cannot craft. 1x Wood -> 1x Wood (wood cannot be crafted because it gets used
    // up in the next iteration, it doesn't multiply)
    private final NonNullList<ItemStack> blacklistedItems = NonNullList.create();
    private final NonNullList<FluidStack> blacklistedFluids = NonNullList.create();

    public CraftingPattern(World world, ICraftingPatternContainer container, ItemStack stack) {
        if (!OneSixMigrationHelper.isValidOneSixPattern(stack)) {
            this.valid = false;

            return;
        }

        this.container = container;
        this.stack = stack;
        this.processing = ItemPattern.isProcessing(stack);
        this.oredict = ItemPattern.isOredict(stack);

        if (processing) {
            for (int i = 0; i < 9; ++i) {
                ItemStack input = ItemPattern.getInputSlot(stack, i);

                if (input == null) {
                    inputs.add(NonNullList.create());
                } else if (oredict) {
                    NonNullList<ItemStack> ores = NonNullList.create();

                    ores.add(input.copy());

                    for (int id : OreDictionary.getOreIDs(input)) {
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

                    outputs.add(output);
                }

                FluidStack fluidInput = ItemPattern.getFluidInputSlot(stack, i);
                if (fluidInput != null) {
                    this.valid = true;

                    fluidInputs.add(fluidInput);
                }

                FluidStack fluidOutput = ItemPattern.getFluidOutputSlot(stack, i);
                if (fluidOutput != null) {
                    this.valid = true;

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
                            if (recipe.getIngredients().size() > 0) {
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
        for (NonNullList<ItemStack> input : this.inputs) {
            for (ItemStack possibleItemStack : input) {
                for (ItemStack output : this.outputs) {
                    if (API.instance().getComparer()
                            .isEqual(possibleItemStack, output, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE)
                            && possibleItemStack.getCount() >= output.getCount()) {
                        blacklistedItems.add(output);
                    }
                }
            }
        }

        for (FluidStack fluidInput : this.fluidInputs) {
            for (FluidStack fluidOutput : this.fluidOutputs) {
                if (API.instance().getComparer().isEqual(fluidInput, fluidOutput, IComparer.COMPARE_NBT)
                        && fluidInput.amount >= fluidOutput.amount) {
                    blacklistedFluids.add(fluidOutput);
                    return;
                }
            }
        }

        //if all items and fluids are blacklisted, then this pattern is useless
        if (blacklistedFluids.size() == fluidOutputs.size() && blacklistedItems.size() == inputs.size())
            this.valid = false;
    }

    @Override
    public ICraftingPatternContainer getContainer() {
        return container;
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
            List<ItemStack> inputs = this.inputs.get(i);
            List<ItemStack> otherInputs = other.getInputs().get(i);

            if (inputs.size() != otherInputs.size()) {
                return false;
            }

            for (int j = 0; j < inputs.size(); ++j) {
                if (!API.instance().getComparer().isEqual(inputs.get(j), otherInputs.get(j))) {
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

    @Override
    public int getChainHashCode() {
        int result = 0;

        result = 31 * result + (processing ? 1 : 0);
        result = 31 * result + (oredict ? 1 : 0);

        for (List<ItemStack> inputs : this.inputs) {
            for (ItemStack input : inputs) {
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
        return getChainHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CraftingPattern) {
            return canBeInChainWith((CraftingPattern) obj);
        }
        return false;
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
