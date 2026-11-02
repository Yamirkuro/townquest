package org.example.yamirkuro.townquest.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.example.yamirkuro.townquest.Townquest;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Townquest.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.townquest"))
                    .icon(() -> ModItems.QUEST_BOOK.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ModItems.QUEST_TABLE.get());
                        output.accept(ModItems.QUEST_BOOK.get());
                        output.accept(ModItems.QUEST_GLOBE.get());
                        output.accept(ModItems.QUEST_ARCHIVE.get());
                    })
                    .build());
}
