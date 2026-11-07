package mcaxolotl.boneanchor.client;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.Direction;

/** Describes an addon action through BBS's ordinary deferred tooltip pass. */
final class TooltipContextAction extends ContextAction {
    private final UIElement tooltipRow = new UIElement();

    TooltipContextAction(Icon icon, IKey label, IKey description, Runnable action) {
        super(icon, label, action);
        tooltipRow.tooltip(description, 260, Direction.RIGHT);
    }

    @Override
    public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected) {
        super.render(context, font, x, y, w, h, hover, selected);
        if (hover) {
            tooltipRow.area.set(x, y, w, h);
            context.tooltip.set(context, tooltipRow);
        }
    }
}
