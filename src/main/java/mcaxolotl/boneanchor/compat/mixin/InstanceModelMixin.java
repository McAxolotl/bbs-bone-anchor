package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.client.RigInstances;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The addon API does not expose per-renderer model allocation. */
@Mixin(value=ModelFormRenderer.class,remap=false)
public abstract class InstanceModelMixin {
    @Inject(method="getModel()Lmchorse/bbs_mod/cubic/ModelInstance;",at=@At("RETURN"),cancellable=true)
    private void boneAnchor$instance(CallbackInfoReturnable<ModelInstance> ci) {
        ci.setReturnValue(RigInstances.view(((ModelFormRenderer)(Object)this).getForm(),ci.getReturnValue()));
    }
}
