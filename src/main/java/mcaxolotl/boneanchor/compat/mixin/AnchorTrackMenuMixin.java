package mcaxolotl.boneanchor.compat.mixin;

import java.util.function.Consumer;
import mcaxolotl.boneanchor.client.BoneAnchorClient;
import mcaxolotl.boneanchor.BoneAnchors;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIReplaysEditorUtils.class, remap = false)
public abstract class AnchorTrackMenuMixin {
    @Inject(method = "addOverlayTrackAction", at = @At("HEAD"))
    private static void boneAnchor$filter(ContextMenuManager menu, UIKeyframeSheet sheet, Consumer<TrackId> refresh, CallbackInfo ci) {
        if (sheet == null || sheet.descriptor == null || !(sheet.descriptor.owner() instanceof ModelForm)) return;
        TrackId id = sheet.descriptor.id();
        if (!id.formPath().isEmpty()) return;
        if (id.kind() != TrackKind.PROPERTY || !(id.subject().equals("anchor") || BoneAnchors.isKey(id.subject()))) return;
        menu.action(Icons.LINK, mchorse.bbs_mod.l10n.L10n.lang("bbs_bone_anchor.bound_only"), BoneAnchorClient.boundOnly(), () -> {
            BoneAnchorClient.setBoundOnly(!BoneAnchorClient.boundOnly());
            refresh.accept(TrackId.property("", "anchor"));
        });
    }
}
