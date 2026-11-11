package xpsmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.HullModEffect;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.MutableValue;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * The refit panel, laid out like vanilla's story-point "Build in" dialog. It is placed over the refit
 * frame minus the fleet list column (see {@link RefitFrame}, which also dims that column like vanilla),
 * with a bordered table of hullmods (tick rows to build in or remove) and a summary column on the
 * right ending in Confirm / Cancel.
 */
final class BuildInDialog extends BaseCustomUIPanelPlugin {

    private static final Logger log = Global.getLogger(BuildInDialog.class);

    private static final float PAD = 10f;
    private static final float RIGHT_W = 320f;
    private static final float HEADER_H = 25f;
    private static final float ROW_H = 36f;

    private static final String CHECK_SPRITE = "graphics/ui/buttons/checkmark20.png";
    private static final String LOCK_SPRITE = "graphics/ui/buttons/lock20.png";

    private static final String BUY_SLOT = "xpsmods_buy_slot";
    private static final String CONFIRM = "xpsmods_confirm";
    private static final String CANCEL = "xpsmods_cancel";

    private final XPSModsRefitButton owner;
    private final CustomPanelAPI background;
    private final FleetMemberAPI member;
    private final ShipVariantAPI variant;
    private final MarketAPI market;
    private final HullSize size;

    // Geometry (relative to the dialog's top-left).
    private final float width, height;
    private final float tableX, tableW, tableTop, tableBottom, rightX;
    /** Header/row width: the table box minus the same small gap on each side, like vanilla. */
    private final float contentW;
    /** Gap between the table box and its header/rows, on both sides. */
    private static final float TABLE_GAP = 4f;
    /** Measured in game: the scrolling row list starts its content this far right of where it's placed. */
    private static final float LIST_INSET = 10.5f;
    /** Measured in game: the header table starts this far right of where it's placed... */
    private static final float HEADER_INSET = 4f;
    /** ...and comes out this much wider than its columns add up to (cell padding). */
    private static final float HEADER_EXTRA_W = 11f;
    private final float colNameX, colNameW, colDesignX, colDesignW, colOpX, colOpW;
    private final float colCostX, colCostW, colStatusX, colStatusW;

    /** Pending changes, applied on Confirm. */
    private final Set<String> toBuild = new LinkedHashSet<String>();
    private final Set<String> toRemove = new LinkedHashSet<String>();

    private CustomPanelAPI root;
    private PositionAPI rootPos;
    /**
     * The right column lives in its own panel, replaced wholesale on every rebuild. Removing single
     * elements isn't enough: a scrolling element sits inside a wrapper the game adds, which would
     * stay behind and get drawn over.
     */
    private CustomPanelAPI rightPanel;

    BuildInDialog(XPSModsRefitButton owner, CustomPanelAPI background, FleetMemberAPI member,
                  ShipVariantAPI variant, MarketAPI market) {
        this.owner = owner;
        this.background = background;
        this.member = member;
        this.variant = variant;
        this.market = market;
        this.size = variant.getHullSize();

        width = background.getPosition().getWidth();
        height = background.getPosition().getHeight();
        // Measured from vanilla's dialog: the table box starts at the fleet list's edge, 3 units below
        // the frame top, and the right column starts 6 units after it.
        tableX = 1f;
        tableTop = 3f;
        tableBottom = height - 3f;
        tableW = width - RIGHT_W - PAD - tableX - 6f;
        rightX = tableX + tableW + 6f;
        contentW = tableW - 2f * TABLE_GAP;

        // Columns as fractions of the table, matching vanilla's proportions.
        colNameX = ROW_H + 8f;
        colDesignX = contentW * 0.42f;
        colOpX = contentW * 0.64f;
        colCostX = contentW * 0.73f;
        colStatusX = contentW * 0.88f;
        colNameW = colDesignX - colNameX;
        colDesignW = colOpX - colDesignX;
        colOpW = colCostX - colOpX;
        colCostW = colStatusX - colCostX;
        colStatusW = contentW - colStatusX;
    }

    // ------------------------------------------------------------------ layout

    void build() {
        if (root != null) background.removeComponent(root);
        rightPanel = null;
        root = background.createCustomPanel(width, height, this);
        buildTable();
        buildRight();
        background.addComponent(root).inTL(0f, 0f);
    }

    /** Whether rows can be ticked at all right now. */
    private String rowsLockedReason() {
        if (SModRules.isModule(member, variant)) return "Select the main hull to change its S-mods.";
        if (market == null) return "Dock at a market to build in or remove S-mods.";
        return null;
    }

