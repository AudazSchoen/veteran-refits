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
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import lunalib.backend.ui.refit.RefitButtonAdder;
import lunalib.lunaRefit.BaseRefitButton;
import lunalib.lunaRefit.LunaRefitManager;
import java.util.Set;
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
    /** customData of MagicLib's "Paintjob" button (MagicPaintjobRefitPanelCreator). */
    private static final String MAGICLIB_PAINTJOB_ID = "PAINTJOB_BUTTON";

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
        if (Global.getSector() == null) return;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null) return;
        if (ui.getCurrentCoreTab() != CoreUITabId.REFIT) return;

        Object tab;
        try {
            tab = UiReflect.call(UiReflect.coreUI(ui), "getCurrentTab");
        } catch (Throwable t) {
            failed = true;
            UpgradeIconOverlay.failed = true;
            log.error("Veteran Refits: could not read the refit screen; the button under 'Build in' and the "
                    + "upgrade icons stay off until the game restarts", t);
            return;
        }

        if (!UpgradeIconOverlay.failed && tab instanceof UIPanelAPI) {
            try {
                UpgradeIconOverlay.ensureAttached((UIPanelAPI) tab);
            } catch (Throwable t) {
                UpgradeIconOverlay.failed = true;
                log.error("Veteran Refits: could not add upgrade icons to the refit ship list; "
                        + "they stay off until the game restarts", t);
            }
        }

        if (failed) return;
        try {
            Object refitPanel = UiReflect.call(tab, "getRefitPanel");
            Object modDisplay = UiReflect.call(refitPanel, "getModDisplay");
            if (modDisplay == null) return;
            ensureButton(UiReflect.call(modDisplay, "getMods"), false);

            // The "add hullmod" picker (ModPickerDialogV3) is a child of the core UI with its own copy of
            // the mod display, and so its own "Build in" button (same approach as Progressive S-Mods).
            for (Object child : UiReflect.children(UiReflect.coreUI(ui))) {
                if (!UiReflect.isPanel(child)) continue;
                for (Object sub : UiReflect.children(child)) {
                    if (sub != modDisplay && sub != null && sub.getClass() == modDisplay.getClass()) {
                        ensureButton(UiReflect.call(sub, "getMods"), true);
                    }
                }
            }
        } catch (Throwable t) {
            failed = true;
            log.error("Veteran Refits: could not add the button under 'Build in' in the refit screen; "
                    + "it stays off until the game restarts (the LunaLib refit list button still works)", t);
        }
    }

    /**
     * Adds our button under this mods panel's "Build in" button, unless it's already there.
     * {@code inPicker}: this is the add-hullmod picker's copy of the panel.
     */
    private static void ensureButton(Object mods, boolean inPicker) throws Throwable {
        if (!(mods instanceof UIPanelAPI)) return;
        Object perm = UiReflect.call(mods, "getPerm");
        if (!(perm instanceof ButtonAPI)) return;
        // The game rebuilds these panels (for example when switching ships), so re-attach when missing.
        CustomPanelAPI ours = null;
        for (Object child : UiReflect.children(mods)) {
            if (child instanceof CustomPanelAPI && ((CustomPanelAPI) child).getPlugin() instanceof ColumnButtonPlugin) {
                ours = (CustomPanelAPI) child;
                break;
            }
        }
        if (ours == null) ours = attach((UIPanelAPI) mods, (ButtonAPI) perm, inPicker);
        makeRoomBelow((UIPanelAPI) mods, ours);
    }

    /**
     * MagicLib puts its "Paintjob" button below whatever was last in this panel. If it got there
     * before us, it sits right where our button goes, so move it below ours.
     */
    private static void makeRoomBelow(UIPanelAPI mods, CustomPanelAPI ours) throws Throwable {
        PositionAPI o = ours.getPosition();
        for (Object child : UiReflect.children(mods)) {
            if (!(child instanceof ButtonAPI)) continue;
            ButtonAPI b = (ButtonAPI) child;
            if (!MAGICLIB_PAINTJOB_ID.equals(b.getCustomData())) continue;
            PositionAPI p = b.getPosition();
            // Its top edge is above our bottom edge: they overlap.
            if (p.getY() + p.getHeight() > o.getY() - 1f) p.belowLeft(ours, GAP_BELOW_BUILD_IN);
        }
    }

    private static CustomPanelAPI attach(UIPanelAPI mods, ButtonAPI perm, boolean inPicker) {
        float width = perm.getPosition().getWidth();
        float height = perm.getPosition().getHeight();

        ColumnButtonPlugin plugin = new ColumnButtonPlugin(inPicker);
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
        return panel;
    }

    /**
     * Tells any open "add hullmod" picker about S-mod changes made by our dialog. The picker
     * (ModPickerDialogV3) snapshots the variant's S-mods and enhanced built-ins when it opens, and on
     * closing reverts the variant to that snapshot: S-mods not in it are stripped, and the enhanced
     * built-ins are reset to it. Without this, S-mods paid for while the picker is open vanish again.
     * Its getOriginalPermaMods()/getOriginalSModBuiltIns() return the live snapshot sets.
     */
    @SuppressWarnings("unchecked")
    static void syncOpenPickers(Set<String> built, Set<String> removed, Set<String> enhanced, Set<String> unenhanced) {
        if (built.isEmpty() && removed.isEmpty() && enhanced.isEmpty() && unenhanced.isEmpty()) return;
        try {
            CampaignUIAPI ui = Global.getSector().getCampaignUI();
            if (ui == null || ui.getCurrentCoreTab() != CoreUITabId.REFIT) return;
            for (Object child : UiReflect.children(UiReflect.coreUI(ui))) {
                if (child == null || !UiReflect.hasMethod(child, "getOriginalPermaMods", 0)
                        || !UiReflect.hasMethod(child, "getOriginalSModBuiltIns", 0)) continue;
                Set<String> perma = (Set<String>) UiReflect.call(child, "getOriginalPermaMods");
                Set<String> builtIns = (Set<String>) UiReflect.call(child, "getOriginalSModBuiltIns");
                if (perma != null) {
                    perma.removeAll(removed);
                    perma.addAll(built);
                }
                if (builtIns != null) {
                    builtIns.removeAll(unenhanced);
                    builtIns.addAll(enhanced);
                }
                if (UiReflect.hasMethod(child, "syncUIWithVariant", 0)) UiReflect.call(child, "syncUIWithVariant");
                if (XPSModsSettings.debugLog) log.info("Veteran Refits: updated the open hullmod picker's S-mod snapshot");
            }
        } catch (Throwable t) {
            log.error("Veteran Refits: could not update the open hullmod picker; S-mods changed while it was "
                    + "open may be reverted when it closes", t);
        }
    }

    /**
     * Opens our LunaLib panel the same way LunaLib's own refit button list does (RefitButtonAdder.kt),
     * but with our {@link DialogBackground}, which leaves out the screen dim when {@code overPicker}.
     */
    static void openPanel(boolean overPicker) {
        BaseRefitButton button = LunaRefitManager.getFirstButtonOfClass(XPSModsRefitButton.class);
        RefitButtonAdder adder = RefitFrame.lunaAdder();
        if (button == null || adder == null) {
            log.warn("Veteran Refits: LunaLib refit manager not found, can't open the panel");
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
        DialogBackground background = new DialogBackground(overPicker);
        CustomPanelAPI panel = Global.getSettings().createCustom(width, height, background);
        adder.setActivePanel(panel);
        adder.setActivePanelButton(button);
        panel.getPosition().inTL(Global.getSettings().getScreenWidth() / 2f - width / 2f,
                Global.getSettings().getScreenHeight() / 2f - height / 2f);
        background.panel = panel;
        corePanel.addComponent(panel);
        button.initPanel(panel, adder.getMember(), adder.getVariant(), adder.getMarket());
    }

    private static class ColumnButtonPlugin extends BaseCustomUIPanelPlugin {
        private final boolean inPicker;

        ColumnButtonPlugin(boolean inPicker) {
            this.inPicker = inPicker;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (!BUTTON_ID.equals(buttonId)) return;
            try {
                openPanel(inPicker);
            } catch (Throwable t) {
                log.error("Veteran Refits: failed to open the refit panel", t);
            }
        }
    }
}
