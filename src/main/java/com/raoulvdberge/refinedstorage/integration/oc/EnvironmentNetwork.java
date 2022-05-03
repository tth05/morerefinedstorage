package com.raoulvdberge.refinedstorage.integration.oc;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IStorageExternal;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.*;
import java.util.function.Function;

import static com.raoulvdberge.refinedstorage.api.util.IComparer.COMPARE_DAMAGE;
import static com.raoulvdberge.refinedstorage.api.util.IComparer.COMPARE_NBT;

public class EnvironmentNetwork extends AbstractManagedEnvironment {

    protected final INetworkNode node;

    private static final String ERROR_NOT_CONNECTED = "not connected";

    public EnvironmentNetwork(INetworkNode node) {
        this.node = node;

        setNode(Network.newNode(this, Visibility.Network).withComponent("refinedstorage", Visibility.Network).create());
    }

    @Callback(doc = "function():boolean -- Whether the node is connected.")
    public Object[] isConnected(final Context context, final Arguments args) {
        return new Object[]{node.canUpdate()};
    }

    @Callback(doc = "function():number -- Gets the energy usage of this network.")
    public Object[] getEnergyUsage(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        return new Object[]{node.getNetwork().getEnergyUsage()};
    }

    @Callback(doc = "function():table -- Gets the crafting tasks of this network.")
    public Object[] getTasks(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        return new Object[]{node.getNetwork().getCraftingManager().getTasks()};
    }

    @Callback(doc = "function(stack:table):table -- Get one pattern of this network.")
    public Object[] getPattern(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        ItemStack stack = args.checkItemStack(0);
        return new Object[]{node.getNetwork().getCraftingManager().getPattern(stack)};
    }

    @Callback(doc = "function(stack:table):table -- Get one fluid pattern of this network.")
    public Object[] getFluidPattern(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        FluidStack stack = checkFluid(args.checkTable(0), 1000);
        return new Object[]{node.getNetwork().getCraftingManager().getPattern(stack)};
    }

    @Callback(doc = "function():table -- Gets the patterns of this network.")
    public Object[] getPatterns(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        List<ItemStack> patterns = new LinkedList<>();
        for (ICraftingPattern pattern : node.getNetwork().getCraftingManager().getPatterns()) {
            if (!pattern.getOutputs().isEmpty()) {
                patterns.addAll(pattern.getOutputs());
            }
        }

        return new Object[]{patterns};
    }

    @Callback(doc = "function():table -- Gets the fluid patterns of this network.")
    public Object[] getFluidPatterns(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        List<FluidStack> patterns = new LinkedList<>();
        for (ICraftingPattern pattern : node.getNetwork().getCraftingManager().getPatterns()) {
            if (!pattern.getFluidOutputs().isEmpty()) {
                patterns.addAll(pattern.getFluidOutputs());
            }
        }

        return new Object[]{patterns};
    }

    @Callback(doc = "function(stack:table):boolean -- Whether a crafting pattern exists for this item.")
    public Object[] hasPattern(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        ItemStack stack = args.checkItemStack(0);

        return new Object[]{node.getNetwork().getCraftingManager().getPattern(stack) != null};
    }

    @Callback(doc = "function(stack:table):boolean -- Whether a crafting pattern exists for this fluid.")
    public Object[] hasFluidPattern(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        FluidStack stack = checkFluid(args.checkTable(0), 1000);

        return new Object[]{node.getNetwork().getCraftingManager().getPattern(stack) != null};
    }

    @Callback(doc = "function(stack:table[, count: number[, canSchedule: boolean]]):table -- Schedules a crafting task.")
    public Object[] scheduleTask(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{ERROR_NOT_CONNECTED};
        }

        ItemStack stack = args.checkItemStack(0);
        int amount = args.optInteger(1, 1);

        ICraftingTask task = node.getNetwork().getCraftingManager().create(stack, amount);
        if (task == null) {
            throw new IllegalArgumentException("Could not create crafting task");
        }

        ICraftingTaskError error;
        try {
            error = task.calculate();
        } catch (Throwable t) {
            t.printStackTrace();
            task.onCancelled();
            return new Object[]{};
        }

