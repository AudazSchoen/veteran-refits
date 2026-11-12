package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CombatDamageData;
import com.fs.starfarer.api.campaign.CombatDamageData.DamageToFleetMember;
import com.fs.starfarer.api.campaign.CombatDamageData.DealtByFleetMember;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

/** Awards XP to the player's ships after every engagement. Registered as a transient listener. */
public class XPBattleListener extends BaseCampaignEventListener {

    private static final Logger log = Global.getLogger(XPBattleListener.class);

    /**
     * Player ships disabled or destroyed during this battle's engagements (id -> name). Whether they're
     * really lost is only known when the battle ends, after the player has (or hasn't) recovered them.
     */
    private final Map<String, String> casualties = new LinkedHashMap<String, String>();

    /** The last interaction dialog shown; the post-battle dialog, if getCurrentInteractionDialog() is null. */
    private InteractionDialogAPI lastDialog;

    /**
     * Ids of the player's fleet members when the last interaction dialog opened, so before the battle.
     * Ships that mods spawn mid-combat on the player's side (Secrets of the Frontier's mimics, Dotty,
     * daydream ships and so on) are never in it, so they earn no XP and don't count toward the average.
     */
    private final Set<String> fleetAtDialogStart = new HashSet<String>();

    public XPBattleListener() {
        super(false);
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        lastDialog = dialog;
        fleetAtDialogStart.clear();
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet != null) {
            for (FleetMemberAPI m : playerFleet.getFleetData().getMembersListCopy()) fleetAtDialogStart.add(m.getId());
        }
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
                    if (XPSModsSettings.debugLog) log.info("Veteran Refits: " + e.getValue() + " was recovered, data kept");
                } else {
                    XPData.removeAll(e.getKey());
                    log.info("Veteran Refits: " + e.getValue() + " (id " + e.getKey() + ") was lost in battle, data removed");
                }
            }
        } catch (Exception ex) {
            log.error("Veteran Refits: failed to check battle losses", ex);
        } finally {
            casualties.clear();
        }
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        try {
            awardXP(result);
        } catch (Exception e) {
            log.error("Veteran Refits: failed to award XP", e);
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
            if (XPSModsSettings.debugLog) log.info("Veteran Refits: engagement had no player side, no XP awarded");
            return;
        }

        // Only the player's own ships earn XP: not allies, and not temporary ships other mods spawn during
        // combat. Disabled ships may already have been taken out of the fleet, so ships in the fleet when
        // the encounter dialog opened, and ships we hold data for, also count as ours.
        List<FleetMemberAPI> fleetMembers = playerFleet.getFleetData().getMembersListCopy();
        Set<String> readyBefore = new HashSet<String>();
        for (FleetMemberAPI m : fleetMembers) if (XPData.canBuySlot(m.getId())) readyBefore.add(m.getId());
        Set<String> playerIds = new HashSet<String>();
        for (FleetMemberAPI m : fleetMembers) playerIds.add(m.getId());
        playerIds.addAll(XPData.allIds());
        playerIds.addAll(fleetAtDialogStart);

        // Deployed ships, including ones disabled or destroyed in this engagement.
        Map<String, FleetMemberAPI> deployed = new LinkedHashMap<String, FleetMemberAPI>();
        addOwn(deployed, player.getDeployed(), playerIds);
        addOwn(deployed, player.getRetreated(), playerIds);
        Map<String, FleetMemberAPI> knockedOut = new LinkedHashMap<String, FleetMemberAPI>();
        addOwn(knockedOut, player.getDisabled(), playerIds);
        addOwn(knockedOut, player.getDestroyed(), playerIds);
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
                log.info("Veteran Refits: no combat damage data (auto-resolve?), no damage XP, kill XP split evenly");
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

        // First work out what each deployed ship earned, then the reserve share from those real numbers.
        // Ships knocked out this engagement are left out of the average: they often end up lost, and
        // with knockedOutEarnXP off they earn nothing anyway.
        float totalDeployedXP = 0f;
        int survivors = 0;
        Map<String, float[]> earnedBy = new LinkedHashMap<String, float[]>(); // id -> {damageXP, killXP}
        for (FleetMemberAPI m : deployed.values()) {
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
            earnedBy.put(m.getId(), new float[] {damageXP, killXP});
            if (!knockedOut.containsKey(m.getId())) {
                totalDeployedXP += damageXP + killXP;
                survivors++;
            }
        }

        // Undeployed ships (reserve and civilian) get a share of the deployed average. Mothballed get nothing.
        float reserveXP = survivors > 0
                ? (totalDeployedXP / survivors) * XPSModsSettings.reserveShareOfAverage : 0f;

        // Deployed ships never earn less than the reserve ships did.
        Map<FleetMemberAPI, Float> deployedEarned = new LinkedHashMap<FleetMemberAPI, Float>();
        for (FleetMemberAPI m : deployed.values()) {
            float[] parts = earnedBy.get(m.getId());
            boolean out = knockedOut.containsKey(m.getId());
            String detail = String.format("damage %.0f, kills %.0f", parts[0], parts[1]);
            float earned;
            if (out && !XPSModsSettings.knockedOutEarnXP) {
                earned = 0f;
                detail += ", knocked out, no XP";
            } else {
                earned = Math.max(parts[0] + parts[1], reserveXP);
                if (parts[0] + parts[1] < reserveXP) detail += ", raised to reserve share";
                if (out) detail += ", knocked out";
            }
            deployedEarned.put(m, earned);
            float newTotal = earned > 0f ? XPData.addXP(m.getId(), earned) : XPData.getXP(m.getId());
            debug(m, earned, newTotal, detail);
        }

        int reserveCount = 0;
        if (reserveXP > 0f) {
            for (FleetMemberAPI m : fleetMembers) {
                if (m.isFighterWing() || m.isMothballed() || deployed.containsKey(m.getId())) continue;
                float newTotal = XPData.addXP(m.getId(), reserveXP);
                debug(m, reserveXP, newTotal, "reserve");
                reserveCount++;
            }
        }

        if (XPSModsSettings.battleReport) {
            try {
                report(deployedEarned, knockedOut.keySet(), reserveXP, reserveCount, playerFleet, readyBefore);
            } catch (Exception e) {
                log.error("Veteran Refits: failed to write the battle report", e);
            }
        }
    }

    /** XP earned and ships with an upgrade available, written into the post-battle dialog text. */
    private void report(Map<FleetMemberAPI, Float> deployedEarned, Set<String> knockedOut, float reserveXP, int reserveCount,
                        CampaignFleetAPI playerFleet, Set<String> readyBefore) {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (dialog == null) dialog = lastDialog;
        if (dialog == null || dialog.getTextPanel() == null) return;
        TextPanelAPI text = dialog.getTextPanel();
        Color hl = Misc.getHighlightColor();
        Color good = Misc.getPositiveHighlightColor();

        text.setFontSmallInsignia();
        text.addPara("Ship XP earned:", Misc.getTextColor());

        // Highest earners first.
        List<Map.Entry<FleetMemberAPI, Float>> rows = new ArrayList<Map.Entry<FleetMemberAPI, Float>>(deployedEarned.entrySet());
        Collections.sort(rows, new Comparator<Map.Entry<FleetMemberAPI, Float>>() {
            @Override
            public int compare(Map.Entry<FleetMemberAPI, Float> a, Map.Entry<FleetMemberAPI, Float> b) {
                return Float.compare(b.getValue(), a.getValue());
            }
        });
        for (Map.Entry<FleetMemberAPI, Float> row : rows) {
            FleetMemberAPI m = row.getKey();
            String gained = "+" + Misc.getWithDGS(Math.round(row.getValue())) + " XP";
            if (knockedOut.contains(m.getId())) gained += " (disabled)";
            String total = Misc.getWithDGS(Math.round(XPData.getXP(m.getId())));
            text.addPara("    %s, " + m.getHullSpec().getHullNameWithDashClass() + ": %s (%s total)",
                    Misc.getTextColor(), hl, m.getShipName(), gained, total);
        }
        if (reserveCount > 0) {
            text.addPara("    Reserve ships: %s each (%s ships)", Misc.getTextColor(), hl,
                    "+" + Misc.getWithDGS(Math.round(reserveXP)) + " XP", String.valueOf(reserveCount));
        }

        // Every ship that can buy a slot now; the ones that just got there first, marked new.
        List<String> fresh = new ArrayList<String>(), older = new ArrayList<String>();
        for (FleetMemberAPI m : playerFleet.getFleetData().getMembersListCopy()) {
            if (!XPData.canBuySlot(m.getId())) continue;
            if (readyBefore.contains(m.getId())) older.add(m.getShipName());
            else fresh.add(m.getShipName() + " (new)");
        }
        if (!fresh.isEmpty() || !older.isEmpty()) {
            List<String> names = new ArrayList<String>(fresh);
            names.addAll(older);
            String hotkey = XPSModsSettings.refitHotkey > 0
                    ? " [" + Keyboard.getKeyName(XPSModsSettings.refitHotkey) + "]" : "";
            StringBuilder format = new StringBuilder("Upgrade available: ");
            for (int i = 0; i < names.size(); i++) format.append(i == 0 ? "%s" : ", %s");
            format.append(". Buy S-mod slots with Ship upgrades").append(hotkey).append(" in the refit screen.");
            text.addPara(format.toString(), Misc.getTextColor(), good, names.toArray(new String[0]));
        }
        text.setFontInsignia();
    }

    private void addOwn(Map<String, FleetMemberAPI> into, List<FleetMemberAPI> members, Set<String> playerIds) {
        for (FleetMemberAPI m : members) {
            if (m == null || m.isFighterWing()) continue;
            if (playerIds.contains(m.getId())) {
                into.put(m.getId(), m);
            } else if (XPSModsSettings.debugLog) {
                log.info("Veteran Refits: skipped " + m.getShipName() + ", " + m.getHullSpec().getHullNameWithDashClass()
                        + " (id " + m.getId() + "), not a player ship (ally, or a temporary ship spawned in combat)");
            }
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

    private static void debug(FleetMemberAPI m, float earned, float total, String detail) {
        if (!XPSModsSettings.debugLog) return;
        String totalText = total >= 0f ? String.format("%.0f total", total) : "-";
        log.info(String.format("Veteran Refits: %s, %s (id %s) +%.0f XP -> %s [%s]",
                m.getShipName(), m.getHullSpec().getHullNameWithDashClass(), m.getId(), earned, totalText, detail));
    }
}
