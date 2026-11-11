package xpsmods;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.log4j.Logger;

/**
 * Once a month (game time), notes which ships still exist and drops stored data for ships that haven't
 * been seen for pruneAfterMonths. A ship counts as seen in the player fleet or in any market's storage,
 * across every location (so abandoned stations count too). Waiting rather than deleting at once keeps
 * data for ships that leave and come back with the same id: sold and bought back, stored, or lent.
 * Registered as a transient script, so it's not written to the save.
 */
public class ShipDataPruner implements EveryFrameScript {

    private static final Logger log = Global.getLogger(ShipDataPruner.class);
    private static final float CHECK_EVERY_DAYS = 30f;

    /** Not saved: the first check after loading a game happens right away. */
    private long lastCheck = Long.MIN_VALUE;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        CampaignClockAPI clock = Global.getSector().getClock();
        if (lastCheck != Long.MIN_VALUE && clock.getElapsedDaysSince(lastCheck) < CHECK_EVERY_DAYS) return;
        lastCheck = clock.getTimestamp();
        try {
            check(clock);
        } catch (Throwable t) {
            log.error("XP S-Mods: ship data cleanup failed", t);
        }
    }

    private static void check(CampaignClockAPI clock) {
        long now = clock.getTimestamp();
        Set<String> seen = existingShipIds();
        for (String id : seen) XPData.markSeen(id, now);

        if (XPSModsSettings.pruneAfterMonths <= 0) return;
        // The clock has no "months since"; convert with the game's own days per month.
        float daysPerMonth = clock.convertToDays(1f) / clock.convertToMonths(1f);
        float limitDays = XPSModsSettings.pruneAfterMonths * daysPerMonth;
        int removed = 0;
        for (String id : XPData.allIds()) {
            if (seen.contains(id)) continue;
            Long last = XPData.getLastSeen(id);
            if (last == null) {
                // Data from before this feature: start its clock now instead of removing it.
                XPData.markSeen(id, now);
            } else if (clock.getElapsedDaysSince(last) > limitDays) {
                log.info(String.format("XP S-Mods: removed data for ship %s (XP %.0f, slots %d), not seen for %.1f months",
                        id, XPData.getXP(id), XPData.getBoughtSlots(id), clock.getElapsedDaysSince(last) / daysPerMonth));
                XPData.removeAll(id);
                removed++;
            }
        }
        if (removed > 0 || XPSModsSettings.debugLog) {
            log.info("XP S-Mods: ship data check: " + seen.size() + " ships seen, " + removed + " entries removed");
        }
    }

    /** Ids of ships in the player fleet and in every market storage, in every location. */
    static Set<String> existingShipIds() {
        Set<String> ids = new HashSet<String>();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player != null) {
            for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) ids.add(m.getId());
        }
        Set<MarketAPI> markets = Collections.newSetFromMap(new IdentityHashMap<MarketAPI, Boolean>());
        markets.addAll(Global.getSector().getEconomy().getMarketsCopy());
        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (SectorEntityToken entity : loc.getAllEntities()) {
                if (entity.getMarket() != null) markets.add(entity.getMarket());
            }
        }
        for (MarketAPI market : markets) {
            CargoAPI storage = Misc.getStorageCargo(market);
            if (storage == null || storage.getMothballedShips() == null) continue;
            for (FleetMemberAPI m : storage.getMothballedShips().getMembersListCopy()) ids.add(m.getId());
        }
        return ids;
    }
}
