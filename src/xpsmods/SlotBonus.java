package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;

/** Puts the hidden slot hullmod on ships that have a slot bonus. The hullmod stays on permanently. */
public final class SlotBonus {

    private SlotBonus() {}

    /** Adds the hullmod if the ship has a bonus and lacks it, then refreshes its stats. */
    public static void ensureHullmod(FleetMemberAPI member) {
        if (member == null || member.isFighterWing()) return;
        if (XPData.getSlotBonus(member.getId()) <= 0) return;

        ShipVariantAPI variant = member.getVariant();
        if (variant.hasHullMod(XPSlotsHullmod.ID)) return;

        // Never edit a shared stock/goal/empty variant: give the ship its own copy first.
        if (variant.isStockVariant() || variant.isGoalVariant() || variant.isEmptyHullVariant()) {
            variant = variant.clone();
            variant.setSource(VariantSource.REFIT);
            member.setVariant(variant, false, false);
        }
        variant.addPermaMod(XPSlotsHullmod.ID, false);
        member.updateStats();
    }

    /** Adds the hullmod to a variant the caller already owns (for example the refit screen's copy). */
    public static void ensureHullmod(ShipVariantAPI variant) {
        if (variant != null && !variant.hasHullMod(XPSlotsHullmod.ID)) {
            variant.addPermaMod(XPSlotsHullmod.ID, false);
        }
    }

    public static void ensureHullmodOnPlayerFleet() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            ensureHullmod(member);
        }
    }
}
