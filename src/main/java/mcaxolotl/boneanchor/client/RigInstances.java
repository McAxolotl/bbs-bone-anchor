package mcaxolotl.boneanchor.client;

import java.util.*;
import java.lang.ref.WeakReference;
import mchorse.bbs_mod.api.client.render.RenderAttachment;
import mchorse.bbs_mod.cubic.*;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.model.ModelSetupQueue;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.math.molang.MolangParser;

/** A private mutable rig, parser, animation runtime and GPU lifetime for each placed form. */
public final class RigInstances {
    private static final RenderAttachment<Map<ModelInstance,View>> VIEWS=new RenderAttachment<>();
    private static final Map<ModelInstance,Revision> REVISIONS=new WeakHashMap<>();
    private static final class Revision { long value; }
    private record View(ModelInstance asset,IModel source,long revision,ModelInstance instance){}
    private static Revision revision(ModelInstance asset) {
        Revision existing=REVISIONS.get(asset);if(existing!=null)return existing;
        Revision value=new Revision();REVISIONS.put(asset,value);
        WeakReference<Revision> weak=new WeakReference<>(value);
        asset.config.postCallback((v,f)->{Revision r=weak.get();if(r!=null)r.value++;});
        return value;
    }
    public static void changed(ModelInstance asset) { Revision r=REVISIONS.get(asset);if(r!=null)r.value++; }
    public static void changed(Model model) {
        for(var entry:REVISIONS.entrySet())if(entry.getKey().model==model)entry.getValue().value++;
    }
    public static ModelInstance view(ModelForm form,ModelInstance asset) {
        if(asset==null || !(asset.model instanceof Model) || asset.cemAnimation!=null)return asset;
        BindingScenes.Slot slot=BindingScenes.find(form);
        if(slot==null || !slot.scene.enabled())return asset;
        var renderer=FormUtilsClient.getRenderer(form);if(renderer==null)return asset;
        long rev=revision(asset).value;
        Map<ModelInstance,View> views=VIEWS.get(renderer);
        if(views==null){views=new LinkedHashMap<>(8,.75f,true);VIEWS.set(renderer,views);}
        View old=views.get(asset);
        if(old!=null && old.source==asset.model && old.revision==rev)return old.instance;
        if(old!=null)dispose(old.instance);
        ModelInstance clone=copy(asset);clone.form=form;views.put(asset,new View(asset,asset.model,rev,clone));
        while(views.size()>4){var first=views.entrySet().iterator();View expired=first.next().getValue();first.remove();dispose(expired.instance);}
        return clone;
    }
    private static ModelInstance copy(ModelInstance asset) {
        Model original=(Model)asset.model;
        MolangParser parser=new MolangParser();MolangHelper.registerVars(parser);
        Model model=new Model(parser);model.fromData(original.toData());model.initialize();
        for(ModelGroup group:model.getAllGroups()) {
            ModelGroup old=original.getGroup(group.id);group.initial.copy(old.initial);group.visible=old.visible;
        }
        model.resetPose();model.snapshotChannels();
        Animations animations=null;
        if(asset.animations!=null) { animations=new Animations(parser);animations.fromData(asset.animations.toData()); }
        ModelInstance clone=new ModelInstance(asset.id,model,animations,asset.baseTexture);
        clone.applyConfig(asset.config.toData().asMap());clone.invalidateWelds();
        clone.materialTextures.putAll(asset.materialTextures);clone.materials.addAll(asset.materials);
        clone.warnings.addAll(asset.warnings);clone.setModelFile(asset.getModelFile());clone.setup();return clone;
    }
    static void release(Form form) {
        if(form!=null && form.getRenderer() instanceof mchorse.bbs_mod.forms.renderers.FormRenderer<?> renderer) {
            Map<ModelInstance,View> views=VIEWS.get(renderer);
            if(views!=null){VIEWS.remove(renderer);for(View view:views.values())dispose(view.instance);}
        }
    }
    private static void dispose(ModelInstance instance) {
        if(instance.model instanceof Model model)BoneAnchorRuntime.clearEvaluation(model);
        ModelSetupQueue.add(instance::delete);
    }
    private RigInstances(){}
}
