package mcaxolotl.boneanchor;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.BBSApi;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.events.RegisterKeyframeFactoriesEvent;
public final class BoneAnchorAddon implements BBSAddonMod {
    public static final String MOD_ID = "bbs_bone_anchor";
    @Subscribe public void sources(mchorse.bbs_mod.api.events.RegisterSourcePacksEvent event) {
        BBSApi.requireVersion(MOD_ID, 2);
        event.registerAddon(MOD_ID, BoneAnchorAddon.class);
    }
    @Subscribe public void factories(RegisterKeyframeFactoriesEvent event) {
        BBSApi.requireVersion(MOD_ID, 2);
        event.register(BoneAnchors.FACTORY_ID, BoneAnchors.FACTORY);
    }
}
