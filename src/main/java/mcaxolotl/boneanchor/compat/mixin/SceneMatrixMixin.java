package mcaxolotl.boneanchor.compat.mixin;
import mcaxolotl.boneanchor.client.BindingResolver;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import mchorse.bbs_mod.utils.pose.Transform;
@Mixin(value=FormRenderer.class,remap=false)
public abstract class SceneMatrixMixin {
    @Inject(method="createEvaluatedTransform",at=@At("HEAD"),cancellable=true)
    private void boneAnchor$localTransform(float transition,CallbackInfoReturnable<Transform> ci) {
        if(BindingResolver.localCollection(((FormRenderer<?>)(Object)this).getForm()))ci.setReturnValue(new Transform());
    }
    @Inject(target=@Desc(value="collectMatrices",args={IEntity.class,MatrixStack.class,MatrixCache.class,String.class,float.class},ret=void.class),at=@At("HEAD"),cancellable=true)
    private void boneAnchor$scene(IEntity entity,MatrixStack stack,MatrixCache matrices,String prefix,float transition,CallbackInfo ci) {
        if(BindingResolver.collectInto(((FormRenderer<?>)(Object)this).getForm(),transition,stack.peek().getPositionMatrix(),matrices,prefix))ci.cancel();
    }
}
