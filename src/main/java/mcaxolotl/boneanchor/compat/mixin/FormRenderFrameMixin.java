package mcaxolotl.boneanchor.compat.mixin;
import mcaxolotl.boneanchor.client.BindingRenderHooks;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/**
 * Hooks every drawn form — root actors and nested body parts alike — after animation states,
 * on the renderer's own pushed matrices and before its local transform.
 */
@Mixin(value=FormRenderer.class,remap=false)
public abstract class FormRenderFrameMixin {
    @Inject(target=@Desc(value="applyTransforms",args={MatrixStack.class,boolean.class,float.class},ret=void.class),at=@At("HEAD"))
    private void boneAnchor$parentFrame(MatrixStack stack,boolean origin,float transition,CallbackInfo ci) {
        BindingRenderHooks.prepareIfDrawing(((FormRenderer<?>)(Object)this).getForm(),stack,origin);
    }
}
