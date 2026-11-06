package mcaxolotl.boneanchor.client;

import mcaxolotl.boneanchor.*;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.*;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
public final class BoneAnchorClient implements BBSAddonMod {
    private static boolean boundOnly;
    public static boolean boundOnly() { return boundOnly; }
    public static void setBoundOnly(boolean value) { boundOnly = value; }
    @Subscribe public void languages(RegisterL10nEvent event) {
        event.l10n.registerOne(language -> mchorse.bbs_mod.resources.Link.create("bbs_bone_anchor:strings/" + language + ".json"));
    }
    @Subscribe public void editors(RegisterKeyframeEditorsEvent event) { event.register(BoneAnchors.FACTORY, UIAnchorKeyframeFactory::new); }
    @Subscribe public void ready(BBSClientReadyEvent event) {
        FilmEvents.CREATED.register(BoneAnchorRuntime::remember);
        FilmEvents.SHUTDOWN.register(BoneAnchorRuntime::forget);
        FormPoseEvents.PARENT_FRAME.register((form, entity, parent, path, transition) -> {
            BoneAnchorRuntime.sample(form, transition);
        });
        FormPoseEvents.ACTOR_BEFORE.register(context -> {
            if (context.entity != null && context.entity.getForm()!=null)
                BoneAnchorRuntime.sample(context.entity.getForm(), context.transition);
        });
        FormRenderEvents.BEFORE.register(BindingRenderHooks::before);
        FormRenderEvents.AFTER.register(BindingRenderHooks::after);
        FormPoseEvents.PIVOT_OFFSETS.register(BoneAnchorRuntime::hasBoundOffsets);
    }
}
