package com.tovkaic.phonon.network;

import com.tovkaic.phonon.Constants;
import com.tovkaic.phonon.audio.AudioResource;
import com.tovkaic.phonon.audio.PlaybackState;
import com.tovkaic.phonon.network.packets.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
    }
}
