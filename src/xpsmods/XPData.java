package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-ship XP, bought S-mod slots and last-seen time, keyed by FleetMemberAPI.getId(). Stored in the
 * sector's persistent data as plain HashMaps of String to Float / Integer / Long.
 */
public final class XPData {

    private static final String XP_KEY = "xpsmods_xp";
    private static final String SLOTS_KEY = "xpsmods_slots";
    private static final String LAST_SEEN_KEY = "xpsmods_lastSeen";

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

    /** Records that the ship still exists (game timestamp). */
    public static void markSeen(String memberId, long timestamp) {
        XPData.<Long>map(LAST_SEEN_KEY).put(memberId, timestamp);
    }

    /** Game timestamp the ship was last seen, or null if never recorded. */
    public static Long getLastSeen(String memberId) {
        return XPData.<Long>map(LAST_SEEN_KEY).get(memberId);
    }

    /** Every ship id we hold any data for. */
    public static Set<String> allIds() {
        Set<String> ids = new LinkedHashSet<String>();
        ids.addAll(XPData.<Float>map(XP_KEY).keySet());
        ids.addAll(XPData.<Integer>map(SLOTS_KEY).keySet());
        ids.addAll(XPData.<Long>map(LAST_SEEN_KEY).keySet());
        return ids;
    }

    /** Drops everything stored for the ship. */
    public static void removeAll(String memberId) {
        XPData.<Float>map(XP_KEY).remove(memberId);
        XPData.<Integer>map(SLOTS_KEY).remove(memberId);
        XPData.<Long>map(LAST_SEEN_KEY).remove(memberId);
    }

    /** XP cost of the next slot: baseSlotXP * slotCostMult ^ slotsAlreadyBought. */
    public static float nextSlotCost(int bought) {
        return XPSModsSettings.baseSlotXP * (float) Math.pow(XPSModsSettings.slotCostMult, bought);
    }

    /** True when the ship has enough XP to buy its next slot and isn't at the cap. */
    public static boolean canBuySlot(String memberId) {
        if (memberId == null) return false;
        int bought = getBoughtSlots(memberId);
        return bought < XPSModsSettings.maxExtraSlots && getXP(memberId) >= nextSlotCost(bought);
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
