package mcaxolotl.boneanchor.client;

import java.util.*;
import mcaxolotl.boneanchor.*;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

/** Stable instance addresses; resource names never identify a placed model. */
public final class BindingScenes {
    private static final Set<Scene> SCENES = Collections.newSetFromMap(new IdentityHashMap<>());
    public static final class Slot {
        public final Scene scene;
        public final String actor;
        public final List<String> path;
        public Form form;
        public IEntity entity;
        public Slot parent;
        public BodyPart part;
        public final List<Slot> children = new ArrayList<>();
        Slot(Scene scene,String actor,List<String> path) { this.scene=scene; this.actor=actor; this.path=List.copyOf(path); }
        public String address() { return actor + (path.isEmpty()?"":"/"+String.join("/",path)); }
        public String label(String bone) { return address() + (bone==null?"/anchor":"/bone["+bone+"]"); }
    }
    public static final class Scene {
        public final BaseFilmController controller;
        public final Map<String,Slot> slots = new LinkedHashMap<>();
        public final Map<Form,Slot> byForm = new IdentityHashMap<>();
        private boolean enabled;
        Scene(BaseFilmController controller) { this.controller=controller; }
        public void refresh() {
            Set<String> seen = new HashSet<>(); byForm.clear(); enabled = false;
            for (var actor : controller.getEntities().entrySet())
                walk(actor.getKey(),List.of(),actor.getValue().getForm(),actor.getValue(),null,null,seen,0);
            if(controller.film!=null)for(var replay:controller.film.replays.getList())
                for(var id:replay.properties.tracks.keySet())
                    enabled |= id.kind()==mchorse.bbs_mod.film.replays.tracks.TrackKind.PROPERTY && id.formPath().isEmpty() && BoneAnchors.isKey(id.subject());
            for (String key : new ArrayList<>(slots.keySet())) if (!seen.contains(key)) {
                RigInstances.release(slots.remove(key).form);
            }
            if (!enabled) for (Slot slot : slots.values()) RigInstances.release(slot.form);
        }
        private Slot walk(String actor,List<String> path,Form form,IEntity entity,Slot parent,BodyPart part,Set<String> seen,int depth) {
            if(form==null || depth>64) return null;
            String key=actor+"/"+String.join("/",path); seen.add(key);
            Slot slot=slots.computeIfAbsent(key,k->new Slot(this,actor,path));
            if(slot.form!=null && slot.form!=form) RigInstances.release(slot.form);
            slot.form=form; slot.entity=entity; slot.parent=parent; slot.part=part; slot.children.clear(); byForm.put(form,slot);
            enabled |= customBinding(form,parent!=null) || definitions(form,parent!=null);
            for(BodyPart child : form.parts.getAllTyped()) {
                var childPath=new ArrayList<>(path); childPath.add(child.getId());
                Slot nested=walk(actor,childPath,child.getForm(),child.getRenderEntity(entity),slot,child,seen,depth+1);
                if(nested!=null)slot.children.add(nested);
            }
            return slot;
        }
        public boolean enabled() { return enabled; }
        public Slot root(String actor) { return slots.get(actor+"/"); }
        public Slot slot(String actor,List<String> path) { return slots.get(actor+"/"+String.join("/",path)); }
    }
    public static void remember(BaseFilmController controller) {
        for(Scene s:SCENES) if(s.controller==controller) { s.refresh(); return; }
        Scene scene=new Scene(controller);SCENES.add(scene);scene.refresh();
    }
    public static Scene scene(BaseFilmController controller) {
        for(Scene scene:SCENES)if(scene.controller==controller){scene.refresh();return scene;}
        remember(controller);for(Scene scene:SCENES)if(scene.controller==controller)return scene;
        throw new IllegalStateException("Film scene unavailable");
    }
    public static void forget(BaseFilmController controller) {
        for(Scene scene:new ArrayList<>(SCENES)) if(scene.controller==controller) {
            for(Slot slot:scene.slots.values())RigInstances.release(slot.form);
            SCENES.remove(scene);
        }
    }
    static Slot find(Form form) {
        var current=BindingResolver.current();
        if(current!=null) {
            Slot slot=current.scene.byForm.get(form);if(slot!=null)return slot;
        }
        for(Scene scene:SCENES) {
            scene.refresh(); Slot slot=scene.byForm.get(form); if(slot!=null)return slot;
        }
        return null;
    }
    private static boolean customBinding(Form form,boolean nested) {
        if(nested)return false;
        for(var value:form.getAll())if(BoneAnchors.isKey(value.getId()) && value instanceof BaseValueBasic<?> basic
            && basic.get() instanceof mchorse.bbs_mod.forms.forms.utils.Anchor a && BoneAnchors.contributes(a))return true;
        return false;
    }
    private static boolean definitions(Form form,boolean nested) {
        if(nested)return false;
        for(var state:form.states.getAllTyped())for(var id:state.properties.tracks.keySet())
            if(id.kind()==mchorse.bbs_mod.film.replays.tracks.TrackKind.PROPERTY && id.formPath().isEmpty() && BoneAnchors.isKey(id.subject()))return true;
        return false;
    }
    static boolean subtreeBinding(Form form) {
        Slot slot=find(form);
        if(slot==null || !slot.scene.enabled())return false;
        return subtreeBinding(slot);
    }
    private static boolean subtreeBinding(Slot slot) {
        if(customBinding(slot.form,slot.parent!=null) || definitions(slot.form,slot.parent!=null))return true;
        return false;
    }
    public static Slot anchorOwner(mchorse.bbs_mod.forms.forms.utils.Anchor anchor) {
        var current=BindingResolver.current();
        if(current!=null)for(Slot slot:current.scene.slots.values())if(slot.form.anchor.get()==anchor)return slot;
        for(Scene scene:SCENES)for(Slot slot:scene.slots.values())if(slot.form.anchor.get()==anchor)return slot;
        return null;
    }
    private BindingScenes(){}
}
