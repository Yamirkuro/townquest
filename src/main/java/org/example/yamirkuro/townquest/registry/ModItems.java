package org.example.yamirkuro.townquest.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.yamirkuro.townquest.Townquest;
import org.example.yamirkuro.townquest.item.QuestBookItem;
import org.example.yamirkuro.townquest.item.QuestArchiveItem;
import org.example.yamirkuro.townquest.item.QuestGlobeItem;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Townquest.MODID);

    public static final RegistryObject<Item> QUEST_TABLE = ITEMS.register("quest_table",
            () -> new BlockItem(ModBlocks.QUEST_TABLE.get(), new Item.Properties()));

    public static final RegistryObject<Item> QUEST_BOOK = ITEMS.register("quest_book",
            () -> new QuestBookItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> QUEST_GLOBE = ITEMS.register("quest_globe",
            () -> new QuestGlobeItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> QUEST_ARCHIVE = ITEMS.register("quest_archive",
            () -> new QuestArchiveItem(new Item.Properties().stacksTo(1)));
}


