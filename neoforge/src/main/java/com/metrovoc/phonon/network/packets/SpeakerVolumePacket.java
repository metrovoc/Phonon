package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.platform.PlatformHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Update speaker volume.
 * Volume is a per-speaker property, independent of playback state.
 */
public record SpeakerVolumePacket(
    BlockPos pos,
    float volume
) implements CustomPacketPayload {

    public static final Type<SpeakerVolumePacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "speaker_volume"));

    public static final StreamCodec<ByteBuf, SpeakerVolumePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SpeakerVolumePacket::pos,
        ByteBufCodecs.FLOAT,
        SpeakerVolumePacket::volume,
        SpeakerVolumePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerVolumePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var level = player.level();
            SpeakerBlockEntity speaker = SpeakerPacketValidator.getAccessibleSpeaker(player, packet.pos);
            if (speaker == null) return;

            speaker.setVolume(packet.volume);
            float volume = speaker.getVolume();

            // Broadcast to all tracking players
            PlatformHelper.INSTANCE.sendToAllTracking(
                level,
                packet.pos,
                new SyncSpeakerVolumePacket(packet.pos, volume)
            );
        });
    }
}
