package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 同步 speaker 播放状态到客户端。
 * 使用锚点模型: anchorTimeMs + positionAtAnchorMs + speed。
 */
public record SyncSpeakerStatePacket(
    BlockPos pos,
    PlaybackState playback,
    float volume,
    long serverTimeMs
) implements CustomPacketPayload {

    public static final Type<SyncSpeakerStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_speaker_state"));

    public static final StreamCodec<ByteBuf, SyncSpeakerStatePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SyncSpeakerStatePacket::pos,
        StreamCodec.composite(
            UUIDCodec.STREAM_CODEC,
            PlaybackState::resourceId,
            ByteBufCodecs.VAR_LONG,
            PlaybackState::anchorTimeMs,
            ByteBufCodecs.VAR_LONG,
            PlaybackState::positionAtAnchorMs,
            ByteBufCodecs.FLOAT,
            PlaybackState::speed,
            PlaybackState::new
        ),
        SyncSpeakerStatePacket::playback,
        ByteBufCodecs.FLOAT,
        SyncSpeakerStatePacket::volume,
        ByteBufCodecs.VAR_LONG,
        SyncSpeakerStatePacket::serverTimeMs,
        SyncSpeakerStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpeakerStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            long clientTimeMs = System.currentTimeMillis();
            long clockOffset = packet.serverTimeMs - clientTimeMs;

            // 将服务端 anchor 转换为客户端本地时间
            PlaybackState adjusted = new PlaybackState(
                packet.playback.resourceId(),
                packet.playback.anchorTimeMs() - clockOffset,
                packet.playback.positionAtAnchorMs(),
                packet.playback.speed()
            );

            ClientSpeakerManager.getInstance().updateSpeaker(packet.pos, adjusted, packet.volume);
        });
    }
}
