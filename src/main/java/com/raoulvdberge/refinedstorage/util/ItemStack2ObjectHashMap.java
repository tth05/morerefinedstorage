package com.raoulvdberge.refinedstorage.util;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

public class ItemStack2ObjectHashMap<V> extends Object2ObjectOpenCustomHashMap<ItemStack, V> {

    public ItemStack2ObjectHashMap() {
        super(ItemStackHashingStrategy.INSTANCE);
    }

    private static class ItemStackHashingStrategy implements Hash.Strategy<ItemStack> {

        private static final ItemStackHashingStrategy INSTANCE = new ItemStackHashingStrategy();

        @Override
        public int hashCode(ItemStack object) {
            NBTTagCompound nbt = object.getTagCompound();

            int hashCode = 31 + Boolean.hashCode(object.isEmpty());
            hashCode = 31 * hashCode + object.getItem().hashCode();
            hashCode = 31 * hashCode + (nbt == null ? 0 : nbt.hashCode());
            hashCode = 31 * hashCode + object.getItemDamage();
            return hashCode;
        }

        @Override
        public boolean equals(ItemStack o1, ItemStack o2) {
            return (o1 == null && o2 == null) ||
                    (o1 != null && o2 != null &&
                            o1.isEmpty() == o2.isEmpty() &&
                            o1.getItemDamage() == o2.getItemDamage() &&
                            o1.getItem().equals(o2.getItem()) &&
                            Objects.equals(o1.getTagCompound(), o2.getTagCompound()));
        }
    }
}
