package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.EnumMap;
import java.util.Map;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.lwjgl.input.Keyboard;

/** Tunable numbers from data/config/xpsmods_settings.json, loaded once at application load. */
public final class XPSModsSettings {

    public static final String MOD_ID = "xpsmods";
    private static final String PATH = "data/config/xpsmods_settings.json";
    private static final Logger log = Global.getLogger(XPSModsSettings.class);

    public static float baseDeployXP = 50f;
    public static float xpPerHullDamage = 0.05f;
    public static float killXP = 100f;
    public static float reserveShareOfAverage = 0.5f;
    public static boolean debugLog = true;
    public static int maxExtraSlots = 3;
    public static float baseSlotXP = 1000f;
    public static float slotCostMult = 2f;
    public static float creditsPerOP = 1500f;
    public static float minBuildInCredits = 10000f;
    public static boolean allowRemoval = true;
    public static float removalFeeFraction = 0.25f;
    /** LWJGL key code for the refit button; 0 means no hotkey. */
    public static int refitHotkey = Keyboard.KEY_S;
    /** Testing only: when above 0, every ship is treated as having this many bought slots. */
    public static int debugGrantSlots = 0;
    public static boolean showUpgradeIcon = true;

    private static final Map<HullSize, Float> hullSizeMult = new EnumMap<HullSize, Float>(HullSize.class);
    static {
        hullSizeMult.put(HullSize.FRIGATE, 1f);
        hullSizeMult.put(HullSize.DESTROYER, 1.5f);
        hullSizeMult.put(HullSize.CRUISER, 2f);
        hullSizeMult.put(HullSize.CAPITAL_SHIP, 3f);
    }

    private static final Map<HullSize, Float> buildInSizeMult = new EnumMap<HullSize, Float>(HullSize.class);
    static {
        buildInSizeMult.put(HullSize.FRIGATE, 1f);
        buildInSizeMult.put(HullSize.DESTROYER, 1.25f);
        buildInSizeMult.put(HullSize.CRUISER, 1.5f);
        buildInSizeMult.put(HullSize.CAPITAL_SHIP, 2f);
    }

    private XPSModsSettings() {}

    public static float buildInSizeMult(HullSize size) {
        Float mult = buildInSizeMult.get(size);
        return mult != null ? mult : 1f;
    }

    public static float hullSizeMult(HullSize size) {
        Float mult = hullSizeMult.get(size);
        return mult != null ? mult : 1f;
    }

    public static void load() throws Exception {
        JSONObject json = Global.getSettings().loadJSON(PATH, MOD_ID);
        baseDeployXP = getFloat(json, "baseDeployXP", baseDeployXP);
        xpPerHullDamage = getFloat(json, "xpPerHullDamage", xpPerHullDamage);
        killXP = getFloat(json, "killXP", killXP);
        reserveShareOfAverage = getFloat(json, "reserveShareOfAverage", reserveShareOfAverage);
        debugLog = json.optBoolean("debugLog", debugLog);
        maxExtraSlots = (int) getFloat(json, "maxExtraSlots", maxExtraSlots);
        debugGrantSlots = (int) getFloat(json, "debugGrantSlots", debugGrantSlots);
        showUpgradeIcon = json.optBoolean("showUpgradeIcon", showUpgradeIcon);
        baseSlotXP = getFloat(json, "baseSlotXP", baseSlotXP);
        slotCostMult = getFloat(json, "slotCostMult", slotCostMult);
        creditsPerOP = getFloat(json, "creditsPerOP", creditsPerOP);
        minBuildInCredits = getFloat(json, "minBuildInCredits", minBuildInCredits);
        allowRemoval = json.optBoolean("allowRemoval", allowRemoval);
        removalFeeFraction = getFloat(json, "removalFeeFraction", removalFeeFraction);
        loadSizeMults(json, "buildInSizeMult", buildInSizeMult);
        if (json.has("refitHotkey")) {
            String key = json.optString("refitHotkey", "").trim();
            refitHotkey = key.isEmpty() ? 0 : Keyboard.getKeyIndex(key.toUpperCase());
            if (!key.isEmpty() && refitHotkey == Keyboard.KEY_NONE) {
                log.warn("XP S-Mods: unknown refitHotkey '" + key + "', hotkey disabled");
            }
        } else {
            log.warn("XP S-Mods: settings missing refitHotkey, using default S");
        }

        loadSizeMults(json, "hullSizeMult", hullSizeMult);
        log.info("XP S-Mods: settings loaded");
    }

    private static void loadSizeMults(JSONObject json, String key, Map<HullSize, Float> into) {
        JSONObject sizes = json.optJSONObject(key);
        if (sizes == null) {
            log.warn("XP S-Mods: settings missing " + key + ", using defaults");
            return;
        }
        for (HullSize size : new HullSize[] {HullSize.FRIGATE, HullSize.DESTROYER, HullSize.CRUISER, HullSize.CAPITAL_SHIP}) {
            into.put(size, getFloat(sizes, size.name(), into.get(size)));
        }
    }

    private static float getFloat(JSONObject json, String key, float fallback) {
        if (!json.has(key)) {
            log.warn("XP S-Mods: settings missing " + key + ", using default " + fallback);
            return fallback;
        }
        return (float) json.optDouble(key, fallback);
    }
}
