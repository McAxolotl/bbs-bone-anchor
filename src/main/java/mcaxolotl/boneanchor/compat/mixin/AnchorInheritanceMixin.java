package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.BindingAnchor;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native and parent-local inheritance are distinct interpolation endpoints in either order. */
@Mixin(value = Anchor.class, remap = false)
public abstract class AnchorInheritanceMixin {
    @Inject(method = "hasSameTarget", at = @At("HEAD"), cancellable = true)
    private void boneAnchor$inheritanceSpace(Anchor other, CallbackInfoReturnable<Boolean> ci) {
        if (BindingAnchor.local((Anchor)(Object)this) != BindingAnchor.local(other)) ci.setReturnValue(false);
    }
}
