package org.example.yamirkuro.townquest;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.example.yamirkuro.townquest.network.ModNetwork;
import org.example.yamirkuro.townquest.registry.ModBlockEntities;
import org.example.yamirkuro.townquest.registry.ModBlocks;
import org.example.yamirkuro.townquest.registry.ModCreativeTabs;
import org.example.yamirkuro.townquest.registry.ModItems;
import org.example.yamirkuro.townquest.registry.ModMenus;
import org.slf4j.Logger;

@Mod(Townquest.MODID)
public class Townquest {

    public static final String MODID = "townquest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Townquest() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        modBus.addListener(this::commonSetup);

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
