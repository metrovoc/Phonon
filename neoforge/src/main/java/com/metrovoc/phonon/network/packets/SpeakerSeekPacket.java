package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.server.ServerSpeakerManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Seek 到指定位置。
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
            if (current.resourceId() == null) return;

            long serverTime = System.currentTimeMillis();

            // 创建新状态: anchor=now, position=seekPosition, 保持原有 speed
            PlaybackState newState = new PlaybackState(
                current.resourceId(),
                serverTime,
                packet.seekPositionMs,
                current.speed()
            );

            speaker.setPlayback(newState);

            // 如果正在播放，更新 ServerSpeakerManager
            if (newState.isPlaying()) {
                long durationMs = ServerSpeakerManager.getDurationMs(newState.resourceId());
                ServerSpeakerManager.getInstance().registerSpeaker(
                    level.dimension(), packet.pos, newState, durationMs);
            }

            PlatformHelper.INSTANCE.sendToAllTracking(
                level,
                packet.pos,
                new SyncSpeakerStatePacket(packet.pos, newState, speaker.getVolume(), serverTime)
            );
        });
    }
}
