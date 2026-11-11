package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CombatDamageData;
import com.fs.starfarer.api.campaign.CombatDamageData.DamageToFleetMember;
import com.fs.starfarer.api.campaign.CombatDamageData.DealtByFleetMember;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

/** Awards XP to the player's ships after every engagement. Registered as a transient listener. */
public class XPBattleListener extends BaseCampaignEventListener {

    private static final Logger log = Global.getLogger(XPBattleListener.class);

    /**
     * Player ships disabled or destroyed during this battle's engagements (id -> name). Whether they're
     * really lost is only known when the battle ends, after the player has (or hasn't) recovered them.
     */
    private final Map<String, String> casualties = new LinkedHashMap<String, String>();

    public XPBattleListener() {
        super(false);
    }

    /**
     * Fired by vanilla once the battle is over and the player has left the post-battle dialog, so after
     * ship recovery (FleetInteractionDialogPluginImpl: RECOVERY_SELECT, then
     * applyAfterBattleEffectsIfThereWasABattle). Casualties that aren't back in the fleet are gone for
     * good: destroyed, not recovered, or lost on a retreat.
     */
    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (casualties.isEmpty() || battle == null) return;
        if (!battle.isPlayerInvolved() && !battle.isPlayerInvolvedAtStart()) return;
        try {
            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            Set<String> inFleet = new HashSet<String>();
            if (playerFleet != null) {
                for (FleetMemberAPI m : playerFleet.getFleetData().getMembersListCopy()) inFleet.add(m.getId());
            }
            for (Map.Entry<String, String> e : casualties.entrySet()) {
                if (inFleet.contains(e.getKey())) {
                    if (XPSModsSettings.debugLog) log.info("XP S-Mods: " + e.getValue() + " was recovered, data kept");
                } else {
                    XPData.removeAll(e.getKey());
                    log.info("XP S-Mods: " + e.getValue() + " (id " + e.getKey() + ") was lost in battle, data removed");
                }
            }
        } catch (Exception ex) {
            log.error("XP S-Mods: failed to check battle losses", ex);
        } finally {
            casualties.clear();
        }
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        try {
            awardXP(result);
        } catch (Exception e) {
            log.error("XP S-Mods: failed to award XP", e);
        }
    }

    private void awardXP(EngagementResultAPI result) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null || result == null) return;

        EngagementResultForFleetAPI player = result.getWinnerResult();
        EngagementResultForFleetAPI enemy = result.getLoserResult();
        if (player == null || enemy == null) return;
        if (!isPlayerSide(player)) {
            EngagementResultForFleetAPI tmp = player;
            player = enemy;
            enemy = tmp;
        }
        if (!isPlayerSide(player)) {
            if (XPSModsSettings.debugLog) log.info("XP S-Mods: engagement had no player side, no XP awarded");
            return;
        }

        // Only the player's own ships earn XP, not allies. Disabled ships may already have been taken out
        // of the fleet, so a ship we hold data for also counts as ours (only player ships ever get data).
        List<FleetMemberAPI> fleetMembers = playerFleet.getFleetData().getMembersListCopy();
        Set<String> playerIds = new HashSet<String>();
        for (FleetMemberAPI m : fleetMembers) playerIds.add(m.getId());
        playerIds.addAll(XPData.allIds());

        // Deployed ships, including ones disabled or destroyed in this engagement.
        Map<String, FleetMemberAPI> deployed = new LinkedHashMap<String, FleetMemberAPI>();
        addOwn(deployed, player.getDeployed(), playerIds, playerFleet);
        addOwn(deployed, player.getRetreated(), playerIds, playerFleet);
        Map<String, FleetMemberAPI> knockedOut = new LinkedHashMap<String, FleetMemberAPI>();
        addOwn(knockedOut, player.getDisabled(), playerIds, playerFleet);
        addOwn(knockedOut, player.getDestroyed(), playerIds, playerFleet);
        deployed.putAll(knockedOut);
        // Lost or not is decided when the battle ends (reportBattleOccurred).
        for (FleetMemberAPI m : knockedOut.values()) casualties.put(m.getId(), m.getShipName());
        if (deployed.isEmpty()) return;

        // Enemy ships knocked out this engagement, for kill XP.
        Set<FleetMemberAPI> enemyKills = new HashSet<FleetMemberAPI>();
        enemyKills.addAll(enemy.getDisabled());
        enemyKills.addAll(enemy.getDestroyed());

        CombatDamageData damageData = result.getLastCombatDamageData();
        // Without damage data (auto-resolve) kill XP can't be split by damage share, so split it evenly.
        float evenKillXP = 0f;
        if (damageData == null) {
            for (FleetMemberAPI kill : enemyKills) {
                if (kill == null || kill.isFighterWing()) continue;
                evenKillXP += XPSModsSettings.killXP * XPSModsSettings.hullSizeMult(kill.getHullSpec().getHullSize());
            }
            evenKillXP /= deployed.size();
            if (XPSModsSettings.debugLog) {
                log.info("XP S-Mods: no combat damage data (auto-resolve?), no damage XP, kill XP split evenly");
            }
        }

        // Total hull damage each killed enemy took from the player's ships, to split kill XP by share.
        Map<FleetMemberAPI, Float> damageTakenByKill = new HashMap<FleetMemberAPI, Float>();
        if (damageData != null) {
            for (FleetMemberAPI m : deployed.values()) {
                DealtByFleetMember dealt = damageData.getDealt().get(m);
                if (dealt == null) continue;
                for (Map.Entry<FleetMemberAPI, DamageToFleetMember> e : dealt.getDamage().entrySet()) {
                    if (!enemyKills.contains(e.getKey())) continue;
                    Float sum = damageTakenByKill.get(e.getKey());
                    damageTakenByKill.put(e.getKey(), (sum != null ? sum : 0f) + e.getValue().hullDamage);
                }
            }
        }

        float totalDeployedXP = 0f;
        for (FleetMemberAPI m : deployed.values()) {
            float base = XPSModsSettings.baseDeployXP * XPSModsSettings.hullSizeMult(m.getHullSpec().getHullSize());
            float damageXP = 0f;
            float killXP = damageData == null ? evenKillXP : 0f;
            DealtByFleetMember dealt = damageData != null ? damageData.getDealt().get(m) : null;
            if (dealt != null) {
                for (Map.Entry<FleetMemberAPI, DamageToFleetMember> e : dealt.getDamage().entrySet()) {
                    float dmg = e.getValue().hullDamage;
                    if (dmg <= 0f) continue;
                    damageXP += dmg * XPSModsSettings.xpPerHullDamage;
                    Float total = damageTakenByKill.get(e.getKey());
                    if (total != null && total > 0f) {
                        killXP += XPSModsSettings.killXP
                                * XPSModsSettings.hullSizeMult(e.getKey().getHullSpec().getHullSize())
                                * (dmg / total);
                    }
                }
            }
            float earned = base + damageXP + killXP;
            totalDeployedXP += earned;

            float newTotal = XPData.addXP(m.getId(), earned);
            String detail = String.format("base %.0f, damage %.0f, kills %.0f", base, damageXP, killXP);
            if (knockedOut.containsKey(m.getId())) detail += ", knocked out";
            debug(m, earned, newTotal, detail);
        }

        // Undeployed ships (reserve and civilian) get a share of the deployed average. Mothballed get nothing.
        float reserveXP = (totalDeployedXP / deployed.size()) * XPSModsSettings.reserveShareOfAverage;
        if (reserveXP <= 0f) return;
        for (FleetMemberAPI m : fleetMembers) {
            if (m.isFighterWing() || m.isMothballed() || deployed.containsKey(m.getId())) continue;
            float newTotal = XPData.addXP(m.getId(), reserveXP);
            debug(m, reserveXP, newTotal, "reserve");
        }
    }

    private static void addOwn(Map<String, FleetMemberAPI> into, List<FleetMemberAPI> members,
                               Set<String> playerIds, CampaignFleetAPI playerFleet) {
        for (FleetMemberAPI m : members) {
            if (m == null || m.isFighterWing()) continue;
            if (playerIds.contains(m.getId()) || isOwnedBy(m, playerFleet)) into.put(m.getId(), m);
        }
    }

    /**
     * The auto-resolver's results always return false from isPlayer(), so also check the
     * (combined) fleet the same way vanilla's didPlayerWin() does.
     */
    private static boolean isPlayerSide(EngagementResultForFleetAPI side) {
        if (side.isPlayer()) return true;
        CampaignFleetAPI fleet = side.getFleet();
        return fleet != null && Misc.isPlayerOrCombinedContainingPlayer(fleet);
    }

    private static boolean isOwnedBy(FleetMemberAPI m, CampaignFleetAPI fleet) {
        return m.getFleetData() != null && m.getFleetData().getFleet() == fleet;
    }

    private static void debug(FleetMemberAPI m, float earned, float total, String detail) {
        if (!XPSModsSettings.debugLog) return;
        String totalText = total >= 0f ? String.format("%.0f total", total) : "-";
        log.info(String.format("XP S-Mods: %s, %s (id %s) +%.0f XP -> %s [%s]",
                m.getShipName(), m.getHullSpec().getHullNameWithDashClass(), m.getId(), earned, totalText, detail));
    }
}
