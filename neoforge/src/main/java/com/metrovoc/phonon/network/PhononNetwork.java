package com.metrovoc.phonon.network;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.network.packets.AudioChunkPacket;
import com.metrovoc.phonon.network.packets.AudioStreamStartPacket;
import com.metrovoc.phonon.network.packets.LiveBroadcastChunkPacket;
import com.metrovoc.phonon.network.packets.LiveBroadcastEndPacket;
import com.metrovoc.phonon.network.packets.LiveBroadcastStartPacket;
import com.metrovoc.phonon.network.packets.LiveStreamEndPacket;
import com.metrovoc.phonon.network.packets.RequestAudioPacket;
import com.metrovoc.phonon.network.packets.SpeakerControlPacket;
import com.metrovoc.phonon.network.packets.SpeakerSeekPacket;
import com.metrovoc.phonon.network.packets.SpeakerVolumePacket;
import com.metrovoc.phonon.network.packets.SyncAudioResourcesPacket;
import com.metrovoc.phonon.network.packets.SyncSpeakerStatePacket;
import com.metrovoc.phonon.network.packets.SyncSpeakerVolumePacket;
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

        registrar.playToClient(
            SyncSpeakerVolumePacket.TYPE,
            SyncSpeakerVolumePacket.CODEC,
            SyncSpeakerVolumePacket::handle
        );

        registrar.playToClient(
            AudioStreamStartPacket.TYPE,
            AudioStreamStartPacket.CODEC,
            AudioStreamStartPacket::handle
        );

        registrar.playToClient(
            LiveStreamEndPacket.TYPE,
            LiveStreamEndPacket.CODEC,
            LiveStreamEndPacket::handle
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

        registrar.playToServer(
            SpeakerVolumePacket.TYPE,
            SpeakerVolumePacket.CODEC,
            SpeakerVolumePacket::handle
        );

        // Live broadcast packets
        registrar.playToServer(
            LiveBroadcastStartPacket.TYPE,
            LiveBroadcastStartPacket.CODEC,
            LiveBroadcastStartPacket::handle
        );

        // Bidirectional packets (Client -> Server -> Clients)
        registrar.playBidirectional(
            LiveBroadcastChunkPacket.TYPE,
            LiveBroadcastChunkPacket.CODEC,
            LiveBroadcastChunkPacket::handle
        );

        registrar.playBidirectional(
            LiveBroadcastEndPacket.TYPE,
            LiveBroadcastEndPacket.CODEC,
            LiveBroadcastEndPacket::handle
        );
    }
}
