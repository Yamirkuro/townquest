package org.example.yamirkuro.townquest.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.example.yamirkuro.townquest.Townquest;
import org.example.yamirkuro.townquest.client.screen.QuestTableScreen;
import org.example.yamirkuro.townquest.registry.ModMenus;

@Mod.EventBusSubscriber(modid = Townquest.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.QUEST_TABLE.get(), QuestTableScreen::new));
    }
}
