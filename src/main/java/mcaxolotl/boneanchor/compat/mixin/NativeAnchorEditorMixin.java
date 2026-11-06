package mcaxolotl.boneanchor.compat.mixin;

import java.util.function.Consumer;
import mcaxolotl.boneanchor.*;
import mcaxolotl.boneanchor.client.NativeAnchorEditing;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.*;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=UIAnchorKeyframeFactory.class,remap=false)
public abstract class NativeAnchorEditorMixin extends UIKeyframeFactory<Anchor> {
    protected NativeAnchorEditorMixin(Keyframe<Anchor> keyframe,UIKeyframes editor){super(keyframe,editor);}
    @Inject(method="rebase",at=@At("HEAD"),cancellable=true)
    private void boneAnchor$objectSpace(Anchor from,Anchor to,CallbackInfoReturnable<Boolean> ci) {
        if(keyframe.getFactory()!=BoneAnchors.FACTORY)return;
        var sheet=editor==null?null:editor.getGraph().getSheet(keyframe);
        var source=NativeAnchorEditing.source(getParent(UIFilmPanel.class),sheet);
        ci.setReturnValue(NativeAnchorEditing.rebase(source,sheet==null||sheet.descriptor==null?null:BoneAnchors.bone(sheet.descriptor.id().subject()),from,to));
    }
    @ModifyVariable(method="retarget",at=@At("HEAD"),argsOnly=true)
    private Consumer<Anchor> boneAnchor$structured(Consumer<Anchor> change) {
        if(keyframe.getFactory()!=BoneAnchors.FACTORY)return change;
        return anchor->{change.accept(anchor);NativeAnchorEditing.normalize(getParent(UIFilmPanel.class),anchor);};
    }
    @ModifyArg(method="pickTarget",at=@At(value="INVOKE",target="Lmchorse/bbs_mod/ui/film/controller/FilmStencilPicker;toggleTargetPick(Lmchorse/bbs_mod/ui/framework/elements/UIElement;Ljava/lang/String;Ljava/util/function/BiConsumer;)V"),index=1)
    private String boneAnchor$allowOwner(String excluded){return keyframe.getFactory()==BoneAnchors.FACTORY?"":excluded;}
}
