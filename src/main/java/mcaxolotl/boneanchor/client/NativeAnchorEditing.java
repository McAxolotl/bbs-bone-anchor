package mcaxolotl.boneanchor.client;

import java.util.*;
import mcaxolotl.boneanchor.*;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import org.joml.Matrix4f;

/** Adapt only the bound object; the native editor owns all widgets, selections and edit transactions. */
public final class NativeAnchorEditing {
    public static BindingScenes.Slot source(UIFilmPanel panel,UIKeyframeSheet sheet) {
        if(panel==null || sheet==null || sheet.descriptor==null || panel.replayEditor.getReplay()==null || panel.getController().editorController==null)return null;
        if(!sheet.descriptor.id().formPath().isEmpty() || !BoneAnchors.isKey(sheet.descriptor.id().subject()))return null;
        return BindingScenes.scene(panel.getController().editorController).root(panel.replayEditor.getReplay().getId());
    }
    public static void normalize(UIFilmPanel panel,Anchor anchor) {
        if(!(anchor instanceof BindingAnchor own) || panel==null || panel.getController().editorController==null)return;
        if(!anchor.hasTarget()){own.target(List.of(),null);return;}
        var scene=BindingScenes.scene(panel.getController().editorController);
        try(var request=BindingResolver.request(scene,0)) {
            var address=request.session.address(anchor);
            if(address!=null)own.target(address.slot().path,address.bone());
        }
    }
    public static boolean rebase(BindingScenes.Slot source,String bone,Anchor from,Anchor next) {
        if(source==null || source.parent!=null || bone==null || bone.isEmpty() || from==null || next==null)return false;
        try(var request=BindingResolver.request(source.scene,BoneAnchorRuntime.transition(source.form))) {
            // Keep the edited keyframe's settled world pose. The pose itself is measured at the last
            // sampled transition, like BBS's own rebase; only the blend state (previous/x) is ignored.
            Matrix4f oldExpression=request.session.reference(source,bone,from);
            Matrix4f world=oldExpression.mul(from.transform.createMatrix());
            Matrix4f nextExpression=request.session.reference(source,bone,next);
            Matrix4f offset=AnchorMath.inverse(nextExpression).mul(world);
            AnchorMath.decompose(offset);next.transform.fromMatrix(offset);return true;
        }catch(IllegalArgumentException ignored) {
            return false;
        }
    }
    private NativeAnchorEditing(){}
}
