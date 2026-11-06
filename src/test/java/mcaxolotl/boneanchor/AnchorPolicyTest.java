package mcaxolotl.boneanchor;
import java.util.List;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

/** Pure endpoint/key regressions; runs without starting a game. */
public final class AnchorPolicyTest {
    private static int count;
    private static void check(boolean condition, String message) { count++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Anchor a = new Anchor("current", "hand", true, false, false);
        a.previous = new Anchor("previous", "hand", true, false, false);
        a.x = 1;
        check(AnchorReferences.endpoints(a).size() == 1, "Completed transition ignores old target");
        check(AnchorReferences.evaluated(a).replay.equals("current"), "Current endpoint at x=1");
        a.x = 0;
        check(AnchorReferences.evaluated(a).replay.equals("previous"), "Previous endpoint at x=0");
        a.x = .5f;
        check(AnchorReferences.endpoints(a).size() == 2, "Blend retains both live endpoints");
        a.x = 1.2f;
        check(AnchorReferences.endpoints(a).size() == 2, "Overshoot still needs both endpoints");
        a.x = Float.NaN;
        check(AnchorReferences.evaluated(a) == null, "Reject NaN interpolation");
        Anchor none = new Anchor(); none.previous = new Anchor("old", "", true, false, false); none.x = 1;
        check(AnchorReferences.endpoints(none).stream().noneMatch(Anchor::hasTarget), "Completed unbind drops previous dependency");
        check(none.previous.hasTarget(), "Endpoint selection never changes saved values");
        for (String bone : new String[]{"anchor", "中文.bone/path", "007", "arm:left", "a b", "a%2Fb"}) {
            check(BoneAnchors.key(bone).equals("anchor:" + bone), "Raw name is stored literally");
            check(BoneAnchors.bone(BoneAnchors.key(bone)).equals(bone), "Raw name round trip");
        }
        check(!BoneAnchors.isKey("anchor"), "Whole-model anchor is not a bone track");
        check(!BoneAnchors.isKey("anchor:"), "Reject empty name");
        check(BoneAnchors.bone("unrelated").isEmpty(), "Ignore foreign property id");
        BindingAnchor cleared = new BindingAnchor(); cleared.target(List.of(),null);
        check(!cleared.structured() && !cleared.toData().asMap().has("target"), "Cleared Anchor omits empty target metadata");
        ModelForm defaults = new ModelForm();
        String unused = BoneAnchors.key("unused");
        BoneAnchors.ensure(defaults,unused);
        MapType data = new MapType(); data.putString(unused,"present");
        BoneAnchors.stripDefaults(defaults,data);
        check(!data.has(unused), "Untouched bone Anchor is omitted from saved form data");
        @SuppressWarnings("unchecked") BaseValueBasic<Anchor> property = (BaseValueBasic<Anchor>) defaults.getBasic(unused);
        property.set(new Anchor("target","hand",true,true,true));
        data.putString(unused,"present"); BoneAnchors.stripDefaults(defaults,data);
        check(data.has(unused), "Authored bone Anchor remains saved");
        checkLocalization();
        System.out.println("Anchor policies: " + count + " checks passed");
    }
    private static void checkLocalization() throws Exception {
        List<String> languages = List.of(
            "ar_ar", "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "id_id", "ko_kr", "pl_pl",
            "pt_br", "pt_pt", "ru_ru", "th_th", "tr_tr", "uk_ua", "ur_pk", "vi_vn", "zh_cn", "zh_tw"
        );
        for (String language : languages) {
            String path = "assets/bbs_bone_anchor/assets/strings/" + language + ".json";
            try (InputStream input = AnchorPolicyTest.class.getClassLoader().getResourceAsStream(path)) {
                check(input != null, "Localization resource exists: " + language);
                if (input == null) continue;
                JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                check(json.has("bbs_bone_anchor.bound_only") && !json.get("bbs_bone_anchor.bound_only").getAsString().isBlank(),
                    "Localized bound-only menu action: " + language);
                check(!json.has("bbs_bone_anchor.config.timeline.bound_only"),
                    "Persistent settings localization removed: " + language);
            }
        }
    }

}
