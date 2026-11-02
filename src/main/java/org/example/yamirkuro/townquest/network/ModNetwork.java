package org.example.yamirkuro.townquest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.example.yamirkuro.townquest.Townquest;
import org.example.yamirkuro.townquest.network.packet.BookActionPacket;
import org.example.yamirkuro.townquest.network.packet.BookDataPacket;
import org.example.yamirkuro.townquest.network.packet.CreateQuestPacket;
import org.example.yamirkuro.townquest.network.packet.DeleteQuestPacket;
import org.example.yamirkuro.townquest.network.packet.RequestBookPacket;
import org.example.yamirkuro.townquest.network.packet.ReviewQuestPacket;
import org.example.yamirkuro.townquest.network.packet.ExtractArchivePacket;
import org.example.yamirkuro.townquest.network.packet.ForceCompletePacket;
import org.example.yamirkuro.townquest.network.packet.StartBindingPacket;

import java.util.Optional;

public class ModNetwork {

    private static final String PROTOCOL = "5";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Townquest.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, CreateQuestPacket.class,
                CreateQuestPacket::encode, CreateQuestPacket::decode, CreateQuestPacket::handle);
        CHANNEL.registerMessage(id++, DeleteQuestPacket.class,
                DeleteQuestPacket::encode, DeleteQuestPacket::decode, DeleteQuestPacket::handle);
        CHANNEL.registerMessage(id++, StartBindingPacket.class,
                StartBindingPacket::encode, StartBindingPacket::decode, StartBindingPacket::handle);
        CHANNEL.registerMessage(id++, RequestBookPacket.class,
                RequestBookPacket::encode, RequestBookPacket::decode, RequestBookPacket::handle);
        CHANNEL.registerMessage(id++, BookActionPacket.class,
                BookActionPacket::encode, BookActionPacket::decode, BookActionPacket::handle);
        CHANNEL.registerMessage(id++, ReviewQuestPacket.class,
                ReviewQuestPacket::encode, ReviewQuestPacket::decode, ReviewQuestPacket::handle);
        CHANNEL.registerMessage(id++, BookDataPacket.class,
                BookDataPacket::encode, BookDataPacket::decode, BookDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ExtractArchivePacket.class,
                ExtractArchivePacket::encode, ExtractArchivePacket::decode, ExtractArchivePacket::handle);
        CHANNEL.registerMessage(id++, ForceCompletePacket.class,
                ForceCompletePacket::encode, ForceCompletePacket::decode, ForceCompletePacket::handle);
    }
}
