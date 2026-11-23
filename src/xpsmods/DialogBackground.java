package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.List;
import lunalib.backend.ui.refit.RefitButtonAdder;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Background of our refit dialog: a Java copy of LunaLib's RefitPanelBackgroundPlugin (black panel,
 * half-black screen dim, border, Esc or a click outside closes it), except that the screen dim is
 * left out when the dialog opens over the add-hullmod picker, which already dims the screen.
 */
final class DialogBackground extends BaseCustomUIPanelPlugin {

    /** Opened from the add-hullmod picker: the picker's own dim is the only one. */
    final boolean overPicker;
    UIPanelAPI panel;
    private PositionAPI pos;

    DialogBackground(boolean overPicker) {
        this.overPicker = overPicker;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null) return;
        float x = pos.getX(), y = pos.getY(), w = pos.getWidth(), h = pos.getHeight();
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(0f, 0f, 0f, alphaMult);
        GL11.glRectf(x, y, x + w, y + h);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (!overPicker) {
            GL11.glColor4f(0f, 0f, 0f, 0.5f * alphaMult);
            GL11.glRectf(0f, 0f, Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight());
        }

        Color c = Misc.getDarkPlayerColor();
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f * alphaMult);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        GL11.glPopMatrix();
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (panel == null) return;
        PositionAPI p = panel.getPosition();
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isKeyUpEvent() && e.getEventValue() == Keyboard.KEY_ESCAPE) {
                e.consume();
                RefitButtonAdder.setRemoveActivePanel(true);
                continue;
            }
            if (e.isKeyboardEvent()) e.consume();
            if (e.isMouseDownEvent() && !p.containsEvent(e)) {
                RefitButtonAdder.setRemoveActivePanel(true);
                e.consume();
            }
            if (e.isMouseMoveEvent() || e.isMouseDownEvent() || e.isMouseScrollEvent()) e.consume();
        }
    }
}
