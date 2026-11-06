package mcaxolotl.boneanchor.compat.mixin;
import java.util.Map;
import org.joml.Vector3f;
import mcaxolotl.boneanchor.client.BindingResolver;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
/** Re-resolve standard film IK targets at their dependency stage, without changing saved values. */
@Mixin(value=ModelIKRuntime.class,remap=false)
public abstract class SceneIKGoalsMixin {
    @ModifyArgs(method="apply",at=@At(value="INVOKE",target="Lmchorse/bbs_mod/cubic/ik/ModelIKApplier;apply(Lmchorse/bbs_mod/cubic/IModel;Ljava/util/List;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;)V"))
    private static void boneAnchor$goals(Args args,ModelInstance instance,Map<String,Vector3f> targets,Map<String,Vector3f> poles) {
        var resolved=BindingResolver.goals(instance,args.get(3),args.get(5));args.set(3,resolved.targets());args.set(5,resolved.weights());
    }
}
