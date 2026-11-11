package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-ship XP and bought S-mod slots, keyed by FleetMemberAPI.getId(). Stored in the sector's
 * persistent data as plain HashMaps of String to Float / Integer.
 */
public final class XPData {

    private static final String XP_KEY = "xpsmods_xp";
    private static final String SLOTS_KEY = "xpsmods_slots";

    private XPData() {}

    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> map(String key) {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object map = data.get(key);
        if (!(map instanceof Map)) {
            map = new HashMap<String, V>();
            data.put(key, map);
        }
        return (Map<String, V>) map;
    }

    public static float getXP(String memberId) {
        Float xp = XPData.<Float>map(XP_KEY).get(memberId);
        return xp != null ? xp : 0f;
    }

    /** Adds XP and returns the new total. */
    public static float addXP(String memberId, float amount) {
        float total = getXP(memberId) + amount;
        XPData.<Float>map(XP_KEY).put(memberId, total);
        return total;
    }

    /** Removes XP (never below 0) and returns the new total. */
    public static float spendXP(String memberId, float amount) {
        float total = Math.max(0f, getXP(memberId) - amount);
        XPData.<Float>map(XP_KEY).put(memberId, total);
        return total;
    }

    public static int getBoughtSlots(String memberId) {
        Integer slots = XPData.<Integer>map(SLOTS_KEY).get(memberId);
        return slots != null ? slots : 0;
    }

    public static void setBoughtSlots(String memberId, int slots) {
        XPData.<Integer>map(SLOTS_KEY).put(memberId, slots);
    }

    /**
     * Extra S-mod slots this ship gets: bought slots (or the debug override), capped at maxExtraSlots.
     * Safe to call outside the campaign (missions, simulator), where it returns 0.
     */
    public static int getSlotBonus(String memberId) {
        SectorAPI sector = Global.getSector();
        if (memberId == null || sector == null || sector.getPersistentData() == null) return 0;
        int slots = XPSModsSettings.debugGrantSlots > 0 ? XPSModsSettings.debugGrantSlots : getBoughtSlots(memberId);
        return Math.max(0, Math.min(slots, XPSModsSettings.maxExtraSlots));
    }
}
