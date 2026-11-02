package org.example.yamirkuro.townquest.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.yamirkuro.townquest.Townquest;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Townquest.MODID);

    public static final RegistryObject<BlockEntityType<QuestTableBlockEntity>> QUEST_TABLE =
            BLOCK_ENTITIES.register("quest_table",
                    () -> BlockEntityType.Builder
                            .of(QuestTableBlockEntity::new, ModBlocks.QUEST_TABLE.get())
                            .build(null));
}
