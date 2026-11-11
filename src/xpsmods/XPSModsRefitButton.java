package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import lunalib.lunaRefit.BaseRefitButton;
import org.apache.log4j.Logger;

/** LunaLib refit panel: shows a ship's XP and S-mod slots, and buys extra slots with XP. */
public class XPSModsRefitButton extends BaseRefitButton {

    private static final Logger log = Global.getLogger(XPSModsRefitButton.class);
    private static final float WIDTH = 520f;
    private static final float HEIGHT = 300f;
    private static final String BUY = "xpsmods_buy_slot";
    private static final String CLOSE = "xpsmods_close";

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
        new Content(backgroundPanel, member, variant).build();
    }

    /** XP cost of the next slot: baseSlotXP * slotCostMult ^ slotsAlreadyBought. */
    static float nextSlotCost(int bought) {
        return XPSModsSettings.baseSlotXP * (float) Math.pow(XPSModsSettings.slotCostMult, bought);
    }

    /** One open panel. Rebuilds its contents after each purchase. */
    private class Content extends BaseCustomUIPanelPlugin {
        private final CustomPanelAPI background;
        private final FleetMemberAPI member;
        private final ShipVariantAPI variant;
        private CustomPanelAPI inner;

        Content(CustomPanelAPI background, FleetMemberAPI member, ShipVariantAPI variant) {
            this.background = background;
            this.member = member;
            this.variant = variant;
        }

        /** A module of a multi-section ship is selected rather than the main hull. */
        private boolean isModule() {
            return !variant.getHullSpec().getBaseHullId().equals(member.getHullSpec().getBaseHullId());
        }

        void build() {
            if (inner != null) background.removeComponent(inner);
            float pad = 10f;
            float w = WIDTH - 2 * pad;
            inner = background.createCustomPanel(WIDTH, HEIGHT, this);
            TooltipMakerAPI t = inner.createUIElement(w, HEIGHT - 2 * pad, false);
            Color hl = Misc.getHighlightColor();
            Color bad = Misc.getNegativeHighlightColor();

            String id = member.getId();
            float xp = XPData.getXP(id);
            int bought = XPData.getBoughtSlots(id);
            int bonus = XPData.getSlotBonus(id);
            int total = Math.round(member.getStats().getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD)
                    .computeEffective(Misc.MAX_PERMA_MODS));
            // The stat already includes our bonus when the hidden hullmod is on the ship.
            int vanilla = total - (member.getVariant().hasHullMod(XPSlotsHullmod.ID) ? bonus : 0);
            int max = XPSModsSettings.maxExtraSlots;
            float cost = nextSlotCost(bought);

            t.addSectionHeading("Ship upgrades: " + member.getShipName() + ", "
                    + member.getHullSpec().getHullNameWithDashClass(), Alignment.MID, 0f);
            t.addPara("Ship XP: %s", pad, hl, fmt(xp));
            t.addPara("S-mod limit: %s (vanilla %s + bought %s)", pad, hl,
                    String.valueOf(vanilla + bonus), String.valueOf(vanilla), String.valueOf(bonus));
            t.addPara("Extra slots bought: %s of %s", pad, hl, String.valueOf(bought), String.valueOf(max));

            String reason = null;
            if (isModule()) {
                reason = "Select the main hull to buy S-mod slots.";
            } else if (bought >= max) {
                reason = "This ship has reached the maximum of " + max + " extra slots.";
            } else {
                t.addPara("Next slot costs %s XP.", pad, hl, fmt(cost));
                if (xp < cost) reason = "Not enough XP: needs " + fmt(cost - xp) + " more.";
            }

            ButtonAPI buy = t.addButton("Buy S-mod slot", BUY, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, com.fs.starfarer.api.ui.CutStyle.ALL, 200f, 30f, pad * 2);
            buy.setEnabled(reason == null);
            if (reason != null) t.addPara(reason, pad, bad);

            t.addButton("Close", CLOSE, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, com.fs.starfarer.api.ui.CutStyle.ALL, 120f, 25f, pad * 2);

            inner.addUIElement(t).inTL(pad, pad);
            background.addComponent(inner).inTL(0f, 0f);
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (CLOSE.equals(buttonId)) {
                closePanel();
            } else if (BUY.equals(buttonId)) {
                try {
                    buySlot();
                } catch (Throwable t) {
                    log.error("XP S-Mods: failed to buy a slot", t);
                }
                build();
            }
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
    }

    private static String fmt(float value) {
        return Misc.getWithDGS(Math.round(value));
    }
}
