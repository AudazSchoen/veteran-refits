package xpsmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

/** Hidden hullmod that raises the ship's S-mod limit by its XP-bought slots. */
public class XPSlotsHullmod extends BaseHullMod {

    public static final String ID = "xpsmods_slots";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        FleetMemberAPI member = stats.getFleetMember();
        if (member == null) return;
        int bonus = XPData.getSlotBonus(member.getId());
        if (bonus > 0) {
            stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).modifyFlat(id, bonus);
        } else {
            stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).unmodifyFlat(id);
        }
    }
}