    private void buildTable() {
        FactionAPI faction = Global.getSector().getPlayerFaction();
        TooltipMakerAPI header = root.createUIElement(contentW, HEADER_H, false);
        // Take the header's cell padding out of each column, so the whole header is contentW wide and
        // its column boundaries stay over the rows' columns.
        float cellPad = HEADER_EXTRA_W / 5f;
        header.beginTable2(faction, ROW_H, false, true,
                "Hullmod", colDesignX - cellPad, "Design type", colDesignW - cellPad, "OP", colOpW - cellPad,
                "Cost", colCostW - cellPad, "Status", colStatusW - cellPad);
        header.addTable("", 0, 0f);
        root.addUIElement(header).inTL(tableX + TABLE_GAP - HEADER_INSET, tableTop + 2f);

        float listTop = tableTop + 2f + HEADER_H + 2f;
        TooltipMakerAPI list = root.createUIElement(LIST_INSET + contentW + 2f, tableBottom - listTop, true);
        boolean locked = rowsLockedReason() != null;

        List<HullModSpecAPI> sMods = SModRules.currentSMods(variant);
        List<HullModSpecAPI> candidates = SModRules.buildInCandidates(variant);
        // Drop pending picks that no longer apply (for example after a confirm).
        toBuild.retainAll(ids(candidates));
        toRemove.retainAll(ids(sMods));

        for (HullModSpecAPI spec : sMods) {
            if (!XPSModsSettings.allowRemoval) break;
            list.addCustom(new Row(spec, true, locked).panel, 0f);
        }
        for (HullModSpecAPI spec : candidates) {
            list.addCustom(new Row(spec, false, locked).panel, 0f);
        }
        if (sMods.isEmpty() && candidates.isEmpty()) {
            list.addPara("No installed hullmods can be built in.", Misc.getGrayColor(), PAD);
        }
        root.addUIElement(list).inTL(tableX + TABLE_GAP - LIST_INSET, listTop);
    }

