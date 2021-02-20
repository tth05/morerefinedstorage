package com.raoulvdberge.refinedstorage.apiimpl.autocrafting;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingManager;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorListener;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.autocrafting.registry.ICraftingTaskFactory;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.MasterCraftingTask;
import com.raoulvdberge.refinedstorage.tile.TileController;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CraftingManager implements ICraftingManager {
    private static final int THROTTLE_DELAY_MS = 3000;

    private static final Logger LOGGER = LogManager.getLogger(CraftingManager.class);

    private static final String NBT_TASKS = "Tasks";
    private static final String NBT_TASK_TYPE = "Type";
    private static final String NBT_TASK_DATA = "Task";

    private final TileController network;

    private final Map<String, List<IItemHandlerModifiable>> containerInventories = new LinkedHashMap<>();
    private final Map<ICraftingPattern, Set<ICraftingPatternContainer>> patternToContainer = new HashMap<>();

    private final Set<ICraftingPattern> patterns = new HashSet<>();

    private final Map<UUID, ICraftingTask> tasks = new LinkedHashMap<>();
    private final List<UUID> tasksToCancel = new ArrayList<>();
    private final List<ICraftingTask> tasksInCalculation = new ArrayList<>();

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
    public void update() {
        //sends updates at a max rate of 1 per tick and force sends every 20 ticks
        if (this.tasksDirty || this.network.getWorld().getWorldTime() % 20 == 0) {
            listeners.forEach(ICraftingMonitorListener::onChanged);
            this.tasksDirty = false;
        }

        if (tasksToRead != null) {
            for (int i = 0; i < tasksToRead.tagCount(); ++i) {
                NBTTagCompound taskTag = tasksToRead.getCompoundTagAt(i);

                String taskType = taskTag.getString(NBT_TASK_TYPE);
                NBTTagCompound taskData = taskTag.getCompoundTag(NBT_TASK_DATA);

                ICraftingTaskFactory factory = API.instance().getCraftingTaskRegistry().get(taskType);
                if (factory != null) {
                    try {
                        LOGGER.debug("Reading task...");

                        MasterCraftingTask task = factory.createFromNbt(network, taskData);

                        tasks.put(task.getId(), task);

                        LOGGER.debug("Loaded task with id {}", task.getId());
                    } catch (CraftingTaskReadException e) {
                        LOGGER.catching(e);
                    }
                }
            }

            this.tasksToRead = null;
        }

        boolean changed = !tasksToCancel.isEmpty();

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

            if (!task.isHalted() && task.canUpdate() && task.update()) {
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
            network.markNetworkNodeDirty();
        }
    }

    @Override
    public void onTaskChanged() {
        this.tasksDirty = true;
    }

    @Override
    public NBTTagCompound writeToNbt(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();

        for (ICraftingTask task : tasks.values()) {
            NBTTagCompound taskTag = new NBTTagCompound();

            taskTag.setString(NBT_TASK_TYPE, task.getPattern().getId());
            taskTag.setTag(NBT_TASK_DATA, task.writeToNbt(new NBTTagCompound()));

            list.appendTag(taskTag);
        }

        tag.setTag(NBT_TASKS, list);

        return tag;
    }

    @Override
    public void readFromNbt(NBTTagCompound tag) {
        this.tasksToRead = tag.getTagList(NBT_TASKS, Constants.NBT.TAG_COMPOUND);
    }

    @Override
    public void add(@Nonnull ICraftingTask task) {
        tasks.put(task.getId(), task);

        network.markNetworkNodeDirty();
    }

    @Override
    public void cancel(@Nullable UUID id) {
        if (id == null) {
            tasksToCancel.addAll(tasks.keySet());
        } else {
            tasksToCancel.add(id);
        }

        network.markNetworkNodeDirty();
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
    @Nullable
    public ICraftingTask request(Object source, ItemStack stack, long amount) {
        if (isThrottled(source)) {
            return null;
        }

        for (ICraftingTask task : getTasks()) {
            if (task.getRequested().getItem() == null)
                continue;

            if (API.instance().getComparer().isEqualNoQuantity(task.getRequested().getItem(), stack))
                amount -= task.getQuantity();
        }

        if (amount <= 0)
            return null;

        //check tasks that are still calculating
        for (ICraftingTask task : this.tasksInCalculation) {
            if (task.getRequested().getItem() == null)
                continue;

            if (API.instance().getComparer().isEqualNoQuantity(task.getRequested().getItem(), stack))
                amount -= task.getQuantity();
        }

        if (amount <= 0)
            return null;

        ICraftingTask task = create(stack, amount);

        if (task != null) {
            addAndCalculateTask(source, task);
            return task;
        } else {
            throttle(source);
        }

        return null;
    }

    @Nullable
    @Override
    public ICraftingTask request(Object source, FluidStack stack, long amount) {
        if (isThrottled(source)) {
            return null;
        }

        for (ICraftingTask task : getTasks()) {
            if (task.getRequested().getFluid() == null)
                continue;

            if (API.instance().getComparer().isEqual(task.getRequested().getFluid(), stack, IComparer.COMPARE_NBT)) {
                amount -= task.getQuantity();
            }
        }

        if (amount <= 0)
            return null;

        //check tasks that are still calculating
        for (ICraftingTask task : this.tasksInCalculation) {
            if (task.getRequested().getFluid() == null)
                continue;

            if (API.instance().getComparer().isEqual(task.getRequested().getFluid(), stack, IComparer.COMPARE_NBT)) {
                amount -= task.getQuantity();
            }
        }

        if (amount <= 0)
            return null;

        ICraftingTask task = create(stack, amount);

        if (task != null) {
            addAndCalculateTask(source, task);
            return task;
        } else {
            throttle(source);
        }

        return null;
    }

    private void addAndCalculateTask(Object source, ICraftingTask task) {
      /*  this.tasksInCalculation.add(task);
        CompletableFuture.supplyAsync(task::calculate).thenAccept((err) -> {
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                if (err == null && !task.hasMissing()) {
                    this.add(task);
                    task.setCanUpdate(true);
                } else {
                    throttle(source);
                }

                this.tasksInCalculation.remove(task);
            });
        });*/

        FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
            this.tasksInCalculation.add(task);
            ICraftingTaskError err = task.calculate();
            if (err == null && !task.hasMissing()) {
                this.add(task);
                task.setCanUpdate(true);
            } else {
                throttle(source);
            }
            this.tasksInCalculation.remove(task);
        });
    }

    @Override
    @Nullable
    public ICraftingTask create(ItemStack stack, long quantity) {
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
    public ICraftingTask create(FluidStack stack, long quantity) {
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
    public synchronized void track(ItemStack stack) {
        int trackedAmount = 0;
        int oldStackSize = stack.getCount();

        for (ICraftingTask task : tasks.values()) {
            if (!task.canUpdate())
                continue;

            trackedAmount = task.onTrackedInsert(stack, trackedAmount);

            if (stack.isEmpty())
                break;
        }

        this.tasksDirty |= trackedAmount > 0 || oldStackSize != stack.getCount();
    }

    @Override
    public synchronized void track(FluidStack stack) {
        int trackedAmount = 0;
        int oldStackSize = stack.amount;

        for (ICraftingTask task : tasks.values()) {
            if (!task.canUpdate())
                continue;

            trackedAmount = task.onTrackedInsert(stack, trackedAmount);

            if (stack.amount < 1)
                break;
        }

        this.tasksDirty |= trackedAmount > 0 || oldStackSize != stack.amount;
    }

    @Override
    public void rebuild() {
        this.network.getItemStorageCache().getCraftablesList().clearCounts();
        this.network.getFluidStorageCache().getCraftablesList().clearCounts();

        //synchronized for access by crafting tasks during calculation
        synchronized (patterns) {
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

                    outer:
                    for (ItemStack output : pattern.getOutputs()) {
                        for (ItemStack blacklistedItem : pattern.getBlacklistedItems()) {
                            if (API.instance().getComparer().isEqualNoQuantity(blacklistedItem, output))
                                continue outer;
                        }

                        network.getItemStorageCache().getCraftablesList().add(output);
                    }

                    outer:
                    for (FluidStack output : pattern.getFluidOutputs()) {
                        for (FluidStack blacklistedFluid : pattern.getBlacklistedFluids()) {
                            if (API.instance().getComparer().isEqual(blacklistedFluid, output, IComparer.COMPARE_NBT))
                                continue outer;
                        }

                        network.getFluidStorageCache().getCraftablesList().add(output);
                    }

                    this.patternToContainer.computeIfAbsent(pattern, key -> new LinkedHashSet<>()).add(container);
                }

                IItemHandlerModifiable handler = container.getPatternInventory();
                if (handler != null) {
                    this.containerInventories.computeIfAbsent(container.getName(), k -> new ArrayList<>()).add(handler);
                }
            }
        }

        this.network.getItemStorageCache().getCraftablesList().clearEmpty();
        this.network.getFluidStorageCache().getCraftablesList().clearEmpty();

        this.network.getItemStorageCache().reAttachListeners();
        this.network.getFluidStorageCache().reAttachListeners();

        //cancel or resume crafting tasks
        for (ICraftingTask task : this.tasks.values()) {
            task.updateHaltedState();
        }
    }

    private void throttle(@Nullable Object source) {
        if (source != null) {
            throttledRequesters.put(source, MinecraftServer.getCurrentTimeMillis());
        }
    }

    private boolean isThrottled(@Nullable Object source) {
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
    @Nullable
    public ICraftingTask getTask(UUID id) {
        return tasks.get(id);
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

    @Override
    public Set<ICraftingPatternContainer> getAllContainer(ICraftingPattern pattern) {
        return patternToContainer.getOrDefault(pattern, Collections.emptySet());
    }

    @Override
    public Collection<ICraftingTask> getTasks() {
        return tasks.values();
    }

    @Override
    public Set<ICraftingPattern> getPatterns() {
        //synchronized for access by crafting tasks during calculation
        synchronized (patterns) {
            return patterns;
        }
    }

    @Override
    public Map<String, List<IItemHandlerModifiable>> getNamedContainers() {
        return containerInventories;
    }

}