package mcaxolotl.boneanchor;

import java.util.Set;
import java.util.HashSet;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.utils.keyframes.factories.AnchorKeyframeFactory;

public final class BoneAnchors {
    private static final String PREFIX = "anchor:";
    public static final String FACTORY_ID = "anchor:bone";
    public static final AnchorKeyframeFactory FACTORY = new AnchorKeyframeFactory() {
        @Override public Anchor fromData(mchorse.bbs_mod.data.types.BaseType data) {
            BindingAnchor anchor=new BindingAnchor();if(data.isMap())anchor.fromData(data.asMap());return anchor;
        }
        @Override public Anchor copy(Anchor value) {
            BindingAnchor anchor=BindingAnchor.of(value);anchor.x=value.x;
            anchor.previous=value.previous==null?null:BindingAnchor.of(value.previous);return anchor;
        }
        @Override public Anchor createEmpty() {
            return BindingAnchor.of(super.createEmpty());
        }
    };
    public static boolean contributes(Anchor anchor) {
        for(Anchor endpoint:AnchorReferences.endpoints(anchor))
            if(endpoint.hasTarget() || !endpoint.transform.isDefault())return true;
        return false;
    }
    public static String key(String bone) {
        if (bone == null || bone.isEmpty()) throw new IllegalArgumentException("Bone name must not be empty");
        return PREFIX + bone;
    }
    public static String bone(String key) {
        return isKey(key) ? key.substring(PREFIX.length()) : "";
    }
    public static boolean isKey(String key) {
        return key != null && key.startsWith(PREFIX) && key.length() > PREFIX.length();
    }
    public static void ensure(Form form, String key) {
        if (form instanceof ModelForm && mchorse.bbs_mod.forms.FormUtils.getPath(form).isEmpty() && isKey(key) && form.get(key) == null) {
            form.add(new BaseKeyframeFactoryValue<Anchor>(key, FACTORY, FACTORY.createEmpty()));
        }
    }
    /** Keep matching property objects (undo/UI may hold them), drop stale values absent in a replacement. */
    public static void retain(Form form, Set<String> keys) {
        for (var value : form.getAll()) if (isKey(value.getId())) {
            if (value instanceof BaseValueBasic<?> basic) basic.setRuntimeValue(null);
            if (!keys.contains(value.getId())) form.remove(value);
        }
    }

    public static void prepareCopy(Form form, BaseValueGroup source) {
        if (source == form) return;
        Set<String> keys = new HashSet<>();
        for (var value : source.getAll()) if (isKey(value.getId())) keys.add(value.getId());
        retain(form, keys);
        keys.forEach(key -> ensure(form, key));
    }

    /** UI needs every bone as an editable row, but untouched defaults must not be written into films. */
    public static void stripDefaults(Form form, MapType data) {
        for (var value : form.getAll()) if (isKey(value.getId()) && value instanceof BaseValueBasic<?> basic && basic.isDefault())
            data.remove(value.getId());
    }

    private BoneAnchors() {}
}
