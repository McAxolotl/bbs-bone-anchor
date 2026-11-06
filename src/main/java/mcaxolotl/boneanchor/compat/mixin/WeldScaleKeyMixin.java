package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.client.AnchorScales;
import mchorse.bbs_mod.cubic.ModelInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Include addon-owned evaluated scale in BBS's welded-geometry cache key. */
@Mixin(value = ModelInstance.class, remap = false)
public abstract class WeldScaleKeyMixin {
    @Inject(method = "weldKey", at = @At("RETURN"), cancellable = true)
    private void boneAnchor$scaleKey(CallbackInfoReturnable<Long> ci) {
        long scale = AnchorScales.key(((ModelInstance)(Object)this).model);
        if (scale != 0) ci.setReturnValue(ci.getReturnValue() * 31 + scale);
    }
}
