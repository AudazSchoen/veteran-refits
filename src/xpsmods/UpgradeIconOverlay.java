package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Draws a green "upgrade" arrow in the top-left of the refit screen's ship cards for ships that can buy their next
 * S-mod slot. One invisible overlay panel is attached to the refit tab; every frame it reads the
 * cards and their ships (refitTab.getFleetList().getItems() and refitTab.getButtonToMember(), the
 * same path Exiled Sector uses) and draws at each card's current position, so scrolling just works.
 */
public class UpgradeIconOverlay extends BaseCustomUIPanelPlugin {

    public static final String SPRITE = "graphics/icons/intel/log_upgrade.png";
    private static final Logger log = Global.getLogger(UpgradeIconOverlay.class);
    private static final float ICON_SIZE = 24f;
    private static final float INSET = 4f;

    /** Set on the first failure; the icon then stays off until the game restarts. */
    static boolean failed = false;

    private final Object refitTab;
    private float time = 0f;

    private UpgradeIconOverlay(Object refitTab) {
        this.refitTab = refitTab;
    }

    /** Adds the overlay to the refit tab unless it's already there. */
    static void ensureAttached(UIPanelAPI refitTab) throws Throwable {
        if (failed || !XPSModsSettings.showUpgradeIcon) return;
        for (Object child : UiReflect.children(refitTab)) {
            if (child instanceof CustomPanelAPI && ((CustomPanelAPI) child).getPlugin() instanceof UpgradeIconOverlay) {
                return;
            }
        }
        // Cover the whole tab so the game never culls it; it has no elements and ignores input,
        // so clicks still reach the ship cards underneath.
        PositionAPI tabPos = refitTab.getPosition();
        CustomPanelAPI overlay = Global.getSettings().createCustom(tabPos.getWidth(), tabPos.getHeight(),
                new UpgradeIconOverlay(refitTab));
        refitTab.addComponent(overlay).inTL(0f, 0f);
    }

    @Override
    public void advance(float amount) {
        time += amount;
    }

    @Override
    public void render(float alphaMult) {
        if (failed) return;
        try {
            draw(alphaMult);
        } catch (Throwable t) {
            failed = true;
            log.error("XP S-Mods: could not draw upgrade icons on the refit ship list; "
                    + "they stay off until the game restarts", t);
        }
    }

    private void draw(float alphaMult) throws Throwable {
        Object fleetList = UiReflect.call(refitTab, "getFleetList");
        Object items = UiReflect.call(fleetList, "getItems");
        Object map = UiReflect.call(refitTab, "getButtonToMember");
        if (!(items instanceof List) || !(map instanceof Map)) return;
        PositionAPI listPos = fleetList instanceof UIComponentAPI ? ((UIComponentAPI) fleetList).getPosition() : null;

        SpriteAPI sprite = Global.getSettings().getSprite(SPRITE);
        if (sprite == null) return;
        sprite.setSize(ICON_SIZE, ICON_SIZE);
        // Gentle pulse so it reads as "something to do" without being loud.
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 3f);

        for (Object item : (List<?>) items) {
            if (!(item instanceof UIComponentAPI)) continue;
            Object member = ((Map<?, ?>) map).get(item);
            if (!(member instanceof FleetMemberAPI)) continue;
            if (!XPData.canBuySlot(((FleetMemberAPI) member).getId())) continue;

            PositionAPI p = ((UIComponentAPI) item).getPosition();
            // Top-left corner: the top-right holds the vanilla S-mod / d-mod indicators.
            float x = p.getX() + INSET;
            float y = p.getY() + p.getHeight() - ICON_SIZE - INSET;
            // Skip cards scrolled out of the list's visible area.
            if (listPos != null && (y < listPos.getY() || y + ICON_SIZE > listPos.getY() + listPos.getHeight())) continue;

            sprite.setAlphaMult(alphaMult * pulse);
            sprite.render(x, y);
        }
    }
}
