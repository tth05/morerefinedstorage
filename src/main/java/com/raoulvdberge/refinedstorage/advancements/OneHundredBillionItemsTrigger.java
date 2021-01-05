package com.raoulvdberge.refinedstorage.advancements;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.advancements.critereon.AbstractCriterionInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OneHundredBillionItemsTrigger implements ICriterionTrigger<OneHundredBillionItemsTrigger.Instance> {

    private static final ResourceLocation ID = new ResourceLocation("rs_one_hundred_billion_items");
    private final Map<PlayerAdvancements, OneHundredBillionItemsTrigger.Listeners> listeners = new HashMap<>();

    @Nonnull
    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void addListener(@Nonnull PlayerAdvancements playerAdvancements, @Nonnull ICriterionTrigger.Listener<Instance> listener) {
        OneHundredBillionItemsTrigger.Listeners existingListeners = this.listeners.get(playerAdvancements);

        if (existingListeners == null) {
            existingListeners = new OneHundredBillionItemsTrigger.Listeners(playerAdvancements);
            this.listeners.put(playerAdvancements, existingListeners);
        }

        existingListeners.add(listener);
    }

    @Override
    public void removeListener(@Nonnull PlayerAdvancements playerAdvancements, @Nonnull Listener<Instance> listener) {
        OneHundredBillionItemsTrigger.Listeners existingListeners = this.listeners.get(playerAdvancements);

        if (existingListeners != null) {
            existingListeners.remove(listener);

            if (existingListeners.isEmpty()) {
                this.listeners.remove(playerAdvancements);
            }
        }
    }

    @Override
    public void removeAllListeners(@Nonnull PlayerAdvancements playerAdvancements) {
        this.listeners.remove(playerAdvancements);
    }

    @Nonnull
    @Override
    public Instance deserializeInstance(@Nonnull JsonObject json, @Nonnull JsonDeserializationContext context) {
        return new Instance();
    }

    public void trigger(EntityPlayerMP player) {
        OneHundredBillionItemsTrigger.Listeners listeners = this.listeners.get(player.getAdvancements());

        if (listeners != null) {
            listeners.trigger();
        }
    }

    public static class Instance extends AbstractCriterionInstance {

        public Instance() {
            super(OneHundredBillionItemsTrigger.ID);
        }
    }

    public static class Listeners {
        private final PlayerAdvancements playerAdvancements;
        private final Set<Listener<OneHundredBillionItemsTrigger.Instance>> listeners = new HashSet<>();

        public Listeners(PlayerAdvancements playerAdvancementsIn) {
            this.playerAdvancements = playerAdvancementsIn;
        }

        public boolean isEmpty() {
            return this.listeners.isEmpty();
        }

        public void add(ICriterionTrigger.Listener<OneHundredBillionItemsTrigger.Instance> listener) {
            this.listeners.add(listener);
        }

        public void remove(ICriterionTrigger.Listener<OneHundredBillionItemsTrigger.Instance> listener) {
            this.listeners.remove(listener);
        }

        public void trigger() {
            for (ICriterionTrigger.Listener<OneHundredBillionItemsTrigger.Instance> listener : this.listeners) {
                listener.grantCriterion(playerAdvancements);
            }
        }
    }
}
