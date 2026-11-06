package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.client.BoneAnchorRuntime;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only externally bound forms bypass the form-local cache; ordinary forms keep BBS's caching. */
@Mixin(value = RenderFrame.class, remap = false)
public abstract class AnchorFrameCacheMixin {
    @Inject(method = "collect", at = @At("HEAD"), cancellable = true)
    private static void boneAnchor$fresh(Form form, IEntity entity, float transition, CallbackInfoReturnable<MatrixCache> ci) {
        if (BoneAnchorRuntime.requiresFreshMatrices(form))
            ci.setReturnValue(BoneAnchorRuntime.collect(form, transition));
    }
}