    private void buildRight() {
        if (rightPanel != null) root.removeComponent(rightPanel);
        // Its buttons go to the dialog; a separate plugin keeps the dialog's own position untouched.
        rightPanel = root.createCustomPanel(RIGHT_W, height, new BaseCustomUIPanelPlugin() {
            @Override
            public void buttonPressed(Object buttonId) {
                BuildInDialog.this.buttonPressed(buttonId);
            }
        });
        root.addComponent(rightPanel).inTL(rightX, 0f);

        Color hl = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();
        Color bad = Misc.getNegativeHighlightColor();
        Color good = Misc.getPositiveHighlightColor();
        String id = member.getId();
        float y = PAD;
        String locked = rowsLockedReason();
        if (locked != null) {
            TooltipMakerAPI lockedEl = element();
            lockedEl.addPara(locked, bad, 0f);
            y = place(lockedEl, y) + 10f;
        }

        // "S-mods / Ship XP" side by side, like vanilla's "Built-in hullmods / Bonus experience".
        int limit = SModRules.sModLimit(member);
        int usedAfter = Misc.getCurrSpecialMods(variant) - toRemove.size() + toBuild.size();
        TooltipMakerAPI left = stat("S-mods", usedAfter + " / " + limit, usedAfter > limit ? bad : hl, RIGHT_W / 2f);
        TooltipMakerAPI right = stat("Ship XP", Misc.getWithDGS(Math.round(XPData.getXP(id))), good, RIGHT_W / 2f);
        add(left).inTL(0f, y);
        add(right).inTL(RIGHT_W / 2f, y);
        y += Math.max(left.getHeightSoFar(), right.getHeightSoFar()) + 10f;

        // Credits, where vanilla shows story points.
        float credits = credits().get();
        y = place(stat("Credits", Misc.getWithDGS(Math.round(credits)), hl, RIGHT_W), y) + 15f;

        // Extra slots for XP (applies immediately, works anywhere).
        TooltipMakerAPI slots = element();
        slots.addSectionHeading("Extra S-mod slots", Alignment.MID, 0f);
        int bought = XPData.getBoughtSlots(id);
        int maxExtra = XPSModsSettings.maxExtraSlots;
        slots.addPara("Bought: %s of %s", 6f, hl, String.valueOf(bought), String.valueOf(maxExtra));
        String slotReason = null;
        if (SModRules.isModule(member, variant)) {
            slotReason = "Select the main hull to buy slots.";
        } else if (bought >= maxExtra) {
            slotReason = "Maximum extra slots reached.";
        } else {
            float slotCost = XPData.nextSlotCost(bought);
            slots.addPara("Next slot: %s XP", 3f, hl, Misc.getWithDGS(Math.round(slotCost)));
            if (XPData.getXP(id) < slotCost) slotReason = "Not enough XP.";
        }
        ButtonAPI buy = button(slots, "Buy slot", BUY_SLOT, RIGHT_W / 2f, 6f);
        buy.setEnabled(slotReason == null);
        if (slotReason != null) slots.addPara(slotReason, gray, 3f);
        y = place(slots, y) + 15f;

        // Bottom, stacked upwards from the bottom edge like vanilla: note, buttons, status/total.
        float bottomY = height - PAD;

        TooltipMakerAPI note = element();
        note.addPara("Credits are spent when you confirm.", gray, 0f);
        bottomY -= note.getHeightSoFar();
        add(note).inTL(0f, bottomY);

        float buttonW = (RIGHT_W - 10f) / 2f;
        TooltipMakerAPI buttons = element();
        ButtonAPI confirm = button(buttons, "Confirm", CONFIRM, buttonW, 0f);
        ButtonAPI cancel = button(buttons, "Cancel", CANCEL, buttonW, 0f);
        cancel.getPosition().rightOfTop(confirm, 10f);
        float total = pendingCost();
        String reason = confirmBlockedReason(usedAfter, limit, total, credits);
        confirm.setShortcut(Keyboard.KEY_G, false);
        confirm.setEnabled(reason == null);
        cancel.setShortcut(Keyboard.KEY_Q, false);
        bottomY -= 25f + 6f;
        add(buttons).inTL(0f, bottomY);

        TooltipMakerAPI status = element();
        boolean anyPicked = !toBuild.isEmpty() || !toRemove.isEmpty();
        if (anyPicked) {
            status.addPara("Total: %s credits", 0f, total > credits ? bad : hl, Misc.getWithDGS(Math.round(total)));
        }
        if (reason != null) status.addPara(reason, anyPicked ? bad : gray, anyPicked ? 3f : 0f);
        bottomY -= status.getHeightSoFar() + 8f;
        add(status).inTL(0f, bottomY);

        // S-mod bonuses of every ticked hullmod, one under the other like vanilla; hullmods without a
        // bonus are left out. Scrolls if they don't fit above the bottom stack.
        // Bonuses being lost to a removal follow, the same way but in red.
        List<HullModSpecAPI> gained = withSModBonus(toBuild);
        List<HullModSpecAPI> lost = withSModBonus(toRemove);
        float space = bottomY - 10f - y;
        if ((!gained.isEmpty() || !lost.isEmpty()) && space > 40f) {
            TooltipMakerAPI bonuses = rightPanel.createUIElement(RIGHT_W, space, true);
            float pad = 0f;
            for (HullModSpecAPI spec : gained) {
                bonuses.addSectionHeading(spec.getDisplayName() + " bonus", good, darker(good), Alignment.MID, pad);
                addSModBonus(bonuses, spec, RIGHT_W - 10f, Misc.getHighlightColor());
                pad = 12f;
            }
            for (HullModSpecAPI spec : lost) {
                bonuses.addSectionHeading(spec.getDisplayName() + " bonus", bad, darker(bad), Alignment.MID, pad);
                addSModBonus(bonuses, spec, RIGHT_W - 10f, bad);
                pad = 12f;
            }
            add(bonuses).inTL(0f, y);
        }
    }

    private String confirmBlockedReason(int usedAfter, int limit, float total, float credits) {
        String locked = rowsLockedReason();
        if (locked != null) return locked;
        if (toBuild.isEmpty() && toRemove.isEmpty()) return "Select hullmods to build in or remove.";
        if (usedAfter > limit) return "Not enough free S-mod slots (" + (usedAfter - limit) + " short).";
        if (total > credits) return "Not enough credits.";
        return null;
    }

    private float pendingCost() {
        float total = 0f;
        for (String id : toBuild) total += SModRules.buildInCost(Global.getSettings().getHullModSpec(id), size);
        for (String id : toRemove) total += SModRules.removalCost(Global.getSettings().getHullModSpec(id), size);
        return total;
    }

    // ------------------------------------------------------------------ actions

