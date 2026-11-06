package mcaxolotl.boneanchor.client;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.*;

public final class BoneAnchorClient implements BBSAddonMod {
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
