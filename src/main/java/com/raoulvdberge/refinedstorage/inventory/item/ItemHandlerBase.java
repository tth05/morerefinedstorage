package com.raoulvdberge.refinedstorage.inventory.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public class ItemHandlerBase extends ItemStackHandler {
    @Nullable
    private final IntConsumer listener;

    private boolean empty = true;

    protected final Predicate<ItemStack>[] validators;

    @SafeVarargs
    public ItemHandlerBase(int size, @Nullable IntConsumer listener, Predicate<ItemStack>... validators) {
        super(size);

        this.listener = listener;
        this.validators = validators;
    }

    @SafeVarargs
    public ItemHandlerBase(int size, Predicate<ItemStack>... validators) {
        this(size, null, validators);
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (validators.length > 0) {
            for (Predicate<ItemStack> validator : validators) {
                if (validator.test(stack)) {
                    return super.insertItem(slot, stack, simulate);
                }
            }

            return stack;
        }

        return super.insertItem(slot, stack, simulate);
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);

        if (listener != null) {
            listener.accept(slot);
        }

        this.empty = stacks.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public void deserializeNBT(NBTTagCompound tag) {
        super.deserializeNBT(tag);

        this.empty = stacks.stream().allMatch(ItemStack::isEmpty);
    }

    public boolean isEmpty() {
        return empty;
    }
}
