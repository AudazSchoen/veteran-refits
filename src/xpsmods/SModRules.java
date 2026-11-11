package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Which hullmods can be built in or removed, what it costs, and the S-mod limit. */
final class SModRules {

    private SModRules() {}

    /** Credits to build in: max(minBuildInCredits, creditsPerOP * OP cost * buildInSizeMult). */
    static float buildInCost(HullModSpecAPI spec, HullSize size) {
        float cost = XPSModsSettings.creditsPerOP * spec.getCostFor(size) * XPSModsSettings.buildInSizeMult(size);
        return Math.max(XPSModsSettings.minBuildInCredits, cost);
    }

    /** Credits to remove an S-mod: a fraction of the build-in cost, with the same minimum. */
    static float removalCost(HullModSpecAPI spec, HullSize size) {
        return Math.max(XPSModsSettings.minBuildInCredits, buildInCost(spec, size) * XPSModsSettings.removalFeeFraction);
    }

    /** Credits to enhance a built-in hullmod: enhanceCostMult x the normal build-in price. */
    static float enhanceCost(HullModSpecAPI spec, HullSize size) {
        return buildInCost(spec, size) * XPSModsSettings.enhanceCostMult;
    }

    /** Credits to remove an enhancement: the removal fee on the enhancement price (no minimum). */
    static float enhanceRemovalCost(HullModSpecAPI spec, HullSize size) {
        return enhanceCost(spec, size) * XPSModsSettings.removalFeeFraction;
    }

    /** Installed hullmods that can become S-mods (same filter as Progressive S-Mods and vanilla). */
    static List<HullModSpecAPI> buildInCandidates(ShipVariantAPI variant) {
        List<HullModSpecAPI> result = new ArrayList<HullModSpecAPI>();
        for (String id : variant.getNonBuiltInHullmods()) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
            if (spec == null || spec.isHidden() || spec.isHiddenEverywhere()) continue;
            if (spec.hasTag(Tags.HULLMOD_DMOD) || spec.hasTag(Tags.HULLMOD_NO_BUILD_IN)) continue;
            if (variant.getPermaMods().contains(id)) continue;
            result.add(spec);
        }
        sortByName(result);
        return result;
    }

    /** Current S-mods, counted the way vanilla does (Misc.isSpecialMod). */
    static List<HullModSpecAPI> currentSMods(ShipVariantAPI variant) {
        List<HullModSpecAPI> result = new ArrayList<HullModSpecAPI>();
        for (String id : variant.getSMods()) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
            if (spec != null && Misc.isSpecialMod(variant, spec)) result.add(spec);
        }
        sortByName(result);
        return result;
    }

    /**
     * Built-in hullmods that can be enhanced by building them in: a real S-mod effect, not yet
     * enhanced (same check as vanilla's Misc.hasSModdableBuiltIns and Progressive S-Mods).
     * Enhancing doesn't count against the S-mod limit (vanilla's Misc.isSpecialMod skips built-ins).
     */
    static List<HullModSpecAPI> enhanceCandidates(ShipVariantAPI variant) {
        List<HullModSpecAPI> result = new ArrayList<HullModSpecAPI>();
        if (!Misc.CAN_SMOD_BUILT_IN || variant.getHullSpec() == null) return result;
        for (String id : variant.getHullSpec().getBuiltInMods()) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
            if (spec == null || spec.isHidden() || spec.isHiddenEverywhere() || spec.hasTag(Tags.HULLMOD_DMOD)) continue;
            if (variant.getSModdedBuiltIns().contains(id)) continue;
            if (!spec.getEffect().hasSModEffect() || spec.getEffect().isSModEffectAPenalty()) continue;
            result.add(spec);
        }
        sortByName(result);
        return result;
    }

    /** Built-in hullmods that have already been enhanced. */
    static List<HullModSpecAPI> enhancedBuiltIns(ShipVariantAPI variant) {
        List<HullModSpecAPI> result = new ArrayList<HullModSpecAPI>();
        for (String id : variant.getSModdedBuiltIns()) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
            if (spec != null) result.add(spec);
        }
        sortByName(result);
        return result;
    }

    /** The ship's S-mod limit, including bought slots once the hidden hullmod is on it. */
    static int sModLimit(FleetMemberAPI member) {
        return Math.round(member.getStats().getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD)
                .computeEffective(Misc.MAX_PERMA_MODS));
    }

    /** True when a module of a multi-section ship is selected rather than the main hull. */
    static boolean isModule(FleetMemberAPI member, ShipVariantAPI variant) {
        return !variant.getHullSpec().getBaseHullId().equals(member.getHullSpec().getBaseHullId());
    }

    private static void sortByName(List<HullModSpecAPI> specs) {
        Collections.sort(specs, new Comparator<HullModSpecAPI>() {
            @Override
            public int compare(HullModSpecAPI a, HullModSpecAPI b) {
                return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
            }
        });
    }
}
