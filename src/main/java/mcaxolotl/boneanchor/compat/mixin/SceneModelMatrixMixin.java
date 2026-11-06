package mcaxolotl.boneanchor.compat.mixin;
import mcaxolotl.boneanchor.client.BindingResolver;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ModelFormRenderer.class,remap=false)
public abstract class SceneModelMatrixMixin {
    @Inject(method="collectMatrices",at=@At("HEAD"),cancellable=true)
    private void boneAnchor$scene(IEntity entity,MatrixStack stack,MatrixCache matrices,String prefix,float transition,CallbackInfo ci) {
        if(BindingResolver.collectInto(((ModelFormRenderer)(Object)this).getForm(),transition,stack.peek().getPositionMatrix(),matrices,prefix))ci.cancel();
    }
}
