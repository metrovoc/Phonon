package driver;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Runs the driver on Fabric, as a development-only mod of its own. */
public final class FabricHook implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PhononSteps.init();
            Driver.tick();
        });
    }
}
