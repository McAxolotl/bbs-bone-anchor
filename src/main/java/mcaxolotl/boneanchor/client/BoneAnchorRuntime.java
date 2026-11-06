package mcaxolotl.boneanchor.client;

import java.util.*;
import mchorse.bbs_mod.api.client.render.RenderAttachment;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;

/** Compatibility entry points; scene identity and solving live in their own components. */
public final class BoneAnchorRuntime {
    private static final Set<Model> BOUND=Collections.newSetFromMap(new WeakHashMap<>());
    private static final RenderAttachment<Float> SAMPLES=new RenderAttachment<>();
    public static void sample(Form form,float transition){var renderer=FormUtilsClient.getRenderer(form);if(renderer!=null)SAMPLES.set(renderer,transition);}
    static float transition(Form form){var renderer=FormUtilsClient.getRenderer(form);Float t=renderer==null?null:SAMPLES.get(renderer);return t==null?0:t;}
    public static void remember(BaseFilmController film){BindingScenes.remember(film);}
    public static void forget(BaseFilmController film){BindingScenes.forget(film);}
    public static boolean intercept(ModelInstance instance,Map<String,org.joml.Vector3f> targets,Map<String,org.joml.Vector3f> poles) {
        if(!(instance.form instanceof ModelForm form))return false;
        Float transition=SAMPLES.get(FormUtilsClient.getRenderer(form));
        return BindingResolver.intercept(instance,targets,poles,transition==null?0:transition);
    }
    public static boolean requiresFreshMatrices(Form form){return !BindingResolver.nativeQuery() && BindingScenes.subtreeBinding(form);}
    public static MatrixCache collect(Form form,float transition){return BindingResolver.collect(form,transition);}
    public static boolean hasBoundOffsets(mchorse.bbs_mod.cubic.IModel model){return BOUND.contains(model);}
    static void markBound(Model model){BOUND.add(model);}
    public static void clearEvaluation(Model model){BOUND.remove(model);AnchorScales.clear(model);}
    private BoneAnchorRuntime(){}
}
