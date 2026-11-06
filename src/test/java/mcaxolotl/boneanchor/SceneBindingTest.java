package mcaxolotl.boneanchor;

import java.util.*;
import mcaxolotl.boneanchor.client.*;
import mchorse.bbs_mod.cubic.*;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.film.*;
import mchorse.bbs_mod.forms.entities.*;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.forms.renderers.utils.*;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.lang.Math;

public final class SceneBindingTest {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static void near(Matrix4f actual,Matrix4f expected,String message) {
        count++;if(actual==null || !actual.isFinite() || !actual.equals(expected,2e-4f))throw new AssertionError(message+"\n"+actual+"\n!=\n"+expected);
    }
    private static void point(Matrix4f actual,Matrix4f expected,String message) {
        count++;if(actual==null || !actual.isFinite() || actual.getTranslation(new Vector3f()).distance(expected.getTranslation(new Vector3f()))>2e-4f)
            throw new AssertionError(message+": "+(actual==null?null:actual.getTranslation(new Vector3f()))+" != "+expected.getTranslation(new Vector3f()));
    }
    public static int run() throws Exception {
        count=0;

        var bonePanel=mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory.createPanel(
            new mchorse.bbs_mod.utils.keyframes.Keyframe<Anchor>("0",BoneAnchors.FACTORY,0,BoneAnchors.FACTORY.createEmpty()),null);
        var wholePanel=mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory.createPanel(
            new mchorse.bbs_mod.utils.keyframes.Keyframe<Anchor>("0",mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories.ANCHOR,0,new Anchor()),null);
        check(bonePanel.getClass()==wholePanel.getClass(),"Bone and overall Anchor use the same native editor class");
        check(bonePanel instanceof mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory,"Bone Anchor uses native panel");
        var nativeDefault=mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories.ANCHOR.createEmpty();
        var boneDefault=BoneAnchors.FACTORY.createEmpty();
        check(boneDefault.inheritPosition==nativeDefault.inheritPosition && boneDefault.inheritRotation==nativeDefault.inheritRotation && boneDefault.inheritScale==nativeDefault.inheritScale,
            "New bone Anchor defaults match native Anchor inheritance");
        try(Fixture f=new Fixture(false)) {
            BindingScenes.Slot prop=f.scene.byForm.get(f.prop);
            var humanInstance=f.humanRenderer.getModel();var otherInstance=f.otherRenderer.getModel();
            check(humanInstance!=otherInstance,"Same resource gets distinct ModelInstances");
            check(humanInstance.model!=otherInstance.model,"Same resource gets distinct mutable rigs");
            check(((Model)humanInstance.model).getGroup("right_hand")!=((Model)otherInstance.model).getGroup("right_hand"),"Bone state is per instance");
            check(humanInstance.animations!=otherInstance.animations,"Animation parsers/runtime are per instance");
            Matrix4f defaultProp=f.nativeBone(f.humanRenderer,f.humanEntity,"left_hand")
                .mul(f.part.transform.get().createMatrix()).mul(f.prop.transform.get().createMatrix()).scale(f.propRenderer.getModel().getScale());
            Matrix4f expectedController=new Matrix4f(defaultProp).rotateY((float)Math.PI)
                .translate(0,.65f/16f,0).rotateY((float)Math.PI);
            MatrixCache cache=BindingResolver.collect(f.human,0);
            point(f.world(cache,prop.path,"controller"),expectedController,"Attachment's stored bone binding is ignored");
            f.propRenderer.evaluateChannels(f.part.getRenderEntity(f.humanEntity),0);
            ModelInstance nativeProp=f.propRenderer.getModel();nativeProp.form=f.prop;
            mchorse.bbs_mod.cubic.ik.ModelIKRuntime.apply(nativeProp,null,null);
            check(((Model)nativeProp.model).getGroup("controller").offset==null,"Native IK path does not execute attachment Anchor");
            f.prop.anchor.set(target("other",List.of(),"left_hand"));
            near(f.world(BindingResolver.collect(f.human,0),prop.path,null),defaultProp,"Attachment placement remains native BodyPart behavior");
            var properties=new mchorse.bbs_mod.film.replays.FormProperties("properties");
            var catalog=mchorse.bbs_mod.film.replays.tracks.TrackCatalog.forPart(f.human,properties,String.join("/",prop.path));
            check(catalog.stream().noneMatch(t->t.kind()==mchorse.bbs_mod.film.replays.tracks.TrackKind.PROPERTY &&
                (t.id().subject().equals("anchor")||BoneAnchors.isKey(t.id().subject()))),"Attachment offers no overall or bone Anchor track");
            check(f.prop.getBasic("anchor:new_bone")==null,"Attachment lookup does not create new Anchor properties");
            var localCatalog=mchorse.bbs_mod.film.replays.tracks.TrackCatalog.of(f.prop);
            check(localCatalog.stream().noneMatch(t->t.kind()==mchorse.bbs_mod.film.replays.tracks.TrackKind.PROPERTY &&
                (t.id().subject().equals("anchor")||BoneAnchors.isKey(t.id().subject()))),"Selecting an attachment as a local editor root does not expose Anchor tracks");

            // Root actors can still target a bone inside a native attachment.
            bind(f.human,"aux",target("hero",prop.path,"controller"));
            cache=BindingResolver.collect(f.human,0);
            point(f.world(cache,List.of(),"aux"),expectedController,"Independent actor can still target attachment bone");
            BindingAnchor structured=target("hero",prop.path,"controller/原名");
            var saved=BoneAnchors.FACTORY.toData(structured).asMap();
            check(saved.has("target")&&!saved.has("attachment"),"Independent binding keeps structured raw names");
            Matrix4f before=f.world(cache,List.of(),"aux");
            @SuppressWarnings("unchecked") BaseValueBasic<Anchor> aux=(BaseValueBasic<Anchor>)f.human.getBasic(BoneAnchors.key("aux"));
            Anchor from=aux.get().copy();
            BindingAnchor next=target("other",List.of(),"left_hand");
            // The native adapter must measure from the edited keyframe, not a different live state.
            aux.set(next.copy());
            check(NativeAnchorEditing.rebase(f.scene.byForm.get(f.human),"aux",from,next),"Native keep-transform adapts to the edited keyframe");
            aux.set(from);
            bind(f.human,"aux",next);
            point(f.world(BindingResolver.collect(f.human,0),List.of(),"aux"),before,"Native keep-transform preserves the bone world position");

        }
        try(Fixture f=new Fixture(false)) {
            Model target=(Model)f.otherRenderer.getModel().model;
            ModelGroup left=target.getGroup("left_hand");
            left.initial.rotate.y=1f;left.current.copy(left.initial);
            var constraint=new mchorse.bbs_mod.cubic.constraints.BoneConstraint();
            constraint.limitY=true;constraint.minY=0f;constraint.maxY=0f;
            f.other.bones.getOrCreate("left_hand").constraints.set(constraint);
            f.other.bumpPoseVersion();mchorse.bbs_mod.forms.renderers.utils.RenderFrame.invalidate();
            Matrix4f constrained=f.world(BindingResolver.collect(f.human,0),List.of(),"aux");
            near(constrained,f.nativeBone(f.otherRenderer,f.otherEntity,"left_hand"),"Constraint applies without an enabled IK chain");
        }
        try(Fixture f=new Fixture(false)) {
            f.other.transform.get().scale.set(0);
            f.human.anchor.set(new Anchor("other","",true,true,true));
            MatrixCache matrices=new MatrixCache();
            check(BindingResolver.collectInto(f.human,0,new Matrix4f(),matrices,""),"Degenerate incoming frame falls back instead of throwing");
            for(var entry:matrices.entrySet()) check(entry.getValue().matrix().isFinite(),"Degenerate frame produced a non-finite matrix");
        }
        try(Fixture f=new Fixture(true)) {
            var prop=f.scene.byForm.get(f.prop);
            var properties=new mchorse.bbs_mod.film.replays.FormProperties("properties");
            String attachmentPath=String.join("/",prop.path);
            var catalog=mchorse.bbs_mod.film.replays.tracks.TrackCatalog.forPart(f.human,properties,attachmentPath);
            String catalogKinds=catalog.stream().map(t->t.kind()+":"+t.id().formPath()+":"+t.id().subject()).toList().toString();
            check(catalog.stream().anyMatch(t->t.kind()==mchorse.bbs_mod.film.replays.tracks.TrackKind.IK_CONTROLS ||
                t.kind()==mchorse.bbs_mod.film.replays.tracks.TrackKind.BONE),
                "Native attachment pose/IK tracks are preserved for path '"+attachmentPath+"': "+catalogKinds);
        }
        ModelForm stateChild=new ModelForm();
        ModelForm stateParent=new ModelForm(){
            @Override public void applyStates(float transition){stateChild.lighting.setRuntimeValue(.4f);}
            @Override public void unapplyStates(){stateChild.lighting.setRuntimeValue(null);}
        };
        BodyPart statePart=new BodyPart("");statePart.setForm(stateChild);stateParent.parts.addBodyPart(statePart);
        StubEntity stateEntity=new StubEntity();stateEntity.setForm(stateParent);
        BaseFilmController stateFilm=new BaseFilmController(null){public Map<String,Integer> getActors(){return Map.of();}public int getTick(){return 0;}};
        stateFilm.entities.put("state",stateEntity);BoneAnchorRuntime.remember(stateFilm);
        try {
            var slot=BindingScenes.scene(stateFilm).byForm.get(stateChild);
            try(var values=StateValues.of(slot,0)){check(stateChild.lighting.getRuntimeValue()!=null,"Ancestor states apply to child query");}
            check(stateChild.lighting.getRuntimeValue()==null,"Null runtime values are restored after layered state queries");
        }finally{BoneAnchorRuntime.forget(stateFilm);}
        return count;
    }
    public static BindingAnchor target(String actor,List<String> path,String bone) {
        BindingAnchor a=new BindingAnchor();a.replay=actor;a.target(path,bone);return a;
    }
    @SuppressWarnings("unchecked") private static void bind(ModelForm form,String bone,Anchor target) {
        ((BaseValueBasic<Anchor>)form.getBasic(BoneAnchors.key(bone))).set(target);
    }
    private static ModelInstance asset(String id,boolean human,boolean ik) {
        MolangParser parser=new MolangParser();MolangHelper.registerVars(parser);Model model=new Model(parser);
        ModelGroup root=new ModelGroup("Root");model.topGroups.add(root);
        if(human) {
            root.children.add(new ModelGroup("aux"));
            ModelGroup left=new ModelGroup("left_hand"),right=new ModelGroup("right_hand");
            left.initial.translate.set(-8,8,0);right.initial.translate.set(8,8,0);root.children.add(left);
            if(ik) {
                ModelGroup shoulder=new ModelGroup("shoulder");shoulder.initial.translate.set(3,12,0);
                shoulder.children.add(right);root.children.add(shoulder);
                ModelGroup goal=new ModelGroup("right_goal");goal.initial.translate.set(10,9,3);root.children.add(goal);
            }else root.children.add(right);
        }else {
            ModelGroup ctrl=new ModelGroup("controller");ctrl.initial.translate.set(0,.65f,0);root.children.add(ctrl);
            root.children.add(new ModelGroup("decoration"));
        }
        model.initialize();model.resetPose();model.snapshotChannels();
        ModelInstance asset=new ModelInstance(id,model,new Animations(parser),null);asset.config.procedural.set(false);return asset;
    }
    private static final class Renderer extends ModelFormRenderer {
        final ModelInstance asset;
        Renderer(ModelForm form,ModelInstance asset){super(form);this.asset=asset;}
        @Override public ModelInstance getModel(){return RigInstances.view(getForm(),asset);}
        @Override public ModelInstance evaluateChannels(IEntity entity,float transition) {
            ModelInstance model=getModel();model.model.resetPose();model.model.applyPose(model.getDefaultPose());model.model.applyPose(getPose());
            model.model.snapshotChannels();model.clearChannels();model.form=getForm();return model;
        }
    }
    private static final class Fixture implements AutoCloseable {
        final ModelForm human=new ModelForm(),other=new ModelForm(),prop=new ModelForm();
        final StubEntity humanEntity=new StubEntity(),otherEntity=new StubEntity();
        final BodyPart part=new BodyPart("");
        final BaseFilmController controller=new BaseFilmController(null) {
            public Map<String,Integer> getActors(){return Map.of();}public int getTick(){return 0;}
        };
        final Renderer humanRenderer,otherRenderer,propRenderer;
        final BindingScenes.Scene scene;
        Fixture(boolean ik) {
            ModelInstance common=asset("same_person_resource",true,ik);
            humanRenderer=new Renderer(human,common);otherRenderer=new Renderer(other,common);propRenderer=new Renderer(prop,asset("attachment_resource",false,false));
            human.setRenderer(humanRenderer);other.setRenderer(otherRenderer);prop.setRenderer(propRenderer);
            human.transform.get().translate.set(3,2,1);human.transform.get().scale.set(1.5f);human.transform.get().rotate.y=.4f;
            other.transform.get().translate.set(-4,1,2);
            prop.transform.get().translate.set(.3f,.1f,.2f);part.transform.get().translate.set(.2f,0,.1f);
            // Simulate an independent model with saved Anchor data subsequently attached to a person.
            bind(prop,"controller",target("hero",List.of(),"right_hand"));
            part.bone.set("left_hand");part.setForm(prop);human.parts.addBodyPart(part);
            if(ik){var chain=human.bones.getOrCreate("right_hand");chain.ikTarget.set("right_goal");chain.ikChainLength.set(2);}
            bind(human,"aux",target("other",List.of(),"left_hand"));
            humanEntity.setForm(human);otherEntity.setForm(other);
            humanEntity.setPosition(4,2,3);humanEntity.setPrevX(4);humanEntity.setPrevY(2);humanEntity.setPrevZ(3);
            otherEntity.setPosition(-2,0,0);otherEntity.setPrevX(-2);
            controller.entities.put("hero",humanEntity);controller.entities.put("other",otherEntity);
            BoneAnchorRuntime.remember(controller);scene=BindingScenes.scene(controller);
            RenderFrame.invalidate();
        }
        Matrix4f actor() {return FilmMatrices.getMatrixForRenderWithRotation(humanEntity,0,0,0,0);}
        Matrix4f world(MatrixCache cache,List<String> path,String bone) {
            String key=String.join("/",path)+(bone==null?"":(path.isEmpty()?"":"/")+bone);
            Matrix4f local=cache.get(key).matrix();return local==null?null:actor().mul(local);
        }
        Matrix4f nativeBone(Renderer renderer,StubEntity entity,String bone) {
            ModelInstance instance=renderer.evaluateChannels(entity,0);
            mchorse.bbs_mod.cubic.ik.ModelIKRuntime.apply(instance,null,null);
            mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime.apply(instance);
            MatrixCache own=new MatrixCache();instance.captureMatrices(own);
            return FilmMatrices.getMatrixForRenderWithRotation(entity,0,0,0,0).mul(renderer.createEvaluatedTransform(0).createMatrix())
                .scale(instance.getScale()).rotateY((float)Math.PI).mul(own.get(bone).matrix());
        }
        public void close(){BoneAnchorRuntime.forget(controller);RenderFrame.invalidate();}
    }
    private SceneBindingTest(){}
}
