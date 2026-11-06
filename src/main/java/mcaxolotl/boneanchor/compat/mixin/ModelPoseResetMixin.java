package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.client.BoneAnchorRuntime;
import mchorse.bbs_mod.cubic.data.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** BBS has no callback for these pose resets; rest/shadow samples can bypass the IK stage. */
@Mixin(value = Model.class, remap = false)
public abstract class ModelPoseResetMixin {
    @Inject(method="initialize",at=@At("TAIL"))
    private void boneAnchor$geometry(CallbackInfo ci){mcaxolotl.boneanchor.client.RigInstances.changed((Model)(Object)this);}
    @Inject(method = {"resetPose", "restoreChannels"}, at = @At("HEAD"))
    private void boneAnchor$resetEvaluation(CallbackInfo ci) {
        BoneAnchorRuntime.clearEvaluation((Model)(Object)this);
    }
}
