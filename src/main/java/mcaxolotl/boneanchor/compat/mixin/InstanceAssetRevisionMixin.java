package mcaxolotl.boneanchor.compat.mixin;
import mcaxolotl.boneanchor.client.RigInstances;
import mchorse.bbs_mod.cubic.ModelInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ModelInstance.class,remap=false)
public abstract class InstanceAssetRevisionMixin {
    @Inject(method={"rebakeGroups","invalidateWelds"},at=@At("HEAD"))
    private void boneAnchor$revision(CallbackInfo ci){RigInstances.changed((ModelInstance)(Object)this);}
}
