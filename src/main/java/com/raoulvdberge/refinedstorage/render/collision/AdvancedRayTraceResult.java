package com.raoulvdberge.refinedstorage.render.collision;

import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

public class AdvancedRayTraceResult<T extends RayTraceResult> {
    private final CollisionGroup group;
    private final T hit;

    public AdvancedRayTraceResult(CollisionGroup group, T hit) {
        this.group = group;
        this.hit = hit;
    }

    public double squareDistanceTo(Vec3d vec) {
        return hit.hitVec.squareDistanceTo(vec);
    }

    public CollisionGroup getGroup() {
        return group;
    }

    public T getHit() {
        return hit;
    }
}