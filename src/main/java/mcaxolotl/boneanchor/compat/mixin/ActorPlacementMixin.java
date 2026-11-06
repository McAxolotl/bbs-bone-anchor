package mcaxolotl.boneanchor.compat.mixin;

import java.util.Map;
import mcaxolotl.boneanchor.*;
import mcaxolotl.boneanchor.client.*;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.utils.FormFrameCache;
import mchorse.bbs_mod.utils.Pair;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Root actor placement must use the same evaluated scene as drawing, tracking and picking. */
@Mixin(value=FilmMatrices.class,remap=false)
public abstract class ActorPlacementMixin {
    @Inject(target=@Desc(value="getTotalMatrix",args={Map.class,Anchor.class,Matrix4f.class,double.class,double.class,double.class,
        float.class,int.class,boolean.class,FormFrameCache.class},ret=Pair.class),at=@At("HEAD"),cancellable=true)
    private static void boneAnchor$placement(Map<String,IEntity> actors,Anchor anchor,Matrix4f fallback,double cx,double cy,double cz,
        float transition,int depth,boolean full,FormFrameCache cache,CallbackInfoReturnable<Pair<Matrix4f,Float>> ci) {
        if(BindingResolver.nativeQuery())return;
        BindingScenes.Slot owner=BindingScenes.anchorOwner(anchor);
        if(owner==null || owner.parent!=null || !owner.scene.enabled())return;
        try(var request=BindingResolver.request(owner.scene,transition)) {
            Matrix4f parent=request.session.rootParent(owner,full);
            parent.m30(parent.m30()-(float)cx).m31(parent.m31()-(float)cy).m32(parent.m32()-(float)cz);
            Anchor a=AnchorReferences.evaluated(anchor);
            float opacity=a==null?1:opacity(owner,a);
            if(a!=null && a.previous!=null)opacity=opacity(owner,a.previous)*(1-a.x)+opacity*a.x;
            if(request.session.blockedHeader(owner))opacity=1;
            ci.setReturnValue(new Pair<>(parent,Math.max(0,Math.min(1,opacity))));
        }catch(IllegalArgumentException ignored){/* Fall through to BBS's own placement. */}
    }
    private static float opacity(BindingScenes.Slot owner,Anchor value){return value.hasTarget()&&owner.scene.root(value.replay)!=null?0:1;}
}
