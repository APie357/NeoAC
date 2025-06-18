package dev.andrewd1.neoac;

import dev.andrewd1.neoac.net.ModListPayload;
import dev.andrewd1.neoac.net.NetworkHelper;
import dev.andrewd1.neoac.util.HashUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.jmx.Server;

import java.io.File;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

@Mod(NeoAC.MOD_ID)
public class NeoAC {
    public static final String MOD_ID = "neoac";
    public static final String MOD_NAME = "NeoAC";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    public static HashMap<String, String> loadedModHashes = new HashMap<>();
    public static final HashMap<ServerPlayer, Timer> playerModListDisconnectTimer = new HashMap<>();

    public static NeoAC instance;

    private static ModContainer modContainer;

    public NeoAC(IEventBus modEventBus, ModContainer modContainer) {
        instance = this;
        this.modContainer = modContainer;
        modEventBus.addListener(this::commonSetup);

        LOGGER.info("Validating mods...");
        for (IModInfo mod : ModList.get().getMods()) {
            File modFile;
            String hash;
            try {
                modFile = mod.getOwningFile().getFile().getFilePath().toFile();
                if (!modFile.exists()) { continue; }
                hash = HashUtil.bytesToHex(HashUtil.hash(modFile));
            } catch (UnsupportedOperationException e) {
                LOGGER.error("Error while hashing mod " + mod.getModId());
                LOGGER.error(e);
                hash = "NULL";
            }

            loadedModHashes.put(mod.getModId(), hash);

            LOGGER.debug("Found mod %s at %s with hash %s".formatted(
                    mod.getModId(),
                    mod.getOwningFile().getFile().getFilePath(),
                    hash
            ));
        }

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (NeoAC) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        modEventBus.register(NetworkHelper.class);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        playerModListDisconnectTimer.clear();
    }

    @SubscribeEvent
    public void onPlayerConnect(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        ServerPlayer serverPlayer = (ServerPlayer) player;
        MinecraftServer server = player.getServer();
        assert server != null;
        PacketDistributor.sendToPlayer(serverPlayer, new ModListPayload(new HashMap<>()));
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                LOGGER.warn("Client %s didn't send their mod list in time! Disconnecting them.".formatted(serverPlayer.getUUID()));
                serverPlayer.connection.disconnect(Component.literal(("""
                        §c%s
                        
                        Couldn't check your client's security.
                        Did you install NeoAC?"""
                ).formatted(MOD_NAME)));
                playerModListDisconnectTimer.remove(player);
            }
        }, Config.modListWaitDuration * 1000L);
        playerModListDisconnectTimer.put(
                serverPlayer,
                timer
        );
    }

    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
    }
}
