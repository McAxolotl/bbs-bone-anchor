package mcaxolotl.boneanchor.compat.mixin;
import mcaxolotl.boneanchor.BoneAnchors;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value = Form.class, remap = false)
public abstract class FormMixin {
    @Inject(method = "getBasic", at = @At("HEAD"))
    private void boneAnchor$lookup(String key, CallbackInfoReturnable<BaseValueBasic<?>> ci) {
        Form form=(Form)(Object)this;
        if(mchorse.bbs_mod.forms.FormUtils.getPath(form).isEmpty())BoneAnchors.ensure(form, key);
    }
    @Inject(method = "fromData", at = @At("HEAD"))
    private void boneAnchor$read(BaseType data, CallbackInfo ci) {
        if (data != null && data.isMap()) {
            Form form = (Form)(Object)this;
            BoneAnchors.retain(form, data.asMap().keys());
            for (String key : data.asMap().keys()) BoneAnchors.ensure(form, key);
        }
    }
    @Inject(method = "copy", at = @At("HEAD"))
    private void boneAnchor$copy(BaseValueGroup source, CallbackInfo ci) {
        BoneAnchors.prepareCopy((Form)(Object)this, source);
    }
    @Inject(method = "toData", at = @At("RETURN"))
    private void boneAnchor$write(CallbackInfoReturnable<BaseType> ci) {
        if (ci.getReturnValue() instanceof MapType data)
            BoneAnchors.stripDefaults((Form)(Object)this, data);
    }
}
