package mcaxolotl.boneanchor.compat.mixin;
import java.util.List;
import mcaxolotl.boneanchor.BoneAnchors;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.*;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.cubic.data.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value = TrackCatalog.class, remap = false)
public abstract class TrackCatalogMixin {
    @Inject(method = "of(Lmchorse/bbs_mod/forms/forms/Form;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private static void boneAnchor$orderFilter(Form form, CallbackInfoReturnable<List<TrackDescriptor>> ci) {
        List<TrackDescriptor> tracks = ci.getReturnValue();
        for (TrackDescriptor track : tracks) {
            if (track.kind() == TrackKind.PROPERTY &&
                (track.id().subject().equals("anchor") || BoneAnchors.isKey(track.id().subject()))) {
                ci.setReturnValue(TrackCatalog.ordered(tracks));
                return;
            }
        }
    }
    @Inject(method = "collect", at = @At("HEAD"))
    private static void boneAnchor$prepare(Form root, Form form, String path, FormProperties properties, List<TrackDescriptor> out, CallbackInfo ci) {
        if (!(form instanceof ModelForm mf) || !path.isEmpty() || !mchorse.bbs_mod.forms.FormUtils.getPath(form).isEmpty()) return;
        var instance = ModelFormRenderer.getModel(mf);
        if (instance != null && instance.model instanceof Model model) {
            for (var group : model.getAllGroups()) BoneAnchors.ensure(form, BoneAnchors.key(group.id));
        }
    }
    @Inject(method = "collect", at = @At("TAIL"))
    private static void boneAnchor$group(Form root, Form form, String path, FormProperties properties, List<TrackDescriptor> out, CallbackInfo ci) {
        if(!path.isEmpty() || !mchorse.bbs_mod.forms.FormUtils.getPath(form).isEmpty()) {
            out.removeIf(t->t.owner()==form && t.kind()==TrackKind.PROPERTY &&
                (t.id().subject().equals("anchor") || BoneAnchors.isKey(t.id().subject())));
            return;
        }
        if (!(form instanceof ModelForm)) return;
        var instance = ModelFormRenderer.getModel((ModelForm) form);
        Model model = instance != null && instance.model instanceof Model m ? m : null;
        mcaxolotl.boneanchor.client.AnchorTrackLayout.apply(form, model, out,
            mcaxolotl.boneanchor.client.BoneAnchorClient.boundOnly());
    }
}
