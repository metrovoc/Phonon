package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.station.Playlist;
import com.metrovoc.phonon.station.Station;
import com.metrovoc.phonon.station.StationView;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Network forms of stations and playlists. */
final class StationCodecs {
    private static final StreamCodec<ByteBuf, List<UUID>> TRACKS = ByteBufCodecs.<ByteBuf, UUID, List<UUID>>collection(
        ArrayList::new, UUIDCodec.STREAM_CODEC, Station.MAX_QUEUE);
    private static final StreamCodec<ByteBuf, String> NAME = ByteBufCodecs.stringUtf8(256);

    static final StreamCodec<ByteBuf, StationView> VIEW = StreamCodec.of(
        (buf, view) -> {
            UUIDCodec.STREAM_CODEC.encode(buf, view.id());
            NAME.encode(buf, view.name());
            UUIDCodec.STREAM_CODEC.encode(buf, view.ownerId());
            NAME.encode(buf, view.ownerName());
            buf.writeBoolean(view.shared());
            TRACKS.encode(buf, view.queue());
            buf.writeInt(view.cursor());
            buf.writeByte(view.repeat().ordinal());
            buf.writeBoolean(view.shuffle());
        },
        buf -> new StationView(
            UUIDCodec.STREAM_CODEC.decode(buf),
            NAME.decode(buf),
            UUIDCodec.STREAM_CODEC.decode(buf),
            NAME.decode(buf),
            buf.readBoolean(),
            List.copyOf(TRACKS.decode(buf)),
            buf.readInt(),
            Station.Repeat.values()[Math.min(buf.readUnsignedByte(), Station.Repeat.values().length - 1)],
            buf.readBoolean()
        )
    );

    static final StreamCodec<ByteBuf, Playlist> PLAYLIST = StreamCodec.of(
        (buf, playlist) -> {
            UUIDCodec.STREAM_CODEC.encode(buf, playlist.id());
            NAME.encode(buf, playlist.name());
            UUIDCodec.STREAM_CODEC.encode(buf, playlist.ownerId());
            NAME.encode(buf, playlist.ownerName());
            TRACKS.encode(buf, playlist.tracks());
        },
        buf -> new Playlist(
            UUIDCodec.STREAM_CODEC.decode(buf),
            NAME.decode(buf),
            UUIDCodec.STREAM_CODEC.decode(buf),
            NAME.decode(buf),
            List.copyOf(TRACKS.decode(buf))
        )
    );

    private StationCodecs() {}
}
