package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.BoneAnchors;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep '/' inside a raw bone name from becoming a body-part path segment. */
@Mixin(value = FormUtils.class, remap = false)
public abstract class AnchorPropertyPathMixin {
    @Inject(method = "getProperty", at = @At("HEAD"), cancellable = true)
    private static void boneAnchor$rawName(Form form, String path, CallbackInfoReturnable<BaseValueBasic<?>> ci) {
        if (form == null) return;
        TrackId track = TrackId.parse(path);
        if (track != null && track.kind() == TrackKind.PROPERTY && BoneAnchors.isKey(track.subject())) {
            Form owner = FormUtils.getForm(form, track.formPath());
            ci.setReturnValue(owner == null ? null : owner.getBasic(track.subject()));
        }
    }
}
