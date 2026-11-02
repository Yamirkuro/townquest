package org.example.yamirkuro.townquest.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Місце роботи: точка (min == max) або 3D-область між двома кутами. */
public record QuestLocation(ResourceKey<Level> dimension, BlockPos min, BlockPos max) {

    public static final int MAX_SIDE = 512;

    /** Нормалізує кути так, щоб min був найменшим, а max найбільшим по кожній осі. */
    public static QuestLocation of(ResourceKey<Level> dimension, BlockPos p1, BlockPos p2) {
        BlockPos min = new BlockPos(
                Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
        return new QuestLocation(dimension, min, max);
    }

    public boolean isArea() {
        return !min.equals(max);
    }

    public int sizeX() { return max.getX() - min.getX() + 1; }
    public int sizeY() { return max.getY() - min.getY() + 1; }
    public int sizeZ() { return max.getZ() - min.getZ() + 1; }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean fitsLimit() {
        return sizeX() <= MAX_SIDE && sizeY() <= MAX_SIDE && sizeZ() <= MAX_SIDE;
    }

    public Vec3 center() {
        return new Vec3(
                (min.getX() + max.getX()) / 2.0 + 0.5,
                (min.getY() + max.getY()) / 2.0 + 0.5,
                (min.getZ() + max.getZ()) / 2.0 + 0.5);
    }

    public double distanceTo(Vec3 pos) {
        return center().distanceTo(pos);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", dimension.location().toString());
        tag.putLong("Min", min.asLong());
        tag.putLong("Max", max.asLong());
        return tag;
    }

    public static QuestLocation load(CompoundTag tag) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dim"));
        ResourceKey<Level> key = dim == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dim);
        return new QuestLocation(key, BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")));
    }
}
