package mcaxolotl.boneanchor.compat.mixin;

import mcaxolotl.boneanchor.BoneAnchors;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Names with a slash must retain their complete identity in filters and track styles. */
@Mixin(value = TrackDescriptor.class, remap = false)
public abstract class AnchorTrackIdentityMixin {
    @Inject(method = "filterKey", at = @At("HEAD"), cancellable = true)
    private void boneAnchor$rawIdentity(CallbackInfoReturnable<String> ci) {
        var track = (TrackDescriptor)(Object)this;
        if (track.kind() == TrackKind.PROPERTY && BoneAnchors.isKey(track.id().subject())) ci.setReturnValue(track.id().subject());
    }
}
