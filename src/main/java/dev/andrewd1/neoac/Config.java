package dev.andrewd1.neoac;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@EventBusSubscriber(modid = NeoAC.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue AUTO_UPDATE_MOD_ALLOWLIST = BUILDER
            .comment(" Automatically update the mod allowlist when a mod is added or updated. Nothing happens when a mod" +
                     " is removed.")
            .define("auto_update_mod_allowlist", true);
    private static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_ALLOWLIST = BUILDER
            .comment(" A list of allowed mods and their hashes. If the mod isn't found or has a different hash, it will " +
                     "be printed to the log and the configuration file will update to reflect the change if \"auto_updat" +
                     "e_mod_allowlist = true\". You probably shouldn't edit this manually.")
            .defineListAllowEmpty("mod_allowlist", new ArrayList<>(), () -> "", Config::validateModAllowlist);
    private static final ModConfigSpec.IntValue MOD_LIST_MAX_WAIT_TIME = BUILDER
            .comment(" The maximum amount of time to wait for a client to send their mod list to the server. Setting it " +
                     "too low may lead to players being kicked when they join.")
            .defineInRange("max_client_wait_duration", 10, 0, 120);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean autoUpdateModAllowlist = false;
    public static final HashMap<String, String> modAllowlist = new HashMap<>();
    public static int modListWaitDuration = 10;

    public static final List<String> violations = new ArrayList<>();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        autoUpdateModAllowlist = AUTO_UPDATE_MOD_ALLOWLIST.get();
        modListWaitDuration = MOD_LIST_MAX_WAIT_TIME.get();
        modAllowlist.clear();
        violations.clear();
        for (var entry : MOD_ALLOWLIST.get()) {
            String[] pair = entry.split(":");
            if (pair.length != 2) {
                NeoAC.LOGGER.error("Invalid mod allowlist entry: " + entry);
                continue;
            }
            modAllowlist.put(pair[0], pair[1]);
        }
        for (String id : NeoAC.loadedModHashes.keySet()) {
            if (!modAllowlist.containsKey(id) || !Objects.equals(modAllowlist.get(id), NeoAC.loadedModHashes.get(id))) {
                violations.add(id);
                NeoAC.LOGGER.warn("Mod %s is mismatched! Current hash: %s | Configured hash: %s".formatted(id, NeoAC.loadedModHashes.get(id), modAllowlist.get(id)));
            }
        }
        if (!violations.isEmpty()) {
            if (autoUpdateModAllowlist) {
                NeoAC.LOGGER.info("Automatically updating allowlist in config");
                NeoAC.LOGGER.info("Set \"auto_update_mod_allowlist = false\" in the config to disable");
                for (String id : Config.violations) {
                    Config.modAllowlist.put(id, NeoAC.loadedModHashes.get(id));
                }
                Config.write();
            }
        }
    }

    private static boolean validateModAllowlist(Object obj) {
        if (!(obj instanceof String str)) return false;
        if (str.split(":").length != 2) return false;
        return true;
    }

    public static void write() {
        ArrayList<String> allowlist = new ArrayList<>();
        for (String id : modAllowlist.keySet()) {
            allowlist.add("%s:%s".formatted(id, modAllowlist.get(id)));
        }
        MOD_ALLOWLIST.set(allowlist);
        SPEC.save();
        NeoAC.LOGGER.info("Updated mod allowlist");
    }
}
