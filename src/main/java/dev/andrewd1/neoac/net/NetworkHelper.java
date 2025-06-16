package dev.andrewd1.neoac.net;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHelper {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").executesOn(HandlerThread.NETWORK);

        registrar.playBidirectional(
                ModListPayload.TYPE,
                ModListPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ModListPayload::clientHandler,
                        ModListPayload::serverHandler
                )
        );
    }
}