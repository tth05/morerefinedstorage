package com.raoulvdberge.refinedstorage.apiimpl.autocrafting;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingManager;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorListener;
import com.raoulvdberge.refinedstorage.api.autocrafting.registry.ICraftingTaskFactory;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.util.OneSixMigrationHelper;
import com.raoulvdberge.refinedstorage.tile.TileController;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CraftingManager implements ICraftingManager {
    private static final int THROTTLE_DELAY_MS = 3000;

    private static final String NBT_TASKS = "Tasks";
    private static final String NBT_TASK_TYPE = "Type";
    private static final String NBT_TASK_DATA = "Task";

    public static final ExecutorService CALCULATION_THREAD_POOL = Executors.newFixedThreadPool(4,
            new ThreadFactoryBuilder().setNameFormat("RS Calculation Thread %d").build());

    private final TileController network;

    private final Map<String, List<IItemHandlerModifiable>> containerInventories = new LinkedHashMap<>();
    private final Map<ICraftingPattern, Set<ICraftingPatternContainer>> patternToContainer = new HashMap<>();

    private final List<ICraftingPattern> patterns = new ArrayList<>();

    private final Map<UUID, ICraftingTask> tasks = new LinkedHashMap<>();
    private final List<ICraftingTask> tasksToAdd = new ArrayList<>();
    private final List<UUID> tasksToCancel = new ArrayList<>();

    private final Map<Object, Long> throttledRequesters = new HashMap<>();
    private final Set<ICraftingMonitorListener> listeners = new HashSet<>();

    private NBTTagList tasksToRead;
    /**
     * Whether or not a crafting monitor update should be sent
     */
    private boolean tasksDirty;

    public CraftingManager(TileController network) {
        this.network = network;
    }

    @Override
    public Collection<ICraftingTask> getTasks() {
        return tasks.values();
    }

    @Override
    @Nullable
    public ICraftingTask getTask(UUID id) {
        return tasks.get(id);
    }

    @Override
    public Map<String, List<IItemHandlerModifiable>> getNamedContainers() {
        return containerInventories;
    }

    @Override
    public void add(@Nonnull ICraftingTask task) {
        tasksToAdd.add(task);

        network.markDirty();
    }

    @Override
    public void cancel(@Nullable UUID id) {
        if (id == null) {
            tasksToCancel.addAll(tasks.keySet());
        } else {
            tasksToCancel.add(id);
        }

        network.markDirty();
    }

    @Override
    @Nullable
    public ICraftingTask create(ItemStack stack, int quantity) {
        ICraftingPattern pattern = getPattern(stack);
        if (pattern == null) {
            return null;
        }

        ICraftingTaskFactory factory = API.instance().getCraftingTaskRegistry().get(pattern.getId());
        if (factory == null) {
            return null;
        }

        return factory.create(network, API.instance().createCraftingRequestInfo(stack), quantity, pattern);
    }

    @Nullable
    @Override
    public ICraftingTask create(FluidStack stack, int quantity) {
        ICraftingPattern pattern = getPattern(stack);
        if (pattern == null) {
            return null;
        }

        ICraftingTaskFactory factory = API.instance().getCraftingTaskRegistry().get(pattern.getId());
        if (factory == null) {
            return null;
        }

        return factory.create(network, API.instance().createCraftingRequestInfo(stack), quantity, pattern);
    }

    @Override
    public void update() {
        if(this.tasksDirty) {
            //makes sure updates are sent only once per tick
            listeners.forEach(ICraftingMonitorListener::onChanged);
            this.tasksDirty = false;
        }

        if (network.canRun()) {
            boolean changed = !tasksToCancel.isEmpty() || !tasksToAdd.isEmpty();

            for (ICraftingTask task : this.tasksToAdd) {
                this.tasks.put(task.getId(), task);
            }

            tasksToAdd.clear();

            for (UUID idToCancel : tasksToCancel) {
                ICraftingTask task = this.tasks.get(idToCancel);
                if (task != null) {
                    task.onCancelled();
                    this.tasks.remove(idToCancel);
                }
            }
            this.tasksToCancel.clear();

            boolean anyFinished = false;

            Iterator<Map.Entry<UUID, ICraftingTask>> it = tasks.entrySet().iterator();
            while (it.hasNext()) {
                ICraftingTask task = it.next().getValue();

                if (task.canUpdate() && task.update()) {
                    anyFinished = true;

                    it.remove();
                    //insert everything that remains, like infinite inputs
                    task.onCancelled();
                }
            }

            if (changed || anyFinished) {
                onTaskChanged();
            }

            if (!tasks.isEmpty()) {
                network.markDirty();
            }

            /*if (tasksToRead != null) {
                for (int i = 0; i < tasksToRead.tagCount(); ++i) {
                    NBTTagCompound taskTag = tasksToRead.getCompoundTagAt(i);

                    String taskType = taskTag.getString(NBT_TASK_TYPE);
                    NBTTagCompound taskData = taskTag.getCompoundTag(NBT_TASK_DATA);

                    ICraftingTaskFactory factory = API.instance().getCraftingTaskRegistry().get(taskType);
                    if (factory != null) {
                        try {
                            MasterCraftingTask task = factory.createFromNbt(network, taskData);

                            tasks.put(task.getId(), task);
                        } catch (CraftingTaskReadException e) {
                            e.printStackTrace();
                        }
                    }
                }

                this.tasksToRead = null;
            }*/
        }
    }

    @Override
    public void readFromNbt(NBTTagCompound tag) {
        this.tasksToRead = tag.getTagList(NBT_TASKS, Constants.NBT.TAG_COMPOUND);
    }

    @Override
    public NBTTagCompound writeToNbt(NBTTagCompound tag) {
        //TODO: nbt writing of tasks
        /*NBTTagList list = new NBTTagList();

        for (MasterCraftingTask task : tasks.values()) {
            NBTTagCompound taskTag = new NBTTagCompound();

            taskTag.setString(NBT_TASK_TYPE, task.getPattern().getId());
            taskTag.setTag(NBT_TASK_DATA, task.writeToNbt(new NBTTagCompound()));

            list.appendTag(taskTag);
        }

        tag.setTag(NBT_TASKS, list);*/

        return tag;
    }

    @Override
    public void addListener(ICraftingMonitorListener listener) {
        listeners.add(listener);

        listener.onAttached();
    }

    @Override
    public void removeListener(ICraftingMonitorListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onTaskChanged() {
        this.tasksDirty = true;
    }

    @Override
    @Nullable
    public ICraftingTask request(Object source, ItemStack stack, int amount) {
        if (isThrottled(source)) {
            return null;
        }

        for (ICraftingTask task : getTasks()) {
            if (task.getRequested().getItem() != null) {
                if (API.instance().getComparer().isEqualNoQuantity(task.getRequested().getItem(), stack)) {
                    amount -= task.getQuantity();
                }
            }
        }

        if (amount > 0) {
            ICraftingTask task = create(stack, amount);

            if (task != null) {
                ICraftingTaskError error = task.calculate();

                if (error == null && !task.hasMissing()) {
                    this.add(task);

                    return task;
                } else {
                    throttle(source);
                }
            } else {
                throttle(source);
            }
        }

        return null;
    }

    @Nullable
    @Override
    public ICraftingTask request(Object source, FluidStack stack, int amount) {
        if (isThrottled(source)) {
            return null;
        }

        for (ICraftingTask task : getTasks()) {
            if (task.getRequested().getFluid() != null) {
                if (API.instance().getComparer().isEqual(task.getRequested().getFluid(), stack, IComparer.COMPARE_NBT)) {
                    amount -= task.getQuantity();
                }
            }
        }

        if (amount > 0) {
            ICraftingTask task = create(stack, amount);

            if (task != null) {
                ICraftingTaskError error = task.calculate();

                if (error == null && !task.hasMissing()) {
                    this.add(task);

                    return task;
                } else {
                    throttle(source);
                }
            } else {
                throttle(source);
            }
        }

        return null;
    }

    private void throttle(@Nullable Object source) {
        OneSixMigrationHelper.removalHook(); // Remove @Nullable source

        if (source != null) {
            throttledRequesters.put(source, MinecraftServer.getCurrentTimeMillis());
        }
    }

    private boolean isThrottled(@Nullable Object source) {
        OneSixMigrationHelper.removalHook(); // Remove @Nullable source

        if (source == null) {
            return false;
        }

        Long throttledSince = throttledRequesters.get(source);
        if (throttledSince == null) {
            return false;
        }

        return MinecraftServer.getCurrentTimeMillis() - throttledSince < THROTTLE_DELAY_MS;
    }

    @Override
    public int track(ItemStack stack, int size) {
        int remainder = size;
        for (ICraftingTask task : tasks.values()) {
            remainder = task.onTrackedInsert(stack, remainder);

            if (remainder < 1) {
                return 0;
            }
        }

        //TODO: don't call this on every insert
        this.onTaskChanged();
        return remainder;
    }

    @Override
    public int track(FluidStack stack, int size) {
        int remainder = size;
        for (ICraftingTask task : tasks.values()) {
            remainder = task.onTrackedInsert(stack, remainder);

            if (remainder < 1) {
                return 0;
            }
        }

        this.onTaskChanged();
        return remainder;
    }

    @Override
    public List<ICraftingPattern> getPatterns() {
        return patterns;
    }

    @Override
    public void rebuild() {
        this.network.getItemStorageCache().getCraftablesList().clear();
        this.network.getFluidStorageCache().getCraftablesList().clear();

        this.patterns.clear();
        this.containerInventories.clear();
        this.patternToContainer.clear();

        List<ICraftingPatternContainer> containers = new ArrayList<>();

        for (INetworkNode node : network.getNodeGraph().all()) {
            if (node instanceof ICraftingPatternContainer && node.canUpdate()) {
                containers.add((ICraftingPatternContainer) node);
            }
        }

        containers.sort((a, b) -> b.getPosition().compareTo(a.getPosition()));

        for (ICraftingPatternContainer container : containers) {
            for (ICraftingPattern pattern : container.getPatterns()) {
                this.patterns.add(pattern);

                for (ItemStack output : pattern.getOutputs()) {
                    network.getItemStorageCache().getCraftablesList().add(output);
                }

                for (FluidStack output : pattern.getFluidOutputs()) {
                    network.getFluidStorageCache().getCraftablesList().add(output);
                }

                Set<ICraftingPatternContainer> list = this.patternToContainer.get(pattern);
                if (list == null) {
                    list = new LinkedHashSet<>();
                }
                list.add(container);
                this.patternToContainer.put(pattern, list);
            }

            IItemHandlerModifiable handler = container.getPatternInventory();
            if (handler != null) {
                this.containerInventories.computeIfAbsent(container.getName(), k -> new ArrayList<>()).add(handler);
            }
        }

        this.network.getItemStorageCache().reAttachListeners();
        this.network.getFluidStorageCache().reAttachListeners();
    }

    @Override
    public Set<ICraftingPatternContainer> getAllContainer(ICraftingPattern pattern) {
        return patternToContainer.getOrDefault(pattern, Collections.emptySet());
    }

    @Nullable
    @Override
    public ICraftingPattern getPattern(ItemStack pattern, int flags) {
        for (ICraftingPattern patternInList : patterns) {
            for (ItemStack output : patternInList.getOutputs()) {
                if (API.instance().getComparer().isEqual(output, pattern, flags) &&
                        patternInList.getBlacklistedItems().stream()
                                .noneMatch(f -> API.instance().getComparer().isEqualNoQuantity(f, pattern))) {
                    return patternInList;
                }
            }
        }

        return null;
    }

    @Nullable
    @Override
    public ICraftingPattern getPattern(FluidStack pattern) {
        for (ICraftingPattern patternInList : patterns) {
            for (FluidStack output : patternInList.getFluidOutputs()) {
                if (API.instance().getComparer().isEqual(output, pattern, IComparer.COMPARE_NBT) &&
                        patternInList.getBlacklistedFluids().stream().noneMatch(f -> API.instance().getComparer()
                                .isEqual(f, pattern, IComparer.COMPARE_NBT))) {
                    return patternInList;
                }
            }
        }

        return null;
    }
}
