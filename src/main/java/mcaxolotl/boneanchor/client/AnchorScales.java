package mcaxolotl.boneanchor.client;

import java.util.Map;
import java.util.WeakHashMap;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import org.joml.Vector3f;

/** Addon-owned evaluated scale; never writes BBS's read-only animation channels. */
public final class AnchorScales {
    private static final Map<ModelGroup, Vector3f> VALUES = new WeakHashMap<>();
    public static void set(ModelGroup group, Vector3f scale) { VALUES.put(group, new Vector3f(scale)); }
    public static Vector3f get(ModelGroup group) { return VALUES.get(group); }
    public static void clear(Model model) { for (ModelGroup group : model.getAllGroups()) VALUES.remove(group); }
    public static long key(IModel model) {
        long hash = 0;
        if (model instanceof Model cubic) for (ModelGroup group : cubic.getOrderedGroups()) {
            Vector3f scale = VALUES.get(group);
            if (scale != null) hash = hash * 31 + group.id.hashCode() * 31L + scale.hashCode();
        }
        return hash;
    }
    private AnchorScales() {}
}
