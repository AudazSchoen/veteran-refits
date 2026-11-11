package xpsmods;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import lunalib.backend.ui.refit.RefitButtonAdder;
import lunalib.backend.ui.refit.RefitPanelBackgroundPlugin;
import lunalib.lunaRefit.BaseRefitButton;
import lunalib.lunaRefit.LunaRefitManager;
import org.apache.log4j.Logger;

/**
 * While the refit screen is open, keeps a "Ship upgrades" button (with hotkey) under the vanilla
 * "Build in" button. Pressing it opens the {@link XPSModsRefitButton} LunaLib panel.
 *
 * Path into the game's UI (same as Progressive S-Mods and Exiled Sector):
 * coreUI.getCurrentTab().getRefitPanel().getModDisplay().getMods().getPerm()
 */
public class RefitButtonHook implements EveryFrameScript {

    private static final Logger log = Global.getLogger(RefitButtonHook.class);
    private static final String BUTTON_ID = "xpsmods_refit_button";
    private static final float GAP_BELOW_BUILD_IN = 3f;
    private static final float X_ALIGN_OFFSET = -5f;

    /** Set on the first failure; the hook then stays off until the game restarts. */
    private static boolean failed = false;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (failed || Global.getSector() == null) return;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null || ui.getCurrentCoreTab() != CoreUITabId.REFIT) return;

        try {
            Object core = UiReflect.coreUI(ui);
            Object tab = UiReflect.call(core, "getCurrentTab");
            Object refitPanel = UiReflect.call(tab, "getRefitPanel");
            Object modDisplay = UiReflect.call(refitPanel, "getModDisplay");
            Object mods = UiReflect.call(modDisplay, "getMods");
            if (!(mods instanceof UIPanelAPI)) return;
            Object perm = UiReflect.call(mods, "getPerm");
            if (!(perm instanceof ButtonAPI)) return;

            // The game rebuilds this panel (for example when switching ships), so re-attach when missing.
            for (Object child : UiReflect.children(mods)) {
                if (child instanceof CustomPanelAPI && ((CustomPanelAPI) child).getPlugin() instanceof ColumnButtonPlugin) {
                    return;
                }
            }
            attach((UIPanelAPI) mods, (ButtonAPI) perm);
        } catch (Throwable t) {
            failed = true;
            log.error("XP S-Mods: could not add the button under 'Build in' in the refit screen; "
                    + "it stays off until the game restarts (the LunaLib refit list button still works)", t);
        }
    }

    private static void attach(UIPanelAPI mods, ButtonAPI perm) {
        float width = perm.getPosition().getWidth();
        float height = perm.getPosition().getHeight();

        ColumnButtonPlugin plugin = new ColumnButtonPlugin();
        CustomPanelAPI panel = Global.getSettings().createCustom(width, height, plugin);
        TooltipMakerAPI element = panel.createUIElement(width, height, false);
        element.setButtonFontOrbitron20();
        ButtonAPI button = element.addButton("Ship upgrades", BUTTON_ID,
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, width, height, 0f);
        int key = XPSModsSettings.refitHotkey;
        if (key > 0) button.setShortcut(key, false);
        panel.addUIElement(element).inTL(0f, 0f);

        mods.addComponent(panel);
        panel.getPosition().belowMid(perm, GAP_BELOW_BUILD_IN).setXAlignOffset(X_ALIGN_OFFSET);
    }

    /** Opens our LunaLib panel the same way LunaLib's own refit button list does (RefitButtonAdder.kt). */
    static void openPanel() {
        BaseRefitButton button = LunaRefitManager.getFirstButtonOfClass(XPSModsRefitButton.class);
        RefitButtonAdder adder = null;
        for (EveryFrameScript script : Global.getSector().getTransientScripts()) {
            if (script instanceof RefitButtonAdder) {
                adder = (RefitButtonAdder) script;
                break;
            }
        }
        if (button == null || adder == null) {
            log.warn("XP S-Mods: LunaLib refit manager not found, can't open the panel");
            return;
        }
        UIPanelAPI corePanel = adder.getCorePanel();
        if (corePanel == null || adder.getActivePanel() != null) return; // not ready yet, or a panel is already open

        if (!button.isClickable(adder.getMember(), adder.getVariant(), adder.getMarket())) {
            Global.getSoundPlayer().playUISound("ui_button_disabled_pressed", 1f, 1f);
            return;
        }
        Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);

        float width = button.getPanelWidth(adder.getMember(), adder.getVariant());
        float height = button.getPanelHeight(adder.getMember(), adder.getVariant());
        RefitPanelBackgroundPlugin background = new RefitPanelBackgroundPlugin(corePanel, true);
        CustomPanelAPI panel = Global.getSettings().createCustom(width, height, background);
        adder.setActivePanel(panel);
        adder.setActivePanelButton(button);
        panel.getPosition().inTL(Global.getSettings().getScreenWidth() / 2f - width / 2f,
                Global.getSettings().getScreenHeight() / 2f - height / 2f);
        background.setPanel(panel);
        corePanel.addComponent(panel);
        button.initPanel(panel, adder.getMember(), adder.getVariant(), adder.getMarket());
    }

    private static class ColumnButtonPlugin extends BaseCustomUIPanelPlugin {
        @Override
        public void buttonPressed(Object buttonId) {
            if (!BUTTON_ID.equals(buttonId)) return;
            try {
                openPanel();
            } catch (Throwable t) {
                log.error("XP S-Mods: failed to open the refit panel", t);
            }
        }
    }
}
