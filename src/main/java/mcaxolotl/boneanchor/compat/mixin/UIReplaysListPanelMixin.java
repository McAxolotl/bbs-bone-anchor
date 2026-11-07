package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.client.BodyPartEditing;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysListPanel;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds BodyPart-to-Replay conversion actions to the replay tree. */
@Mixin(value = UIReplaysListPanel.class, remap = false)
public abstract class UIReplaysListPanelMixin {
    @Shadow @Final public UIForms bodyParts;
    @Shadow private Replay bodyPartsReplay;
    @Shadow @Final private UIFilmPanel filmPanel;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void boneAnchor$bodyPartActions(CallbackInfo ci) {
        this.bodyParts.context(menu -> BodyPartEditing.contextMenu(menu, this.filmPanel, this.bodyPartsReplay, this.bodyParts));
    }
}
