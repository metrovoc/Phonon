package com.metrovoc.phonon.network;

import net.minecraft.world.entity.player.Player;

/** What a packet handler needs from the loader: the sender or receiver, and the game thread. */
public interface PacketContext {
    Player player();

    void enqueueWork(Runnable work);
}
