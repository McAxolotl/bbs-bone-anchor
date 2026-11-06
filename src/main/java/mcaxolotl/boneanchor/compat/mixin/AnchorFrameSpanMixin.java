package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.client.BoneAnchorRuntime;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.FormFrameCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A caller-owned span otherwise returns its entry before it reaches RenderFrame.collect. */
@Mixin(value = FormFrameCache.class, remap = false)
public abstract class AnchorFrameSpanMixin {
    @Inject(method = "collect(Lmchorse/bbs_mod/forms/forms/Form;Lmchorse/bbs_mod/forms/entities/IEntity;F)Lmchorse/bbs_mod/forms/renderers/utils/MatrixCache;",
        at = @At("HEAD"), cancellable = true)
    private void boneAnchor$fresh(Form form, IEntity entity, float transition, CallbackInfoReturnable<MatrixCache> ci) {
        if (BoneAnchorRuntime.requiresFreshMatrices(form))
            ci.setReturnValue(BoneAnchorRuntime.collect(form, transition));
    }
}
