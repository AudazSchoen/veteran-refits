package xpsmods;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import lunalib.lunaRefit.LunaRefitManager;
import org.apache.log4j.Logger;

public class XPSModsPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(XPSModsPlugin.class);

    @Override
    public void onApplicationLoad() throws Exception {
        XPSModsSettings.load();
        Global.getSettings().loadTexture(UpgradeIconOverlay.SPRITE);
        LunaRefitManager.addRefitButton(new XPSModsRefitButton());
        log.info("XP S-Mods: application loaded");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        // Transient: not written to the save, so it is re-added on every load.
        Global.getSector().addTransientListener(new XPBattleListener());
        SlotBonus.ensureHullmodOnPlayerFleet();
        // Transient: not saved, re-added on every load.
        Global.getSector().addTransientScript(new RefitButtonHook());
        log.info("XP S-Mods: game loaded (newGame=" + newGame + "), battle listener registered");
    }
}
