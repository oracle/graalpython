package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.utilities.CyclicAssumption;

public abstract class ManagedPythonClass extends PythonObject implements AbstractPythonClass {

    private final String className;

    @CompilationFinal(dimensions = 1) private AbstractPythonClass[] baseClasses;
    @CompilationFinal(dimensions = 1) private AbstractPythonClass[] methodResolutionOrder;

    /**
     * This assumption will be invalidated whenever the mro changes.
     */
    private final CyclicAssumption lookupStableAssumption;
    /**
     * These assumptions will be invalidated whenever the value of the given slot changes. All
     * assumptions will be invalidated if the mro changes.
     */
    private final Map<String, List<Assumption>> attributesInMROFinalAssumptions = new HashMap<>();

    private final Set<AbstractPythonClass> subClasses = Collections.newSetFromMap(new WeakHashMap<AbstractPythonClass, Boolean>());
    private final Shape instanceShape;
    private final FlagsContainer flags;

    /** {@code true} if the MRO contains a native class. */
    private boolean needsNativeAllocation;
    @CompilationFinal private Object sulongType;

    @TruffleBoundary
    public ManagedPythonClass(LazyPythonClass typeClass, String name, Shape instanceShape, AbstractPythonClass... baseClasses) {
        super(typeClass, PythonLanguage.freshShape() /* do not inherit layout from the TypeClass */);
        this.className = name;

        if (baseClasses.length == 1 && baseClasses[0] == null) {
            this.baseClasses = new AbstractPythonClass[]{};
        } else {
            unsafeSetSuperClass(baseClasses);
        }

        this.flags = new FlagsContainer(getSuperClass());
        this.lookupStableAssumption = new CyclicAssumption(className);

        // Compute MRO
        computeMethodResolutionOrder();

        setAttribute(__NAME__, getBaseName(name));
        setAttribute(__QUALNAME__, className);
        setAttribute(__DOC__, PNone.NONE);
        // provide our instances with a fresh shape tree
        this.instanceShape = instanceShape;
    }

    private static String getBaseName(String qname) {
        int lastDot = qname.lastIndexOf('.');
        if (lastDot != -1) {
            return qname.substring(lastDot + 1);
        }
        return qname;
    }

    public Assumption getLookupStableAssumption() {
        return lookupStableAssumption.getAssumption();
    }

    public Assumption createAttributeInMROFinalAssumption(String name) {
        CompilerAsserts.neverPartOfCompilation();
        List<Assumption> attrAssumptions = attributesInMROFinalAssumptions.getOrDefault(name, null);
        if (attrAssumptions == null) {
            attrAssumptions = new ArrayList<>();
            attributesInMROFinalAssumptions.put(name, attrAssumptions);
        }

        Assumption assumption = Truffle.getRuntime().createAssumption(name.toString());
        attrAssumptions.add(assumption);
        return assumption;
    }

    public void addAttributeInMROFinalAssumption(String name, Assumption assumption) {
        CompilerAsserts.neverPartOfCompilation();
        List<Assumption> attrAssumptions = attributesInMROFinalAssumptions.getOrDefault(name, null);
        if (attrAssumptions == null) {
            attrAssumptions = new ArrayList<>();
            attributesInMROFinalAssumptions.put(name, attrAssumptions);
        }

        attrAssumptions.add(assumption);
    }

    @TruffleBoundary
    public void invalidateAttributeInMROFinalAssumptions(String name) {
        List<Assumption> assumptions = attributesInMROFinalAssumptions.getOrDefault(name, new ArrayList<>());
        if (!assumptions.isEmpty()) {
            String message = className + "." + name;
            for (Assumption assumption : assumptions) {
                assumption.invalidate(message);
            }
        }
    }

    /**
     * This method needs to be called if the mro changes. (currently not used)
     */
    public void lookupChanged() {
        CompilerAsserts.neverPartOfCompilation();
        for (List<Assumption> list : attributesInMROFinalAssumptions.values()) {
            for (Assumption assumption : list) {
                assumption.invalidate();
            }
        }
        lookupStableAssumption.invalidate();
        for (AbstractPythonClass subclass : getSubClasses()) {
            if (subclass != null) {
                subclass.lookupChanged();
            }
        }
    }

    public Shape getInstanceShape() {
        return instanceShape;
    }

    AbstractPythonClass getSuperClass() {
        return getBaseClasses().length > 0 ? getBaseClasses()[0] : null;
    }

    AbstractPythonClass[] getMethodResolutionOrder() {
        return methodResolutionOrder;
    }

    String getName() {
        return className;
    }