    @Override
    public void buttonPressed(Object buttonId) {
        try {
            if (CANCEL.equals(buttonId)) {
                owner.closePanel();
            } else if (BUY_SLOT.equals(buttonId)) {
                buySlot();
                build();
            } else if (CONFIRM.equals(buttonId)) {
                confirm();
                build();
            }
        } catch (Throwable t) {
            log.error("XP S-Mods: refit panel action failed (" + buttonId + ")", t);
        }
    }

    private void buySlot() {
        if (SModRules.isModule(member, variant)) return;
        String id = member.getId();
        int bought = XPData.getBoughtSlots(id);
        float cost = XPData.nextSlotCost(bought);
        if (bought >= XPSModsSettings.maxExtraSlots || XPData.getXP(id) < cost) return;

        XPData.spendXP(id, cost);
        XPData.setBoughtSlots(id, bought + 1);
        // Put the hullmod on the refit screen's variant too: LunaLib's refresh saves that variant
        // back onto the ship, which would otherwise drop a hullmod added only to member.getVariant().
        SlotBonus.ensureHullmod(variant);
        SlotBonus.ensureHullmod(member);
        member.updateStats();
        owner.refreshVariant();
        Global.getSoundPlayer().playUISound("ui_char_spent_story_point_technology", 1f, 1f);
        log.info(String.format("XP S-Mods: %s bought slot %d for %.0f XP (%.0f XP left)",
                member.getShipName(), bought + 1, cost, XPData.getXP(id)));
    }

    private void confirm() {
        int limit = SModRules.sModLimit(member);
        int usedAfter = Misc.getCurrSpecialMods(variant) - toRemove.size() + toBuild.size();
        float total = pendingCost();
        if (confirmBlockedReason(usedAfter, limit, total, credits().get()) != null) return;

        for (String id : toRemove) {
            variant.removePermaMod(id);
            // Remove it from the ship entirely, not just demote it to a normal hullmod.
            if (variant.hasHullMod(id)) variant.removeMod(id);
            log.info("XP S-Mods: " + member.getShipName() + " removed S-mod " + id);
        }
        for (String id : toBuild) {
            variant.addPermaMod(id, true);
            log.info("XP S-Mods: " + member.getShipName() + " built in " + id);
        }
        credits().subtract(total);
        log.info(String.format("XP S-Mods: %s paid %.0f credits", member.getShipName(), total));
        member.updateStats();
        owner.refreshVariant();
        Global.getSoundPlayer().playUISound(toBuild.isEmpty() ? "ui_objective_constructed" : "ui_acquired_hullmod", 1f, 1f);
        toBuild.clear();
        toRemove.clear();
    }

    void rowToggled(Row row, boolean checked) {
        String id = row.spec.getId();
        if (row.isSMod) {
            if (checked) toRemove.add(id); else toRemove.remove(id);
        } else {
            if (checked) toBuild.add(id); else toBuild.remove(id);
        }
        row.refresh();
        buildRight();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void positionChanged(PositionAPI position) {
        rootPos = position;
    }

    /** The ship, faded, behind the table, like vanilla. */
    @Override
    public void renderBelow(float alphaMult) {
        if (rootPos == null) return;
        SpriteAPI ship = Global.getSettings().getSprite(member.getHullSpec().getSpriteName());
        if (ship == null || ship.getWidth() <= 0f) return;
        float ow = ship.getWidth(), oh = ship.getHeight();
        float scale = Math.min(tableW * 0.6f / ow, (tableBottom - tableTop) * 0.6f / oh);
        ship.setSize(ow * scale, oh * scale);
        ship.setAlphaMult(alphaMult * 0.12f);
        ship.renderAtCenter(rootPos.getX() + tableX + tableW / 2f,
                rootPos.getY() + height - (tableTop + tableBottom) / 2f);
        // getSprite returns a shared sprite: put its size back for other users.
        ship.setSize(ow, oh);
    }

    /** Thin border round the table, like vanilla's. */
    @Override
    public void render(float alphaMult) {
        if (rootPos == null) return;
        Color c = Global.getSector().getPlayerFaction().getDarkUIColor();
        float x1 = rootPos.getX() + tableX - 1f, x2 = x1 + tableW + 2f;
        float yTop = rootPos.getY() + height - tableTop + 1f, yBottom = rootPos.getY() + height - tableBottom - 1f;
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alphaMult);
        GL11.glLineWidth(1f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x1, yBottom);
        GL11.glVertex2f(x2, yBottom);
        GL11.glVertex2f(x2, yTop);
        GL11.glVertex2f(x1, yTop);
        GL11.glEnd();
        GL11.glPopMatrix();
    }

    // ------------------------------------------------------------------ one table row

