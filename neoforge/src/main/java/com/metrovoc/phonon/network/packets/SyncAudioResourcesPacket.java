package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.AudioLimits;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.client.ClientAudioManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Sync all audio resources from server to client (sent on join).
 */
public record SyncAudioResourcesPacket(List<AudioResource> resources) implements CustomPacketPayload {

    public static final Type<SyncAudioResourcesPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_audio_resources"));

    public static final StreamCodec<ByteBuf, SyncAudioResourcesPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(
            ArrayList::new,
            StreamCodec.composite(
                UUIDCodec.STREAM_CODEC,
                AudioResource::id,
                ByteBufCodecs.stringUtf8(AudioLimits.MAX_RESOURCE_NAME_CHARS),
                AudioResource::name,
                ByteBufCodecs.stringUtf8(AudioLimits.MAX_RESOURCE_URL_CHARS),
                AudioResource::url,
                ByteBufCodecs.VAR_LONG,
                AudioResource::durationMs,
                ByteBufCodecs.VAR_LONG,
                AudioResource::sizeBytes,
                AudioResource::new
            ),
            AudioLimits.MAX_RESOURCE_COUNT
        ),
        SyncAudioResourcesPacket::resources,
        SyncAudioResourcesPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncAudioResourcesPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientAudioManager.getInstance().setResources(packet.resources);
        });
    }
}
