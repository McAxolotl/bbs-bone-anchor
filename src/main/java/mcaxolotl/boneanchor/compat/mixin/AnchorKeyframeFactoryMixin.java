package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.BindingAnchor;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.utils.keyframes.factories.AnchorKeyframeFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve converted BodyPart inheritance through the native whole-form Anchor factory. */
@Mixin(value = AnchorKeyframeFactory.class, remap = false)
public abstract class AnchorKeyframeFactoryMixin {
    @Inject(method = "fromData(Lmchorse/bbs_mod/data/types/BaseType;)Lmchorse/bbs_mod/forms/forms/utils/Anchor;", at = @At("HEAD"), cancellable = true)
    private void boneAnchor$localInheritance(BaseType data, CallbackInfoReturnable<Anchor> ci) {
        if (data.isMap() && data.asMap().getBool("inherit_local", false)) {
            BindingAnchor anchor = new BindingAnchor();
            anchor.fromData(data.asMap());
            ci.setReturnValue(anchor);
        }
    }
}
