package com.metrovoc.phonon.fabric;

import com.metrovoc.phonon.network.PhononPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The client half of Phonon's Fabric networking; kept apart so a dedicated server never loads client classes. */
public final class PhononFabricClientNetwork {
    private PhononFabricClientNetwork() {}

    public static void register() {
        PhononPayloads.TO_CLIENT.forEach(PhononFabricClientNetwork::handler);
        ClientConfigurationNetworking.registerGlobalReceiver(VersionPacket.TYPE, (packet, context) -> {
            if (!packet.version().equals(PhononPayloads.VERSION)) {
                context.responseSender().disconnect(Component.translatable("phonon.disconnect.version"));
            }
        });
    }

    private static <T extends CustomPacketPayload> void handler(PhononPayloads.Entry<T> entry) {
        ClientPlayNetworking.registerGlobalReceiver(entry.type(), (payload, context) ->
            entry.handler().accept(payload, PhononFabricNetwork.context(context.player())));
    }
}
