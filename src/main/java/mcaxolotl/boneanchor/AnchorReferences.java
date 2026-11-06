package mcaxolotl.boneanchor;

import java.util.List;
import java.util.Objects;
import mchorse.bbs_mod.forms.forms.utils.Anchor;

/** Match FilmMatrices' endpoint selection before validating or walking dependencies. */
public final class AnchorReferences {
    public static Anchor evaluated(Anchor anchor) {
        if (anchor == null || !Float.isFinite(anchor.x)) return null;
        if (anchor.previous == null || Objects.equals(anchor, anchor.previous) || anchor.x == 1F) return anchor.copy();
        if (anchor.x == 0F) return anchor.previous.copy();
        return anchor;
    }

    public static List<Anchor> endpoints(Anchor anchor) {
        Anchor evaluated = evaluated(anchor);
        if (evaluated == null) return List.of();
        return evaluated.previous == null ? List.of(evaluated) : List.of(evaluated, evaluated.previous);
    }

    private AnchorReferences() {}
}
