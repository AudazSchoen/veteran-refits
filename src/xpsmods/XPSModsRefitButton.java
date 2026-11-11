package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.MutableValue;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import lunalib.lunaRefit.BaseRefitButton;
import org.apache.log4j.Logger;

/**
 * LunaLib refit panel: shows a ship's XP and S-mods, buys extra S-mod slots with XP (anywhere),
 * and builds in or removes S-mods for credits (only while docked at a market).
 */
public class XPSModsRefitButton extends BaseRefitButton {

    private static final Logger log = Global.getLogger(XPSModsRefitButton.class);
    private static final float WIDTH = 700f;
    private static final float HEIGHT = 560f;
    private static final String BUY_SLOT = "xpsmods_buy_slot";
    private static final String CLOSE = "xpsmods_close";
    private static final String BUILD_IN_PREFIX = "xpsmods_build_in:";
    private static final String REMOVE_PREFIX = "xpsmods_remove:";

    @Override
    public String getButtonName(FleetMemberAPI member, ShipVariantAPI variant) {
        return "Ship upgrades (XP S-Mods)";
    }

    @Override
    public String getIconName(FleetMemberAPI member, ShipVariantAPI variant) {
        return "graphics/hullmods/integrated_targeting_unit.png";
    }

    @Override
    public boolean hasPanel(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return true;
    }

    @Override
    public boolean isClickable(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return member != null && variant != null;
    }

    @Override
    public float getPanelWidth(FleetMemberAPI member, ShipVariantAPI variant) {
        return WIDTH;
    }

    @Override
    public float getPanelHeight(FleetMemberAPI member, ShipVariantAPI variant) {
        return HEIGHT;
    }

    @Override
    public void initPanel(CustomPanelAPI backgroundPanel, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        new Content(backgroundPanel, member, variant, market).build();
    }

    /** XP cost of the next slot: baseSlotXP * slotCostMult ^ slotsAlreadyBought. */
    static float nextSlotCost(int bought) {
        return XPSModsSettings.baseSlotXP * (float) Math.pow(XPSModsSettings.slotCostMult, bought);
    }

    /** Credits to build in: max(minBuildInCredits, creditsPerOP * OP cost * buildInSizeMult). */
    static float buildInCost(HullModSpecAPI spec, HullSize size) {
        float cost = XPSModsSettings.creditsPerOP * spec.getCostFor(size) * XPSModsSettings.buildInSizeMult(size);
        return Math.max(XPSModsSettings.minBuildInCredits, cost);
    }

    /** Credits to remove an S-mod: a fraction of the build-in cost, with the same minimum. */
    static float removalCost(HullModSpecAPI spec, HullSize size) {
        return Math.max(XPSModsSettings.minBuildInCredits, buildInCost(spec, size) * XPSModsSettings.removalFeeFraction);
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
        return result;
    }

