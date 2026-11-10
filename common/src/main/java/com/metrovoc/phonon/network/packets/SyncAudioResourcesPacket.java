package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.AudioLimits;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.client.ClientAudioManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.metrovoc.phonon.network.PacketContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Sync all audio resources from server to client (sent on join).
 */
public record SyncAudioResourcesPacket(List<AudioResource> resources) implements CustomPacketPayload {

    public static final Type<SyncAudioResourcesPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_audio_resources"));

    private static final StreamCodec<ByteBuf, String> NAME = ByteBufCodecs.stringUtf8(AudioLimits.MAX_RESOURCE_NAME_CHARS);
    private static final StreamCodec<ByteBuf, String> URL = ByteBufCodecs.stringUtf8(AudioLimits.MAX_RESOURCE_URL_CHARS);

    public static final StreamCodec<ByteBuf, SyncAudioResourcesPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(
            ArrayList::new,
            StreamCodec.of(
                (buf, resource) -> {
                    UUIDCodec.STREAM_CODEC.encode(buf, resource.id());
                    NAME.encode(buf, resource.name());
                    URL.encode(buf, resource.url());
                    ByteBufCodecs.VAR_LONG.encode(buf, resource.durationMs());
                    ByteBufCodecs.VAR_LONG.encode(buf, resource.sizeBytes());
                    UUIDCodec.STREAM_CODEC.encode(buf, resource.addedBy());
                    NAME.encode(buf, resource.addedByName());
                },
                buf -> new AudioResource(
                    UUIDCodec.STREAM_CODEC.decode(buf),
                    NAME.decode(buf),
                    URL.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    UUIDCodec.STREAM_CODEC.decode(buf),
                    NAME.decode(buf)
                )
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

    public static void handle(SyncAudioResourcesPacket packet, PacketContext ctx) {
        ctx.enqueueWork(() -> {
            ClientAudioManager.getInstance().setResources(packet.resources);
        });
    }
}