    /** A selectable table row: area checkbox underneath, icon and column labels on top. */
    private final class Row extends BaseCustomUIPanelPlugin {
        final HullModSpecAPI spec;
        final boolean isSMod;
        final CustomPanelAPI panel;
        private final ButtonAPI box;
        private final LabelAPI name, op, status;
        private PositionAPI pos;

        Row(HullModSpecAPI spec, boolean isSMod, boolean locked) {
            this.spec = spec;
            this.isSMod = isSMod;
            FactionAPI faction = Global.getSector().getPlayerFaction();
            panel = root.createCustomPanel(contentW, ROW_H, this);

            TooltipMakerAPI boxEl = panel.createUIElement(contentW, ROW_H, false);
            box = boxEl.addAreaCheckbox("", "row", faction.getBaseUIColor(), faction.getDarkUIColor(),
                    faction.getBrightUIColor(), contentW, ROW_H, 0f);
            box.setChecked(isSMod ? toRemove.contains(spec.getId()) : toBuild.contains(spec.getId()));
            box.setEnabled(!locked);
            boxEl.addTooltipToPrevious(new HullModTooltip(spec), TooltipLocation.RIGHT);
            panel.addUIElement(boxEl).inTL(0f, 0f);

            float icon = ROW_H - 6f;
            TooltipMakerAPI iconEl = panel.createUIElement(icon, icon, false);
            iconEl.addImage(spec.getSpriteName(), icon, icon, 0f);
            panel.addUIElement(iconEl).inTL(4f, 3f);

            float cost = isSMod ? SModRules.removalCost(spec, size) : SModRules.buildInCost(spec, size);
            name = label(spec.getDisplayName(), Misc.getHighlightColor(), colNameX, colNameW, Alignment.LMID);
            label(spec.getManufacturer(), Misc.getGrayColor(), colDesignX, colDesignW, Alignment.MID);
            op = label("", Misc.getGrayColor(), colOpX, colOpW, Alignment.MID);
            label(Misc.getWithDGS(Math.round(cost)) + " cr", Misc.getGrayColor(), colCostX, colCostW, Alignment.MID);
            status = label("", Misc.getNegativeHighlightColor(), colStatusX, colStatusW, Alignment.MID);
            refresh();
        }

        private LabelAPI label(String text, Color color, float x, float w, Alignment align) {
            TooltipMakerAPI el = panel.createUIElement(w, ROW_H, false);
            LabelAPI l = el.addPara(text, color, 0f);
            l.setAlignment(align);
            panel.addUIElement(el).inTL(x, ROW_H / 2f - 8f);
            return l;
        }

        private boolean building() {
            return !isSMod && toBuild.contains(spec.getId());
        }

        private boolean removing() {
            return isSMod && toRemove.contains(spec.getId());
        }

        /** Vanilla shows "n/a" OP once a hullmod is (or will be) built in. */
        void refresh() {
            boolean builtIn = (isSMod && !removing()) || building();
            op.setText(builtIn ? "n/a" : String.valueOf(spec.getCostFor(size)));
            name.setColor(removing() ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor());
            status.setText(removing() ? "Remove" : "");
        }

        @Override
        public void buttonPressed(Object buttonId) {
            rowToggled(this, box.isChecked());
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        /** Status column icon: lock for S-mods (current or pending), check for normal installed. */
        @Override
        public void render(float alphaMult) {
            if (pos == null || removing()) return;
            boolean builtIn = isSMod || building();
            SpriteAPI icon = Global.getSettings().getSprite(builtIn ? LOCK_SPRITE : CHECK_SPRITE);
            if (icon == null) return;
            icon.setSize(20f, 20f);
            icon.setColor(Misc.getHighlightColor());
            icon.setAlphaMult(alphaMult);
            icon.renderAtCenter(pos.getX() + colStatusX + colStatusW / 2f, pos.getY() + ROW_H / 2f);
        }
    }

    // ------------------------------------------------------------------ hover tooltip

    /** Hullmod name and description with its numbers filled in, plus the Codex link (F2). */
    private final class HullModTooltip implements TooltipCreator {
        private final HullModSpecAPI spec;

        HullModTooltip(HullModSpecAPI spec) {
            this.spec = spec;
        }

        @Override
        public boolean isTooltipExpandable(Object tooltipParam) {
            return false;
        }

