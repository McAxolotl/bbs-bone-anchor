package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.client.AnchorScales;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.ICubicRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The addon API has no evaluated-scale field; every cubic matrix walk goes through this method. */
@Mixin(value = ICubicRenderer.class, remap = false)
public interface CubicScaleMixin {
    @Inject(method = "scaleGroup", at = @At("HEAD"), cancellable = true)
    private static void boneAnchor$scale(MatrixStack stack, ModelGroup group, CallbackInfo ci) {
        var scale = AnchorScales.get(group);
        if (scale != null) {
            MatrixStackUtils.scaleStack(stack, scale.x, scale.y, scale.z);
            ci.cancel();
        }
    }
}