        if (error == null && !task.hasMissing() && args.optBoolean(2, true)) {
            task.setCanUpdate(true);
            node.getNetwork().getCraftingManager().add(task);
        }

        return new Object[]{task};
    }

    @Callback(doc = "function(stack:table[, count: number[, canSchedule: boolean]]):table -- Schedules a fluid crafting task.")
    public Object[] scheduleFluidTask(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{ERROR_NOT_CONNECTED};
        }

        FluidStack stack = checkFluid(args.checkTable(0), args.optInteger(1, Fluid.BUCKET_VOLUME));

        ICraftingTask task = node.getNetwork().getCraftingManager().create(stack, stack.amount);
        if (task == null) {
            throw new IllegalArgumentException("Could not create crafting task");
        }

        ICraftingTaskError error;
        try {
            error = task.calculate();
        } catch (Throwable t) {
            t.printStackTrace();
            task.onCancelled();
            return new Object[]{};
        }

        if (error == null && !task.hasMissing() && args.optBoolean(2, true)) {
            task.setCanUpdate(true);
            node.getNetwork().getCraftingManager().add(task);
        }

        return new Object[]{task};
    }

    @Callback(doc = "function(stack:table):number -- Cancels a task and returns the amount of tasks cancelled.")
    public Object[] cancelTask(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        ItemStack stack = args.checkItemStack(0);

        int count = 0;
        for (ICraftingTask task : node.getNetwork().getCraftingManager().getTasks()) {
            if (task.getRequested().getItem() != null) {
                if (API.instance().getComparer().isEqual(task.getRequested().getItem(), stack, COMPARE_NBT | COMPARE_DAMAGE)) {
                    node.getNetwork().getCraftingManager().cancel(task.getId());

                    count++;
                }
            }
        }

        return new Object[]{count};
    }

    @Callback(doc = "function(stack:table):number -- Cancels a fluid task and returns the amount of tasks cancelled.")
    public Object[] cancelFluidTask(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        FluidStack stack = checkFluid(args.checkTable(0), 1000);

        int count = 0;
        for (ICraftingTask task : node.getNetwork().getCraftingManager().getTasks()) {
            if (task.getRequested().getFluid() != null) {
                if (API.instance().getComparer().isEqual(task.getRequested().getFluid(), stack, COMPARE_NBT)) {
                    node.getNetwork().getCraftingManager().cancel(task.getId());

                    count++;
                }
            }
        }

        return new Object[]{count};
    }

    @Callback(doc = "function(stack:table[, amount:number[, direction:number]]):table -- Extracts a fluid from the network.")
    public Object[] extractFluid(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        // First and second argument: fluid and amount.
        FluidStack stack = checkFluid(args.checkTable(0), args.checkInteger(1));

        // Third argument: which direction to extract to
        EnumFacing facing = EnumFacing.byIndex(args.optInteger(2, 0));

        // Get the tile-entity on the specified side
        TileEntity targetEntity = node.getNetwork().world().getTileEntity(node.getNetworkNodePos().offset(facing));
        if (targetEntity == null || !targetEntity.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing.getOpposite())) {
            throw new IllegalArgumentException("No fluid tank on the given side");
        }

        FluidStack extractedSim = node.getNetwork().extractFluid(stack, stack.amount, Action.SIMULATE);
        if (extractedSim == null || extractedSim.amount <= 0) {
            return new Object[]{null, "could not extract the specified fluid"};
        }

        // Simulate inserting the fluid and see how much we were able to insert
        IFluidHandler handler = targetEntity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing.getOpposite());
        int filledAmountSim = handler.fill(extractedSim, false);
        if (filledAmountSim <= 0) {
            return new Object[]{0};
        }

        // Actually do it and return how much fluid we've inserted
        FluidStack extractedActual = node.getNetwork().extractFluid(stack, filledAmountSim, Action.PERFORM);
        int filledAmountActual = handler.fill(extractedActual, true);

        // Attempt to insert excess fluid back into the network
        // This shouldn't need to happen for most tanks, unless input cap decreases based on insert amount
        if (extractedActual != null && extractedActual.amount > filledAmountActual) {
            node.getNetwork().insertFluid(stack, extractedActual.amount - filledAmountActual, Action.PERFORM);
        }

        return new Object[]{filledAmountActual};
    }

    @Callback(doc = "function(stack:table):table -- Gets a fluid from the network.")
    public Object[] getFluid(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        FluidStack needle = checkFluid(args.checkTable(0), 1000);

        return new Object[]{node.getNetwork().getFluidStorageCache().getList().getEntry(needle, COMPARE_NBT)};
    }

    @Callback(doc = "function():table -- Gets a list of all fluids in this network.")
    public Object[] getFluids(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        List<Map<String, Object>> result = new ObjectArrayList<>();
        for (StackListEntry<FluidStack> entry : node.getNetwork().getFluidStorageCache().getList().getStacks()) {
            list.add(serializeFluidStackListEntry(entry));
        }

        return new Object[]{result};
    }

    @Callback(doc = "function(stack:table[, count:number[, direction:number]]):table -- Extracts an item from the network.")
    public Object[] extractItem(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        // First argument: the itemstack to extract
        ItemStack stack = args.checkItemStack(0);

        // Second argument: the number of items to extract, at least 1 ...
        int count = Math.max(1, args.optInteger(1, 1));

        // ... and at most a full stack
        count = Math.min(count, stack.getMaxStackSize());

        // Third argument: which direction to extract to
        EnumFacing facing = EnumFacing.byIndex(args.optInteger(2, 0));

        // Get the tile-entity on the specified side
        TileEntity targetEntity = node.getNetwork().world().getTileEntity(node.getNetworkNodePos().offset(facing));
        if (targetEntity == null || !targetEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
            throw new IllegalArgumentException("No inventory on the given side");
        }

        // Simulate extracting the item and get the amount of items that can be extracted
        StackListResult<ItemStack> extracted = node.getNetwork().extractItem(stack, (long) count, Action.SIMULATE);
        if (extracted == null) {
            return new Object[]{null, "could not extract the specified item"};
        }

        long transferableAmount = extracted.getCount();

        // Simulate inserting the item and see how many we were able to insert
        IItemHandler handler = targetEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, extracted.getFixedStack(), true);
        if (!remainder.isEmpty()) {
            transferableAmount -= remainder.getCount();
        }

        // Abort early if we can not insert items
        if (transferableAmount <= 0) {
            return new Object[]{0};
        }

        // Actually do it and return how many items we've inserted
        extracted = node.getNetwork().extractItem(stack, transferableAmount, Action.PERFORM);
        if (extracted != null) {
            remainder = ItemHandlerHelper.insertItemStacked(handler, extracted.getFixedStack(), false);

            if (!remainder.isEmpty()) {
                node.getNetwork().insertItem(remainder, (long) remainder.getCount(), Action.PERFORM);
            }
        }

        return new Object[]{transferableAmount};
    }

    @Callback(doc = "function(stack:table[, compareMeta:boolean[, compareNBT:boolean]]):table -- Gets an item from the network.")
    public Object[] getItem(final Context context, final Arguments args) {
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        ItemStack stack = args.checkItemStack(0);
        boolean compareMeta = args.optBoolean(1, true);
        boolean compareNBT = args.optBoolean(2, true);

        int flags = 0;

        if (compareMeta) {
            flags |= IComparer.COMPARE_DAMAGE;
        }

        if (compareNBT) {
            flags |= IComparer.COMPARE_NBT;
        }
        StackListEntry<ItemStack> entry = node.getNetwork().getItemStorageCache().getList().getEntry(stack, flags);
        if (entry == null)
            return new Object[]{null};

        return new Object[]{serializeItemStackStackListEntry(entry)};
    }

    //performance
    private final List<Map<String, Object>> list = new ObjectArrayList<>();

    @Callback(doc = "function():table -- Gets a list of all items in this network.")
    public Object[] getItems(final Context context, final Arguments args) {
        list.clear();
        if (node.getNetwork() == null) {
            return new Object[]{null, ERROR_NOT_CONNECTED};
        }

        for (StackListEntry<ItemStack> entry : node.getNetwork().getItemStorageCache().getList().getStacks()) {
            list.add(serializeItemStackStackListEntry(entry));
        }

        return new Object[]{list};
    }

    @Callback(doc = "function():table -- Gets a list of all connected storage disks and blocks in this network.")
    public Object[] getStorages(final Context context, final Arguments args) {
        long totalItemStored = 0;
        long totalItemCapacity = 0;

        long totalFluidStored = 0;
        long totalFluidCapacity = 0;

        List<Map<String, Object>> devices = new ArrayList<>();

        Function<IStorage<?>, Long> getCapacity = (storage) -> {
            if (storage instanceof IStorageDisk) {
                return ((IStorageDisk<?>) storage).getCapacity();
            } else if (storage instanceof IStorageExternal) {
                return ((IStorageExternal<?>) storage).getCapacity();
            }

            throw new IllegalArgumentException();
        };

        if (node.getNetwork() != null) {
            for (IStorage<?> s : node.getNetwork().getItemStorageCache().getStorages()) {
                if (!(s instanceof IStorageDisk) && !(s instanceof IStorageExternal))
                    continue;

                Map<String, Object> data = new HashMap<>();
                data.put("type", "item");
                data.put("usage", s.getStored());

                totalItemStored += s.getStored();

                long capacity = getCapacity.apply(s);

                data.put("capacity", capacity);
                totalItemCapacity += capacity;
                devices.add(data);
            }

            for (IStorage<?> s : node.getNetwork().getFluidStorageCache().getStorages()) {
                if (!(s instanceof IStorageDisk) && !(s instanceof IStorageExternal))
                    continue;

                Map<String, Object> data = new HashMap<>();
                data.put("type", "fluid");
                data.put("usage", s.getStored());

                totalItemStored += s.getStored();

                long capacity = getCapacity.apply(s);

                data.put("capacity", capacity);
                totalItemCapacity += capacity;
                devices.add(data);
            }
        }

        Map<String, Long> itemTotals = new HashMap<>();
        itemTotals.put("usage", totalItemStored);
        itemTotals.put("capacity", totalItemCapacity);

        Map<String, Long> fluidTotals = new HashMap<>();
        fluidTotals.put("usage", totalFluidStored);
        fluidTotals.put("capacity", totalFluidCapacity);

        Map<String, Object> totals = new HashMap<>();
        totals.put("item", itemTotals);
        totals.put("fluid", fluidTotals);

        Map<String, Object> response = new HashMap<>();
        response.put("total", totals);
        response.put("devices", devices);

        return new Object[]{response};
    }

    private FluidStack checkFluid(Map<String, Object> fluidMap, int amount) {
        if (!fluidMap.containsKey("name") || !(fluidMap.get("name") instanceof String) || ((String) fluidMap.get("name")).length() == 0) {
            throw new IllegalArgumentException("no fluid name");
        }
        String fluid = (String) fluidMap.get("name");

        amount = Math.max(1, amount);

        FluidStack stack = FluidRegistry.getFluidStack(fluid, amount);
        if (stack == null) {
            throw new IllegalArgumentException("invalid fluid stack, does not exist");
        }

        return stack;
    }

    static Map<String, Object> serializeItemStackStackListEntry(StackListEntry<ItemStack> entry) {
        Map<String, Object> map = new HashMap<>();
        ItemStack stack = entry.getStack();

        //@Volatile: copied from OpenComputers, adjusted to allow for a long as the size. This is missing various entries
        map.put("damage", stack.getItemDamage());
        map.put("maxDamage", stack.getMaxDamage());
        map.put("size", entry.getCount());
        map.put("maxSize", stack.getMaxStackSize());
        map.put("name", Item.REGISTRY.getNameForObject(stack.getItem()));
        map.put("label", stack.getDisplayName());

        return map;
    }

    static Map<String, Object> serializeFluidStackListEntry(StackListEntry<FluidStack> entry) {
        Map<String, Object> map = new HashMap<>();
        map.put("size", entry.getCount());
        map.put("stack", entry.getStack());
        return map;
    }
}
