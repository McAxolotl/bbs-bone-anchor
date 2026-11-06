package mcaxolotl.boneanchor.compat.mixin;
import java.util.Map;
import mcaxolotl.boneanchor.client.*;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ModelIKRuntime.class,remap=false)
public abstract class ModelIKRuntimeMixin {
    @Inject(method="apply",at=@At("HEAD"),cancellable=true)
    private static void boneAnchor$before(ModelInstance instance,Map<String,Vector3f> targets,Map<String,Vector3f> poles,CallbackInfo ci) {
        if(instance!=null && BoneAnchorRuntime.intercept(instance,targets,poles))ci.cancel();
    }
}
