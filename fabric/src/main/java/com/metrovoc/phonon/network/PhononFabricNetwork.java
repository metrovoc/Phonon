package com.metrovoc.phonon.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** Registers {@link PhononPayloads} with Fabric. */
public final class PhononFabricNetwork {
    /** Clientbound packets carry audio chunks and thumbnails; Fabric splits anything over vanilla's limit. */
    private static final int MAX_CLIENTBOUND_BYTES = 8 * 1024 * 1024;

    private PhononFabricNetwork() {}

    public static void registerTypes() {
        PhononPayloads.TO_CLIENT.forEach(PhononFabricNetwork::clientboundType);
        PhononPayloads.TO_SERVER.forEach(PhononFabricNetwork::serverboundType);
        PhononPayloads.TO_SERVER.forEach(PhononFabricNetwork::serverHandler);
    }

    public static void registerClientHandlers() {
        PhononPayloads.TO_CLIENT.forEach(PhononFabricNetwork::clientHandler);
    }

    private static <T extends CustomPacketPayload> void clientboundType(PhononPayloads.Entry<T> entry) {
        PayloadTypeRegistry.clientboundPlay().registerLarge(entry.type(), entry.codec(), MAX_CLIENTBOUND_BYTES);
    }

    private static <T extends CustomPacketPayload> void serverboundType(PhononPayloads.Entry<T> entry) {
        PayloadTypeRegistry.serverboundPlay().register(entry.type(), entry.codec());
    }

    private static <T extends CustomPacketPayload> void serverHandler(PhononPayloads.Entry<T> entry) {
        ServerPlayNetworking.registerGlobalReceiver(entry.type(), (payload, context) ->
            entry.handler().accept(payload, context(context.player())));
    }

    private static <T extends CustomPacketPayload> void clientHandler(PhononPayloads.Entry<T> entry) {
        ClientPlayNetworking.registerGlobalReceiver(entry.type(), (payload, context) ->
            entry.handler().accept(payload, context(context.player())));
    }

    /** Fabric calls play handlers on the game thread, so their work can run right away. */
    private static PacketContext context(Player player) {
        return new PacketContext() {
            @Override
            public Player player() {
                return player;
            }

            @Override
            public void enqueueWork(Runnable work) {
                work.run();
            }
        };
    }
}
