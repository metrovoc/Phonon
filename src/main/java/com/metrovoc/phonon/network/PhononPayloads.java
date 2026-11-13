package com.metrovoc.phonon.network;

import com.metrovoc.phonon.network.packets.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.function.BiConsumer;

/** Every packet Phonon sends, in one place, for each loader to register. */
public final class PhononPayloads {
    /** Bump when any packet's layout changes. */
    public static final String VERSION = "3.5.0";

    public record Entry<T extends CustomPacketPayload>(
        CustomPacketPayload.Type<T> type,
        StreamCodec<ByteBuf, T> codec,
        BiConsumer<T, PacketContext> handler
    ) {}

    public static final List<Entry<?>> TO_CLIENT = List.of(
        new Entry<>(SyncAudioResourcesPacket.TYPE, SyncAudioResourcesPacket.CODEC, SyncAudioResourcesPacket::handle),
        new Entry<>(SyncSpeakerStatePacket.TYPE, SyncSpeakerStatePacket.CODEC, SyncSpeakerStatePacket::handle),
        new Entry<>(AudioChunkPacket.TYPE, AudioChunkPacket.CODEC, AudioChunkPacket::handle),
        new Entry<>(SyncSpeakerVolumePacket.TYPE, SyncSpeakerVolumePacket.CODEC, SyncSpeakerVolumePacket::handle),
        new Entry<>(AudioStreamStartPacket.TYPE, AudioStreamStartPacket.CODEC, AudioStreamStartPacket::handle),
        new Entry<>(AudioStreamFailedPacket.TYPE, AudioStreamFailedPacket.CODEC, AudioStreamFailedPacket::handle),
        new Entry<>(AddFailedPacket.TYPE, AddFailedPacket.CODEC, AddFailedPacket::handle),
        new Entry<>(SyncSettingsPacket.TYPE, SyncSettingsPacket.CODEC, SyncSettingsPacket::handle),
        new Entry<>(SearchResultsPacket.TYPE, SearchResultsPacket.CODEC, SearchResultsPacket::handle),
        new Entry<>(SearchThumbnailPacket.TYPE, SearchThumbnailPacket.CODEC, SearchThumbnailPacket::handle),
        new Entry<>(SyncBoomboxPlacePacket.TYPE, SyncBoomboxPlacePacket.CODEC, SyncBoomboxPlacePacket::handle),
        new Entry<>(SyncStationPacket.TYPE, SyncStationPacket.CODEC, SyncStationPacket::handle),
        new Entry<>(SyncStationsPacket.TYPE, SyncStationsPacket.CODEC, SyncStationsPacket::handle),
        new Entry<>(SyncLibraryJobsPacket.TYPE, SyncLibraryJobsPacket.CODEC, SyncLibraryJobsPacket::handle)
    );

    public static final List<Entry<?>> TO_SERVER = List.of(
        new Entry<>(SearchPacket.TYPE, SearchPacket.CODEC, SearchPacket::handle),
        new Entry<>(RequestAudioPacket.TYPE, RequestAudioPacket.CODEC, RequestAudioPacket::handle),
        new Entry<>(CancelAudioStreamPacket.TYPE, CancelAudioStreamPacket.CODEC, CancelAudioStreamPacket::handle),
        new Entry<>(SpeakerActionPacket.TYPE, SpeakerActionPacket.CODEC, SpeakerActionPacket::handle),
        new Entry<>(SpeakerSeekPacket.TYPE, SpeakerSeekPacket.CODEC, SpeakerSeekPacket::handle),
        new Entry<>(SpeakerVolumePacket.TYPE, SpeakerVolumePacket.CODEC, SpeakerVolumePacket::handle)
    );

    private PhononPayloads() {}
}
