package mcaxolotl.boneanchor.client;

import java.util.*;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.*;

/** Apply state layers for a matrix query and restore the exact runtime inputs afterwards. */
public final class StateValues implements AutoCloseable {
    private static final ThreadLocal<Set<Form>> APPLIED=ThreadLocal.withInitial(()->Collections.newSetFromMap(new IdentityHashMap<>()));
    private final List<Form> applied=new ArrayList<>();
    private final Map<BaseValueBasic<?>,Object> values=new IdentityHashMap<>();
    private StateValues(BindingScenes.Slot slot,float transition) {
        List<BindingScenes.Slot> ancestors=new ArrayList<>();for(var current=slot;current!=null;current=current.parent)ancestors.add(current);
        Collections.reverse(ancestors);
        try {
            for(var owner:ancestors)if(!BindingRenderHooks.statesApplied(owner.form) && APPLIED.get().add(owner.form)) {
                capture(owner.form);applied.add(owner.form);owner.form.applyStates(transition);
            }
        }catch(RuntimeException error){close();throw error;}
    }
    public static StateValues of(BindingScenes.Slot slot,float transition){return new StateValues(slot,transition);}
    private void capture(BaseValue value) {
        if(value instanceof BaseValueBasic<?> basic && !values.containsKey(basic))values.put(basic,basic.getRuntimeValue());
        if(value instanceof BaseValueGroup group)for(BaseValue child:group.getAll())capture(child);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void restore(BaseValueBasic key,Object value){key.setRuntimeValue(value);}
    public void close() {
        for(int i=applied.size()-1;i>=0;i--){Form form=applied.get(i);form.unapplyStates();APPLIED.get().remove(form);}
        values.forEach(StateValues::restore);applied.clear();
        if(APPLIED.get().isEmpty())APPLIED.remove();
    }
    private StateValues(){throw new UnsupportedOperationException();}
}
