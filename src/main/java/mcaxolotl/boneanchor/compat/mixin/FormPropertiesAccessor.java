package mcaxolotl.boneanchor.compat.mixin;

import java.util.List;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.FormProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes unknown/foreign tracks so body-part conversion can move them with known tracks. */
@Mixin(FormProperties.class)
public interface FormPropertiesAccessor {
    @Accessor("foreignTracks")
    List<MapType> boneAnchor$foreignTracks();
}