        @Override
        public float getTooltipWidth(Object tooltipParam) {
            return 400f;
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
            tooltip.addTitle(spec.getDisplayName());
            HullModEffect effect = spec.getEffect();
            List<String> params = new ArrayList<String>();
            for (int i = 0; i < 10; i++) {
                String p = null;
                try {
                    p = effect.getDescriptionParam(i, size);
                } catch (Throwable ignored) {
                }
                if (p == null) break;
                params.add(p);
            }
            formattedPara(tooltip, spec.getDescription(size), params, 10f, Misc.getHighlightColor());
            if (hasSModBonus(spec)) {
                Color good = Misc.getPositiveHighlightColor();
                tooltip.addSectionHeading("S-mod bonus", good, darker(good), Alignment.MID, 10f);
                addSModBonus(tooltip, spec, getTooltipWidth(null) - 10f, Misc.getHighlightColor());
            }
            try {
                tooltip.setCodexEntryId(CodexDataV2.getHullmodEntryId(spec.getId()));
            } catch (Throwable ignored) {
                // Codex link is a nicety.
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The hullmods in ids that have an S-mod bonus, in order. */
    private List<HullModSpecAPI> withSModBonus(Set<String> ids) {
        List<HullModSpecAPI> out = new ArrayList<HullModSpecAPI>();
        for (String modId : ids) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(modId);
            if (spec != null && hasSModBonus(spec)) out.add(spec);
        }
        return out;
    }

    /** Whether building this hullmod in gives an S-mod bonus worth listing. */
    private boolean hasSModBonus(HullModSpecAPI spec) {
        try {
            if (spec.getEffect().hasSModEffectSection(size, null, false)) return true;
        } catch (Throwable ignored) {
            // Fall through to the text check.
        }
        String text = spec.getSModDescription(size);
        return text != null && !text.trim().isEmpty();
    }

    /** A hullmod's S-mod effect: vanilla's own section if it has one, else its S-mod text. */
    private void addSModBonus(TooltipMakerAPI t, HullModSpecAPI spec, float w, Color highlight) {
        HullModEffect effect = spec.getEffect();
        try {
            if (effect.hasSModEffectSection(size, null, false)) {
                effect.addSModEffectSection(t, size, null, w, false, true);
                return;
            }
        } catch (Throwable ignored) {
            // Some effects need a live ship; fall back to the plain text below.
        }
        String text = spec.getSModDescription(size);
        if (text == null || text.trim().isEmpty()) return;
        List<String> params = new ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            String p = null;
            try {
                p = effect.getSModDescriptionParam(i, size);
            } catch (Throwable ignored) {
            }
            if (p == null) break;
            params.add(p);
        }
        formattedPara(t, text, params, 5f, highlight);
    }

    /** Vanilla hullmod text uses %s placeholders for highlighted numbers. */
    private static void formattedPara(TooltipMakerAPI t, String format, List<String> params, float pad, Color highlight) {
        try {
            t.addPara(format, pad, highlight, params.toArray(new String[0]));
        } catch (Throwable e) {
            t.addPara(format.replace("%s", "?").replace("%%", "%"), pad);
        }
    }

    private TooltipMakerAPI element() {
        return rightPanel.createUIElement(RIGHT_W, 10f, false);
    }

    /** Places a right-column element at y and returns the y below it. */
    private float place(TooltipMakerAPI el, float y) {
        add(el).inTL(0f, y);
        return y + el.getHeightSoFar();
    }

    private PositionAPI add(TooltipMakerAPI el) {
        return rightPanel.addUIElement(el);
    }

    private TooltipMakerAPI stat(String title, String value, Color valueColor, float w) {
        TooltipMakerAPI el = rightPanel.createUIElement(w, 10f, false);
        el.addPara(title, Misc.getGrayColor(), 0f).setAlignment(Alignment.MID);
        el.setParaOrbitronLarge();
        el.addPara(value, valueColor, 2f).setAlignment(Alignment.MID);
        el.setParaFontDefault();
        return el;
    }

    private static ButtonAPI button(TooltipMakerAPI t, String text, String data, float w, float pad) {
        return t.addButton(text, data, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.ALL, w, 25f, pad);
    }

    private static Color darker(Color c) {
        return new Color(c.getRed() / 4, c.getGreen() / 4, c.getBlue() / 4, 255);
    }

    private static MutableValue credits() {
        return Global.getSector().getPlayerFleet().getCargo().getCredits();
    }

    private static Set<String> ids(List<HullModSpecAPI> specs) {
        Set<String> out = new LinkedHashSet<String>();
        for (HullModSpecAPI s : specs) out.add(s.getId());
        return out;
    }

}