    private void computeMethodResolutionOrder() {
        CompilerAsserts.neverPartOfCompilation();

        AbstractPythonClass[] currentMRO = null;

        if (getBaseClasses().length == 0) {
            currentMRO = new AbstractPythonClass[]{this};
        } else if (getBaseClasses().length == 1) {
            AbstractPythonClass[] baseMRO = GetMroNode.doSlowPath(getBaseClasses()[0]);

            if (baseMRO == null) {
                currentMRO = new AbstractPythonClass[]{this};
            } else {
                currentMRO = new AbstractPythonClass[baseMRO.length + 1];
                System.arraycopy(baseMRO, 0, currentMRO, 1, baseMRO.length);
                currentMRO[0] = this;
            }
        } else {
            MROMergeState[] toMerge = new MROMergeState[getBaseClasses().length + 1];

            for (int i = 0; i < getBaseClasses().length; i++) {
                toMerge[i] = new MROMergeState();
                toMerge[i].mro = GetMroNode.doSlowPath(getBaseClasses()[i]);
            }

            toMerge[getBaseClasses().length] = new MROMergeState();
            toMerge[getBaseClasses().length].mro = getBaseClasses();
            ArrayList<AbstractPythonClass> mro = new ArrayList<>();
            mro.add(this);
            currentMRO = mergeMROs(toMerge, mro);
        }

        for (AbstractPythonClass cls : currentMRO) {
            if (cls instanceof PythonNativeClass) {
                needsNativeAllocation = true;
                break;
            }
        }

        methodResolutionOrder = currentMRO;
    }

    AbstractPythonClass[] mergeMROs(MROMergeState[] toMerge, List<AbstractPythonClass> mro) {
        int idx;
        scan: for (idx = 0; idx < toMerge.length; idx++) {
            if (toMerge[idx].isMerged()) {
                continue scan;
            }

            AbstractPythonClass candidate = toMerge[idx].getCandidate();
            for (MROMergeState mergee : toMerge) {
                if (mergee.pastnextContains(candidate)) {
                    continue scan;
                }
            }

            mro.add(candidate);

            for (MROMergeState element : toMerge) {
                element.noteMerged(candidate);
            }

            // restart scan
            idx = -1;
        }

        for (MROMergeState mergee : toMerge) {
            if (!mergee.isMerged()) {
                throw new IllegalStateException();
            }
        }

        return mro.toArray(new AbstractPythonClass[mro.size()]);
    }

    @Override
    @TruffleBoundary
    public void setAttribute(Object key, Object value) {
        if (key instanceof String) {
            invalidateAttributeInMROFinalAssumptions((String) key);
        }
        super.setAttribute(key, value);
    }

    /**
     * This method supports initialization and solves boot-order problems and should not normally be
     * used.
     */
    private void unsafeSetSuperClass(AbstractPythonClass... newBaseClasses) {
        CompilerAsserts.neverPartOfCompilation();
        // TODO: if this is used outside bootstrapping, it needs to call
        // computeMethodResolutionOrder for subclasses.

        assert getBaseClasses() == null || getBaseClasses().length == 0;
        this.baseClasses = newBaseClasses;

        for (AbstractPythonClass base : getBaseClasses()) {
            if (base != null) {
                GetSubclassesNode.doSlowPath(base).add(this);
            }
        }
        computeMethodResolutionOrder();
    }

    final Set<AbstractPythonClass> getSubClasses() {
        return subClasses;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("<class '%s'>", className);
    }

    public AbstractPythonClass[] getBaseClasses() {
        return baseClasses;
    }

    public void setFlags(long flags) {
        this.flags.setValue(flags);
    }

    FlagsContainer getFlagsContainer() {
        return flags;
    }

    /**
     * Flags are copied from the initial dominant base class. However, classes may already be
     * created before the C API was initialized, i.e., flags were not set.
     */
    static final class FlagsContainer {
        AbstractPythonClass initialDominantBase;
        long flags;

        public FlagsContainer(AbstractPythonClass superClass) {
            this.initialDominantBase = superClass;
        }

        private void setValue(long flags) {
            if (initialDominantBase != null) {
                initialDominantBase = null;
            }
            this.flags = flags;
        }
    }

    @Override
    public PythonClassNativeWrapper getNativeWrapper() {
        return (PythonClassNativeWrapper) super.getNativeWrapper();
    }

    public final Object getSulongType() {
        return sulongType;
    }

    @TruffleBoundary
    public final void setSulongType(Object dynamicSulongType) {
        this.sulongType = dynamicSulongType;
    }

    public boolean needsNativeAllocation() {
        return needsNativeAllocation;
    }

}
