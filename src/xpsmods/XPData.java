package xpsmods;

import com.fs.starfarer.api.Global;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-ship XP, keyed by FleetMemberAPI.getId(). Stored in the sector's persistent data as a
 * plain HashMap of String to Float so saves still load if the mod is removed.
 */
public final class XPData {

    private static final String KEY = "xpsmods_xp";

    private XPData() {}

    @SuppressWarnings("unchecked")
    private static Map<String, Float> xpMap() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object map = data.get(KEY);
        if (!(map instanceof Map)) {
            map = new HashMap<String, Float>();
            data.put(KEY, map);
        }
        return (Map<String, Float>) map;
    }

    public static float getXP(String memberId) {
        Float xp = xpMap().get(memberId);
        return xp != null ? xp : 0f;
    }

    /** Adds XP and returns the new total. */
    public static float addXP(String memberId, float amount) {
        float total = getXP(memberId) + amount;
        xpMap().put(memberId, total);
        return total;
    }
}
