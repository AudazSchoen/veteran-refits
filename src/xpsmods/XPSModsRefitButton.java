package xpsmods;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import lunalib.lunaRefit.BaseRefitButton;

/**
 * LunaLib refit button: opens {@link BuildInDialog} for the selected ship. Also reachable from the
 * "Ship upgrades" button under vanilla "Build in" (see {@link RefitButtonHook}).
 */
public class XPSModsRefitButton extends BaseRefitButton {

    @Override
    public String getButtonName(FleetMemberAPI member, ShipVariantAPI variant) {
        return "Ship upgrades (Veteran Refits)";
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
        return RefitFrame.current().width - RefitFrame.FLEET_LIST_W;
    }

    @Override
    public float getPanelHeight(FleetMemberAPI member, ShipVariantAPI variant) {
        return RefitFrame.current().height;
    }

    @Override
    public void initPanel(CustomPanelAPI backgroundPanel, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        // Sit exactly where vanilla's dialog does: over the frame, right of the (dimmed) fleet list.
        RefitFrame frame = RefitFrame.current();
        backgroundPanel.getPosition().inTL(frame.left + RefitFrame.FLEET_LIST_W, frame.top);
        RefitFrame.addDimmer(frame, backgroundPanel);
        new BuildInDialog(this, backgroundPanel, member, variant, market).build();
    }

    @Override
    public void onPanelClose(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        RefitFrame.removeDimmers();
    }
}
