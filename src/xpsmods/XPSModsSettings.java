package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.EnumMap;
import java.util.Map;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * Tunable numbers, edited in game through LunaLib's mod settings menu (fields and defaults in
 * data/config/LunaSettings.csv, ids prefixed "xpsmods_"). Loaded at application load and reloaded
 * whenever the player changes them. The defaults below are only used if LunaLib returns nothing.
 */
public final class XPSModsSettings implements LunaSettingsListener {

    public static final String MOD_ID = "xpsmods";
    private static final Logger log = Global.getLogger(XPSModsSettings.class);

    public static float xpPerHullDamage = 0.05f;
    public static float killXP = 100f;
    public static float reserveShareOfAverage = 0.25f;
    public static boolean debugLog = false;
    public static int maxExtraSlots = 3;
    public static float baseSlotXP = 1000f;
    public static float slotCostMult = 2f;
    public static float creditsPerOP = 1000f;
    public static float minBuildInCredits = 1000f;
    /** Enhancing a built-in hullmod costs this fraction of the normal build-in price. */
    public static float enhanceCostMult = 0.5f;
    public static boolean allowRemoval = true;
    public static float removalFeeFraction = 0.25f;
    /** LWJGL key code for the refit button; 0 means no hotkey. */
    public static int refitHotkey = Keyboard.KEY_S;
    /** Testing only: when above 0, every ship is treated as having this many bought slots. */
    public static int debugGrantSlots = 0;
    public static boolean showUpgradeIcon = true;
    /** Write XP earned and ships with an upgrade available into the post-battle dialog. */
    public static boolean battleReport = true;
    /** Drop stored data for ships not seen (fleet or any storage) for this many game months; 0 = never. */
    public static int pruneAfterMonths = 12;

    private static final HullSize[] SIZES = {HullSize.FRIGATE, HullSize.DESTROYER, HullSize.CRUISER, HullSize.CAPITAL_SHIP};
    private static final String[] SIZE_SUFFIX = {"Frigate", "Destroyer", "Cruiser", "Capital"};

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

    /** Reads the settings now and again every time the player changes them in LunaLib's menu. */
    public static void init() {
        XPSModsSettings listener = new XPSModsSettings();
        LunaSettings.addSettingsListener(listener);
        listener.settingsChanged(MOD_ID);
    }

    @Override
    public void settingsChanged(String modId) {
        if (!MOD_ID.equals(modId)) return;
        try {
            xpPerHullDamage = f("xpPerHullDamage", xpPerHullDamage);
            killXP = f("killXP", killXP);
            reserveShareOfAverage = f("reserveShareOfAverage", reserveShareOfAverage);
            for (int i = 0; i < SIZES.length; i++) {
                hullSizeMult.put(SIZES[i], f("hullSizeMult" + SIZE_SUFFIX[i], hullSizeMult.get(SIZES[i])));
                buildInSizeMult.put(SIZES[i], f("buildInSizeMult" + SIZE_SUFFIX[i], buildInSizeMult.get(SIZES[i])));
            }

            maxExtraSlots = i("maxExtraSlots", maxExtraSlots);
            baseSlotXP = f("baseSlotXP", baseSlotXP);
            slotCostMult = f("slotCostMult", slotCostMult);

            creditsPerOP = f("creditsPerOP", creditsPerOP);
            minBuildInCredits = f("minBuildInCredits", minBuildInCredits);
            enhanceCostMult = f("enhanceCostMult", enhanceCostMult);
            allowRemoval = b("allowRemoval", allowRemoval);
            removalFeeFraction = f("removalFeeFraction", removalFeeFraction);

            refitHotkey = i("refitHotkey", refitHotkey);
            showUpgradeIcon = b("showUpgradeIcon", showUpgradeIcon);
            battleReport = b("battleReport", battleReport);
            pruneAfterMonths = i("pruneAfterMonths", pruneAfterMonths);
            debugLog = b("debugLog", debugLog);
            debugGrantSlots = i("debugGrantSlots", debugGrantSlots);
            log.info("XP S-Mods: settings loaded from LunaLib");
        } catch (Throwable t) {
            log.error("XP S-Mods: could not read LunaLib settings, keeping previous values", t);
        }
    }

    private static float f(String key, float fallback) {
        Float v = LunaSettings.getFloat(MOD_ID, "xpsmods_" + key);
        if (v == null) log.warn("XP S-Mods: setting xpsmods_" + key + " missing, using " + fallback);
        return v != null ? v : fallback;
    }

    private static int i(String key, int fallback) {
        Integer v = LunaSettings.getInt(MOD_ID, "xpsmods_" + key);
        if (v == null) log.warn("XP S-Mods: setting xpsmods_" + key + " missing, using " + fallback);
        return v != null ? v : fallback;
    }

    private static boolean b(String key, boolean fallback) {
        Boolean v = LunaSettings.getBoolean(MOD_ID, "xpsmods_" + key);
        if (v == null) log.warn("XP S-Mods: setting xpsmods_" + key + " missing, using " + fallback);
        return v != null ? v : fallback;
    }
}
