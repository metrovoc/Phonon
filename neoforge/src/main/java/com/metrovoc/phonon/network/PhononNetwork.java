package com.metrovoc.phonon.network;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.network.packets.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network registration for NeoForge.
 * All packet types registered in one place.
 */
public class PhononNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Constants.MOD_ID)
            .versioned("1.0.0")
            .optional();

        // Server -> Client packets
        registrar.playToClient(
            SyncAudioResourcesPacket.TYPE,
            SyncAudioResourcesPacket.CODEC,
            SyncAudioResourcesPacket::handle
        );

        registrar.playToClient(
            SyncSpeakerStatePacket.TYPE,
            SyncSpeakerStatePacket.CODEC,
            SyncSpeakerStatePacket::handle
        );

        registrar.playToClient(
            AudioChunkPacket.TYPE,
            AudioChunkPacket.CODEC,
            AudioChunkPacket::handle
        );

        // Client -> Server packets
        registrar.playToServer(
            RequestAudioPacket.TYPE,
            RequestAudioPacket.CODEC,
            RequestAudioPacket::handle
        );

        registrar.playToServer(
            SpeakerControlPacket.TYPE,
            SpeakerControlPacket.CODEC,
            SpeakerControlPacket::handle
        );

        registrar.playToServer(
            SpeakerSeekPacket.TYPE,
            SpeakerSeekPacket.CODEC,
            SpeakerSeekPacket::handle
        );
    }
}
