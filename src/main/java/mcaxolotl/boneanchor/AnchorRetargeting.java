package mcaxolotl.boneanchor;

import java.util.List;
import mchorse.bbs_mod.forms.forms.utils.Anchor;

/** Rewrites Anchor endpoints when a BodyPart subtree moves to another Replay root. */
public final class AnchorRetargeting {
    public static void retarget(Anchor anchor, String sourceReplayId, String sourcePrefix, String targetReplayId) {
        if (anchor == null || sourceReplayId == null || sourcePrefix == null || sourcePrefix.isEmpty() || targetReplayId == null) return;

        List<String> prefix = List.of(sourcePrefix.split("/"));
        retargetEndpoint(anchor, sourceReplayId, sourcePrefix, prefix, targetReplayId);
        if (anchor.previous != null) {
            retargetEndpoint(anchor.previous, sourceReplayId, sourcePrefix, prefix, targetReplayId);
        }
    }

    private static void retargetEndpoint(Anchor anchor, String sourceReplayId, String sourcePrefix, List<String> prefix, String targetReplayId) {
        if (!sourceReplayId.equals(anchor.replay)) return;

        if (anchor instanceof BindingAnchor binding && binding.structured()) {
            if (!startsWith(binding.targetPath, prefix)) return;
            binding.target(binding.targetPath.subList(prefix.size(), binding.targetPath.size()), binding.targetBone);
            binding.replay = targetReplayId;
            return;
        }

        String rest = retargetPath(anchor.attachment, sourcePrefix);
        if (rest == null) return;
        anchor.attachment = rest;
        anchor.replay = targetReplayId;
    }

    private static boolean startsWith(List<String> path, List<String> prefix) {
        return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
    }

    private static String retargetPath(String path, String prefix) {
        if (path == null) return null;
        if (path.equals(prefix)) return "";
        return path.startsWith(prefix + "/") ? path.substring(prefix.length() + 1) : null;
    }

    private AnchorRetargeting() {}
}
