package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.platform.PlatformHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Seek to position in current track.
 */
public record SpeakerSeekPacket(
    BlockPos pos,
    long seekPositionMs
) implements CustomPacketPayload {

    public static final Type<SpeakerSeekPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "speaker_seek"));

    public static final StreamCodec<ByteBuf, SpeakerSeekPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SpeakerSeekPacket::pos,
        ByteBufCodecs.VAR_LONG,
        SpeakerSeekPacket::seekPositionMs,
        SpeakerSeekPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerSeekPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker)) return;

            PlaybackState current = speaker.getPlayback();
            if (!current.playing() || current.resourceId() == null) return;

            // Calculate new startTime to simulate seek
            long serverTime = System.currentTimeMillis();
            long newStartTime = serverTime - packet.seekPositionMs;

            var newPlayback = new PlaybackState(
                current.resourceId(),
                newStartTime,
                current.volume(),
                true
            );

            speaker.setPlayback(newPlayback);
            PlatformHelper.INSTANCE.sendToAllTracking(
                level,
                packet.pos,
                new SyncSpeakerStatePacket(packet.pos, newPlayback, serverTime)
            );
        });
    }
}