    /** Current S-mods, counted the way vanilla does (Misc.isSpecialMod). */
    static List<HullModSpecAPI> currentSMods(ShipVariantAPI variant) {
        List<HullModSpecAPI> result = new ArrayList<HullModSpecAPI>();
        for (String id : variant.getSMods()) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
            if (spec != null && Misc.isSpecialMod(variant, spec)) result.add(spec);
        }
        return result;
    }

    private static MutableValue credits() {
        return Global.getSector().getPlayerFleet().getCargo().getCredits();
    }

    private static String fmt(float value) {
        return Misc.getWithDGS(Math.round(value));
    }

    /** One open panel. Rebuilds its contents after each action. */
    private class Content extends BaseCustomUIPanelPlugin {
        private final CustomPanelAPI background;
        private final FleetMemberAPI member;
        private final ShipVariantAPI variant;
        private final MarketAPI market;
        private CustomPanelAPI inner;

        Content(CustomPanelAPI background, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
            this.background = background;
            this.member = member;
            this.variant = variant;
            this.market = market;
        }

        /** A module of a multi-section ship is selected rather than the main hull. */
        private boolean isModule() {
            return !variant.getHullSpec().getBaseHullId().equals(member.getHullSpec().getBaseHullId());
        }

        private int sModLimit() {
            return Math.round(member.getStats().getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD)
                    .computeEffective(Misc.MAX_PERMA_MODS));
        }

        void build() {
            if (inner != null) background.removeComponent(inner);
            float pad = 10f;
            float w = WIDTH - 2 * pad;
            float buttonW = 200f;
            inner = background.createCustomPanel(WIDTH, HEIGHT, this);
            Color hl = Misc.getHighlightColor();
            Color bad = Misc.getNegativeHighlightColor();
            Color gray = Misc.getGrayColor();

            // Fixed footer with the Close button; everything else scrolls above it.
            float footerH = 40f;
            TooltipMakerAPI t = inner.createUIElement(w, HEIGHT - footerH - pad, true);

            String id = member.getId();
            HullSize size = variant.getHullSize();
            float xp = XPData.getXP(id);
            int bought = XPData.getBoughtSlots(id);
            int bonus = XPData.getSlotBonus(id);
            int limit = sModLimit();
            // The stat already includes our bonus when the hidden hullmod is on the ship.
            int vanilla = limit - (member.getVariant().hasHullMod(XPSlotsHullmod.ID) ? bonus : 0);
            int used = Misc.getCurrSpecialMods(variant);
            int maxExtra = XPSModsSettings.maxExtraSlots;
            float slotCost = nextSlotCost(bought);
            float credits = credits().get();
            boolean module = isModule();

            t.addSectionHeading(member.getShipName() + ", " + member.getHullSpec().getHullNameWithDashClass(),
                    Alignment.MID, 0f);
            t.addPara("Ship XP: %s    Credits: %s", pad, hl, fmt(xp), fmt(credits));
            t.addPara("S-mods: %s used of %s (vanilla %s + bought %s)", pad, hl,
                    String.valueOf(used), String.valueOf(limit), String.valueOf(vanilla), String.valueOf(bonus));

            // --- Buy a slot with XP (anywhere) ---
            t.addSectionHeading("Extra S-mod slots (XP)", Alignment.MID, pad * 2);
            t.addPara("Bought: %s of %s", pad, hl, String.valueOf(bought), String.valueOf(maxExtra));
            String slotReason = null;
            if (module) {
                slotReason = "Select the main hull to buy S-mod slots.";
            } else if (bought >= maxExtra) {
                slotReason = "This ship has reached the maximum of " + maxExtra + " extra slots.";
            } else {
                t.addPara("Next slot costs %s XP.", pad, hl, fmt(slotCost));
                if (xp < slotCost) slotReason = "Not enough XP: needs " + fmt(slotCost - xp) + " more.";
            }
            ButtonAPI buySlot = button(t, "Buy S-mod slot", BUY_SLOT, buttonW, pad);
            buySlot.setEnabled(slotReason == null);
            if (slotReason != null) t.addPara(slotReason, pad, bad);

            // --- Build in / remove for credits (docked only) ---
            String creditReason = null;
            if (module) creditReason = "Select the main hull to build in or remove S-mods.";
            else if (market == null) creditReason = "Dock at a market to build in or remove S-mods.";

            t.addSectionHeading("Build in hullmods (credits)", Alignment.MID, pad * 2);
            if (creditReason != null) t.addPara(creditReason, pad, bad);
            else if (used >= limit) t.addPara("No free S-mod slot. Buy a slot or remove an S-mod first.", pad, bad);
            List<HullModSpecAPI> candidates = buildInCandidates(variant);
            if (candidates.isEmpty()) t.addPara("No installed hullmods can be built in.", pad, gray);
            for (HullModSpecAPI spec : candidates) {
                float cost = buildInCost(spec, size);
                t.addPara("%s (%s OP): %s credits", pad, hl,
                        spec.getDisplayName(), String.valueOf(spec.getCostFor(size)), fmt(cost));
                ButtonAPI b = button(t, "Build in " + spec.getDisplayName(), BUILD_IN_PREFIX + spec.getId(), buttonW + 100f, 3f);
                b.setEnabled(creditReason == null && used < limit && credits >= cost);
            }

            if (XPSModsSettings.allowRemoval) {
                t.addSectionHeading("Remove S-mods (credits)", Alignment.MID, pad * 2);
                if (creditReason != null) t.addPara(creditReason, pad, bad);
                t.addPara("The hullmod is removed from the ship entirely. Bought slots stay.", pad, gray);
                List<HullModSpecAPI> sMods = currentSMods(variant);
                if (sMods.isEmpty()) t.addPara("This ship has no S-mods.", pad, gray);
                for (HullModSpecAPI spec : sMods) {
                    float fee = removalCost(spec, size);
                    t.addPara("%s: %s credits to remove", pad, hl, spec.getDisplayName(), fmt(fee));
                    ButtonAPI b = button(t, "Remove " + spec.getDisplayName(), REMOVE_PREFIX + spec.getId(), buttonW + 100f, 3f);
                    b.setEnabled(creditReason == null && credits >= fee);
                }
            }

            inner.addUIElement(t).inTL(pad, pad);

            TooltipMakerAPI footer = inner.createUIElement(w, footerH, false);
            button(footer, "Close", CLOSE, 120f, 0f);
            inner.addUIElement(footer).inTL(pad, HEIGHT - footerH);

            background.addComponent(inner).inTL(0f, 0f);
        }

        private ButtonAPI button(TooltipMakerAPI t, String text, String data, float width, float pad) {
            return t.addButton(text, data, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.ALL, width, 25f, pad);
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (!(buttonId instanceof String)) return;
            String id = (String) buttonId;
            if (CLOSE.equals(id)) {
                closePanel();
                return;
            }
            try {
                if (BUY_SLOT.equals(id)) buySlot();
                else if (id.startsWith(BUILD_IN_PREFIX)) buildIn(id.substring(BUILD_IN_PREFIX.length()));
                else if (id.startsWith(REMOVE_PREFIX)) remove(id.substring(REMOVE_PREFIX.length()));
            } catch (Throwable t) {
                log.error("XP S-Mods: refit panel action failed (" + id + ")", t);
            }
            build();
        }

        private void buySlot() {
            if (isModule()) return;
            String id = member.getId();
            int bought = XPData.getBoughtSlots(id);
            float cost = nextSlotCost(bought);
            if (bought >= XPSModsSettings.maxExtraSlots || XPData.getXP(id) < cost) return;

            XPData.spendXP(id, cost);
            XPData.setBoughtSlots(id, bought + 1);
            // Put the hullmod on the refit screen's variant too: LunaLib's refresh saves that variant
            // back onto the ship, which would otherwise drop a hullmod added only to member.getVariant().
            SlotBonus.ensureHullmod(variant);
            SlotBonus.ensureHullmod(member);
            member.updateStats();
            refreshVariant();
            Global.getSoundPlayer().playUISound("ui_char_spent_story_point_technology", 1f, 1f);
            log.info(String.format("XP S-Mods: %s bought slot %d for %.0f XP (%.0f XP left)",
                    member.getShipName(), bought + 1, cost, XPData.getXP(id)));
        }

        private void buildIn(String hullmodId) {
            if (isModule() || market == null) return;
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(hullmodId);
            if (spec == null || !buildInCandidates(variant).contains(spec)) return;
            if (Misc.getCurrSpecialMods(variant) >= sModLimit()) return;
            float cost = buildInCost(spec, variant.getHullSize());
            if (credits().get() < cost) return;

            credits().subtract(cost);
            variant.addPermaMod(hullmodId, true);
            member.updateStats();
            refreshVariant();
            Global.getSoundPlayer().playUISound("ui_acquired_hullmod", 1f, 1f);
            log.info(String.format("XP S-Mods: %s built in %s for %.0f credits",
                    member.getShipName(), spec.getDisplayName(), cost));
        }

        private void remove(String hullmodId) {
            if (!XPSModsSettings.allowRemoval || isModule() || market == null) return;
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(hullmodId);
            if (spec == null || !currentSMods(variant).contains(spec)) return;
            float fee = removalCost(spec, variant.getHullSize());
            if (credits().get() < fee) return;

            credits().subtract(fee);
            variant.removePermaMod(hullmodId);
            // Remove it from the ship entirely, not just demote it to a normal hullmod.
            if (variant.hasHullMod(hullmodId)) variant.removeMod(hullmodId);
            member.updateStats();
            refreshVariant();
            Global.getSoundPlayer().playUISound("ui_objective_constructed", 1f, 1f);
            log.info(String.format("XP S-Mods: %s removed S-mod %s for %.0f credits",
                    member.getShipName(), spec.getDisplayName(), fee));
        }
    }
}
