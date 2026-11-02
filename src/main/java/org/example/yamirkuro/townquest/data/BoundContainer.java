package org.example.yamirkuro.townquest.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Прив'язаний контейнер: вимір + координати. */
public record BoundContainer(ResourceKey<Level> dimension, BlockPos pos) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", dimension.location().toString());
        tag.putLong("Pos", pos.asLong());
        return tag;
    }

    public static BoundContainer load(CompoundTag tag) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dim"));
        ResourceKey<Level> key = dim == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dim);
        return new BoundContainer(key, BlockPos.of(tag.getLong("Pos")));
    }
}
