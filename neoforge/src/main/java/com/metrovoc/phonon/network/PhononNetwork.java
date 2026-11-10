package com.metrovoc.phonon.network;

import com.metrovoc.phonon.Constants;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers {@link PhononPayloads} with NeoForge. */
public final class PhononNetwork {
    private PhononNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Constants.MOD_ID)
            .versioned(PhononPayloads.VERSION)
            .optional();
        PhononPayloads.TO_CLIENT.forEach(entry -> toClient(registrar, entry));
        PhononPayloads.TO_SERVER.forEach(entry -> toServer(registrar, entry));
    }

    private static <T extends CustomPacketPayload> void toClient(PayloadRegistrar registrar, PhononPayloads.Entry<T> entry) {
        registrar.playToClient(entry.type(), entry.codec(), (payload, context) -> entry.handler().accept(payload, wrap(context)));
    }

    private static <T extends CustomPacketPayload> void toServer(PayloadRegistrar registrar, PhononPayloads.Entry<T> entry) {
        registrar.playToServer(entry.type(), entry.codec(), (payload, context) -> entry.handler().accept(payload, wrap(context)));
    }

    private static PacketContext wrap(IPayloadContext context) {
        return new PacketContext() {
            @Override
            public net.minecraft.world.entity.player.Player player() {
                return context.player();
            }

            @Override
            public void enqueueWork(Runnable work) {
                context.enqueueWork(work);
            }
        };
    }
}
