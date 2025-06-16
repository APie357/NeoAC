package dev.andrewd1.neoac;

import dev.andrewd1.neoac.net.ModListPayload;
import dev.andrewd1.neoac.net.NetworkHelper;
import dev.andrewd1.neoac.util.HashUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;

@Mod(NeoAC.MOD_ID)
public class NeoAC {
    public static final String MOD_ID = "neoac";
    public static final String MOD_NAME = "NeoAC";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    public static HashMap<String, String> loadedModHashes = new HashMap<>();

    public static NeoAC instance;

    public NeoAC(IEventBus modEventBus, ModContainer modContainer) {
        instance = this;
        modEventBus.addListener(this::commonSetup);

        LOGGER.info("Validating mods...");
        for (IModInfo mod : ModList.get().getMods()) {
            IModFile modFile = mod.getOwningFile().getFile();
            String hash = HashUtil.bytesToHex(HashUtil.hash(modFile.getFilePath().toFile()));

            loadedModHashes.put(mod.getModId(), hash);

            LOGGER.debug("Found mod %s at %s with hash %s".formatted(
                    mod.getModId(),
                    modFile.getFilePath(),
                    hash
            ));
        }

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (NeoAC) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        modEventBus.register(NetworkHelper.class);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private void commonSetup(FMLCommonSetupEvent event) {

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    @SubscribeEvent
    public void onPlayerConnect(PlayerEvent.PlayerLoggedInEvent event) {
        PacketDistributor.sendToAllPlayers(new ModListPayload(new HashMap<>()));
    }


    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

        }
    }
}
