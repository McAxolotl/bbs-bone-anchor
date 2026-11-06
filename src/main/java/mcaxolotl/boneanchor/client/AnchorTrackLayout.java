package mcaxolotl.boneanchor.client;

import mcaxolotl.boneanchor.*;

import java.util.*;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.film.replays.tracks.*;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/** Presentation only: never changes channels, bindings or the rig's hierarchy. */
public final class AnchorTrackLayout {
    private static boolean bound(TrackDescriptor track) {
        if (track.property() != null && (hasTarget(track.property().getOriginalValue()) || hasTarget(track.property().get()))) return true;
        if (track.channel() != null) for (Object item : track.channel().getList()) {
            if (item instanceof Keyframe<?> frame && hasTarget(frame.getValue())) return true;
        }
        return false;
    }
    private static boolean hasTarget(Object value) {
        return value instanceof Anchor a && (a.hasTarget() || a.previous != null && a.previous.hasTarget());
    }
    public static void apply(Form owner, Model model, List<TrackDescriptor> tracks, boolean boundOnly) {
        Map<String, TrackDescriptor> rows = new LinkedHashMap<>();
        for (var track : tracks) if (track.owner() == owner && BoneAnchors.isKey(track.id().subject()))
            rows.put(BoneAnchors.bone(track.id().subject()), track);
        if (rows.isEmpty()) return;
        List<String> order = new ArrayList<>();
        if (model != null) for (String bone : model.getGroupKeysInHierarchyOrder()) if (rows.containsKey(bone)) order.add(bone);
        // Preserve authored orphan tracks when an animated model asset changes or is unavailable.
        Set<String> ordered = new HashSet<>(order);
        for (String bone : rows.keySet()) if (ordered.add(bone)) order.add(bone);
        Set<String> shown = new HashSet<>();
        if (!boundOnly) shown.addAll(rows.keySet());
        else for (var entry : rows.entrySet()) if (bound(entry.getValue())) {
            String bone = entry.getKey();
            Set<String> visited = new HashSet<>();
            while (bone != null && visited.add(bone)) {
                if (rows.containsKey(bone)) shown.add(bone);
                bone = model == null ? null : model.getParentGroupKey(bone);
            }
        }
        tracks.removeIf(t -> t.owner() == owner && BoneAnchors.isKey(t.id().subject()));
        TrackId whole = TrackId.property("", "anchor");
        for (String bone : order) if (shown.contains(bone)) {
            String parent = model == null ? null : model.getParentGroupKey(bone);
            Set<String> visited = new HashSet<>();
            while (parent != null && !shown.contains(parent) && visited.add(parent)) parent = model.getParentGroupKey(parent);
            var track = rows.get(bone);
            TrackId parentId = parent == null || !shown.contains(parent) ? whole : TrackId.property("", BoneAnchors.key(parent));
            // Match Pose: the hierarchy and color carry the structure, without a repeated icon column.
            tracks.add(new TrackDescriptor(track.id(), track.channel(), owner, IKey.constant(bone), null,
                track.color(), track.property(), track.seed(), parentId));
        }
    }
    private AnchorTrackLayout() {}
}
