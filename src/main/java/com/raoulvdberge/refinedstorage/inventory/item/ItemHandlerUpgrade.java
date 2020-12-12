package com.raoulvdberge.refinedstorage.inventory.item;

import com.raoulvdberge.refinedstorage.RSItems;
import com.raoulvdberge.refinedstorage.inventory.item.validator.ItemValidatorBasic;
import com.raoulvdberge.refinedstorage.item.ItemUpgrade;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class ItemHandlerUpgrade extends ItemHandlerBase {

    private final Int2IntMap upgradeCountMap = new Int2IntOpenHashMap();

    private int energyUsage;
    private int fortuneLevel;

    public ItemHandlerUpgrade(int size, @Nullable Consumer<Integer> listener, int... supportedUpgrades) {
        super(size, listener, new ItemValidatorBasic[supportedUpgrades.length]);

        for (int i = 0; i < supportedUpgrades.length; ++i) {
            this.validators[i] = new ItemValidatorBasic(RSItems.UPGRADE, supportedUpgrades[i]);
        }
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        this.energyUsage = 0;
        this.fortuneLevel = 0;
        this.upgradeCountMap.clear();

        for (int i = 0; i < getSlots(); ++i) {
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty())
                continue;

            this.energyUsage += ItemUpgrade.getEnergyUsage(stack);
            this.fortuneLevel = Math.max(this.fortuneLevel, ItemUpgrade.getFortuneLevel(stack));

            this.upgradeCountMap.merge(stack.getItemDamage(), 1, Integer::sum);
        }
    }

    public int getSpeed() {
        return getSpeed(9, 2);
    }

    public int getSpeed(int speed, int speedIncrease) {
        return speed - speedIncrease * this.upgradeCountMap.get(ItemUpgrade.TYPE_SPEED);
    }

    public boolean hasUpgrade(int type) {
        return getUpgradeCount(type) > 0;
    }

    public int getUpgradeCount(int type) {
        return this.upgradeCountMap.get(type);
    }

    public int getEnergyUsage() {
        return this.energyUsage;
    }

    public int getFortuneLevel() {
        return this.fortuneLevel;
    }

    public int getItemInteractCount() {
        return hasUpgrade(ItemUpgrade.TYPE_STACK) ? 64 : 1;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}
