package xpsmods;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import java.util.ArrayList;
import java.util.List;
import lunalib.backend.ui.refit.RefitButtonAdder;
import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;

/**
 * Where vanilla's refit frame is on screen, so our dialog can sit exactly where vanilla's
 * "Build in" dialog does: over the frame, minus the fleet list on the left, which vanilla dims
 * instead of covering. Coordinates here are screen UI units with a top-left origin.
 */
final class RefitFrame {

    /** Width of the refit fleet list column at the frame's left edge, plus the gap after it. */
    static final float FLEET_LIST_W = 213f;

    private static final Logger log = Global.getLogger(RefitFrame.class);
    private static final float MAX_W = 1250f, MIN_W = 1010f, SCREEN_W_MARGIN = 20f;
    private static final float MAX_H = 850f, MIN_H = 734f, SCREEN_H_MARGIN = 74f;
    /** Vanilla centres the frame in the area above the bottom tab bar, not in the whole screen. */
    private static final float TAB_BAR_H = 33f;
    private static boolean logged = false;

    final float left, top, width, height;

    private RefitFrame(float left, float top, float width, float height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    /** The frame's size from vanilla's rule (same numbers Exiled Sector uses). */
    private static float ruleWidth() {
        return Math.min(MAX_W, Math.max(Global.getSettings().getScreenWidth() - SCREEN_W_MARGIN, MIN_W)) + 2f;
    }

    private static float ruleHeight() {
        return Math.min(MAX_H, Math.max(Global.getSettings().getScreenHeight() - SCREEN_H_MARGIN, MIN_H)) + 2f;
    }

    /** Measured from the refit tab when it's frame-sized, otherwise the size rule, centred above the tab bar. */
    static RefitFrame current() {
        float sw = Global.getSettings().getScreenWidth(), sh = Global.getSettings().getScreenHeight();
        float w = ruleWidth(), h = ruleHeight();
        try {
            Object tab = UiReflect.call(UiReflect.coreUI(Global.getSector().getCampaignUI()), "getCurrentTab");
            if (tab instanceof UIComponentAPI) {
                PositionAPI p = ((UIComponentAPI) tab).getPosition();
                if (Math.abs(p.getWidth() - w) < 8f && Math.abs(p.getHeight() - h) < 8f) {
                    RefitFrame f = new RefitFrame(p.getX(), sh - (p.getY() + p.getHeight()), p.getWidth(), p.getHeight());
                    logOnce("measured from the refit tab", f);
                    return f;
                }
                logOnce(String.format("refit tab is %.0fx%.0f, not frame-sized; using the size rule", p.getWidth(), p.getHeight()), null);
            }
        } catch (Throwable t) {
            logOnce("could not measure (" + t + "); using the size rule", null);
        }
        RefitFrame f = new RefitFrame((sw - w) / 2f, (sh - TAB_BAR_H - h) / 2f, w, h);
        logOnce("from the size rule", f);
        return f;
    }

    private static void logOnce(String how, RefitFrame f) {
        if (logged || !XPSModsSettings.debugLog) return;
        if (f != null) logged = true;
        log.info("XP S-Mods: refit frame " + how
                + (f == null ? "" : String.format(" at (%.0f, %.0f) %.0fx%.0f", f.left, f.top, f.width, f.height)));
    }

    static RefitButtonAdder lunaAdder() {
        for (EveryFrameScript script : Global.getSector().getTransientScripts()) {
            if (script instanceof RefitButtonAdder) return (RefitButtonAdder) script;
        }
        return null;
    }

    // ------------------------------------------------------------------ fleet list dimmer

    /** Adds a dimmer over the fleet list column (removing any stale one first). */
    static void addDimmer(RefitFrame frame, CustomPanelAPI dialogBackground) {
        RefitButtonAdder adder = lunaAdder();
        UIPanelAPI core = adder == null ? null : adder.getCorePanel();
        if (core == null) return;
        removeDimmers(core);
        Dimmer plugin = new Dimmer(adder, dialogBackground);
        CustomPanelAPI dimmer = Global.getSettings().createCustom(FLEET_LIST_W, frame.height, plugin);
        core.addComponent(dimmer).inTL(frame.left, frame.top);
    }

    static void removeDimmers() {
        RefitButtonAdder adder = lunaAdder();
        if (adder != null && adder.getCorePanel() != null) removeDimmers(adder.getCorePanel());
    }

    private static void removeDimmers(UIPanelAPI core) {
        try {
            List<Object> stale = new ArrayList<Object>();
            for (Object child : UiReflect.children(core)) {
                if (child instanceof CustomPanelAPI && ((CustomPanelAPI) child).getPlugin() instanceof Dimmer) stale.add(child);
            }
            for (Object child : stale) core.removeComponent((UIComponentAPI) child);
        } catch (Throwable t) {
            log.warn("XP S-Mods: could not remove the fleet list dimmer", t);
        }
    }

    /**
     * Darkens the fleet list like vanilla's dialog does and swallows clicks on it, so the ship can't
     * change under an open dialog. Goes inert by itself if LunaLib closes the dialog without telling us.
     */
    private static final class Dimmer extends BaseCustomUIPanelPlugin {
        private final RefitButtonAdder adder;
        private final CustomPanelAPI dialog;
        private PositionAPI pos;

        Dimmer(RefitButtonAdder adder, CustomPanelAPI dialog) {
            this.adder = adder;
            this.dialog = dialog;
        }

        private boolean active() {
            return adder.getActivePanel() == dialog;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null || !active()) return;
            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(0f, 0f, 0f, 0.65f * alphaMult);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(pos.getX(), pos.getY());
            GL11.glVertex2f(pos.getX() + pos.getWidth(), pos.getY());
            GL11.glVertex2f(pos.getX() + pos.getWidth(), pos.getY() + pos.getHeight());
            GL11.glVertex2f(pos.getX(), pos.getY() + pos.getHeight());
            GL11.glEnd();
            GL11.glPopMatrix();
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (pos == null || !active()) return;
            for (InputEventAPI e : events) {
                if (!e.isConsumed() && e.isMouseEvent() && pos.containsEvent(e)) e.consume();
            }
        }
    }
}
