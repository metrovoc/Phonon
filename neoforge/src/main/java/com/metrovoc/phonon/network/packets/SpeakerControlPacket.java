package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.server.ServerSpeakerManager;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: 控制 speaker (play/pause/stop)。
 */
public record SpeakerControlPacket(
    BlockPos pos,
    Action action,
    UUID resourceId
) implements CustomPacketPayload {

    public enum Action {
        PLAY,   // 开始播放 (从头或恢复)
        PAUSE,  // 暂停
        STOP    // 停止 (重置到开头)
    }

    private static final Action[] ACTIONS = Action.values();

    public static final Type<SpeakerControlPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "speaker_control"));

    private static final StreamCodec<ByteBuf, Action> ACTION_CODEC = StreamCodec.of(
        (buf, action) -> buf.writeByte(action.ordinal()),
        buf -> {
            int id = buf.readUnsignedByte();
            if (id >= ACTIONS.length) {
                throw new DecoderException("Unknown speaker action: " + id);
            }
            return ACTIONS[id];
        }
    );

    public static final StreamCodec<ByteBuf, SpeakerControlPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SpeakerControlPacket::pos,
        ACTION_CODEC,
        SpeakerControlPacket::action,
        UUIDCodec.STREAM_CODEC,
        SpeakerControlPacket::resourceId,
        SpeakerControlPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var level = player.level();
            SpeakerBlockEntity speaker = SpeakerPacketValidator.getAccessibleSpeaker(player, packet.pos);
            if (speaker == null) return;

            long serverTime = PlaybackState.nowMs();
            PlaybackState current = speaker.getPlayback();
            PlaybackState newState;

            switch (packet.action) {
                case PLAY -> {
                    if (packet.resourceId == null
                        || AudioManager.getInstance().getResource(packet.resourceId).isEmpty()) {
                        return;
                    }
                    if (current.isPaused() && current.resourceId() != null
                        && current.resourceId().equals(packet.resourceId)) {
                        // 从暂停恢复: 更新 anchor 到现在，保持 position
                        long pausedPos = current.positionAtAnchorMs();
                        newState = new PlaybackState(current.resourceId(), serverTime, pausedPos, 1.0f);
                    } else {
                        // 新播放: 从头开始
                        newState = new PlaybackState(packet.resourceId, serverTime, 0, 1.0f);
                    }

                    speaker.setPlayback(newState);
                    long durationMs = ServerSpeakerManager.getDurationMs(newState.resourceId());
                    ServerSpeakerManager.getInstance().registerSpeaker(
                        level.dimension(), packet.pos, newState, durationMs);
                }
                case PAUSE -> {
                    if (!current.isPlaying() || current.resourceId() == null) return;

                    // 计算当前位置并锁定
                    long currentPos = current.getCurrentPositionMs(serverTime);
                    newState = new PlaybackState(current.resourceId(), serverTime, currentPos, 0f);

                    speaker.setPlayback(newState);
                    ServerSpeakerManager.getInstance().registerSpeaker(
                        level.dimension(), packet.pos, newState, -1);
                }
                case STOP -> {
                    newState = PlaybackState.STOPPED;
                    speaker.setPlayback(newState);
                    ServerSpeakerManager.getInstance().unregisterSpeaker(level.dimension(), packet.pos);
                }
                default -> {
                    return;
                }
            }

            PlatformHelper.INSTANCE.sendToAllTracking(
                level,
                packet.pos,
                SyncSpeakerStatePacket.snapshot(packet.pos, newState, speaker.getVolume(), serverTime)
            );
        });
    }
}
