package org.example.yamirkuro.townquest.registry;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.yamirkuro.townquest.Townquest;
import org.example.yamirkuro.townquest.menu.QuestTableMenu;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Townquest.MODID);

    public static final RegistryObject<MenuType<QuestTableMenu>> QUEST_TABLE = MENUS.register("quest_table",
            () -> IForgeMenuType.create((windowId, inv, buf) -> new QuestTableMenu(windowId, inv, buf.readBlockPos())));
}
