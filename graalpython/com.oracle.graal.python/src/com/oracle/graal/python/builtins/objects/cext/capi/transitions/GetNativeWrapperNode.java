package com.oracle.graal.python.builtins.objects.cext.capi.transitions;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.TruffleObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@SuppressWarnings("truffle-inlining")
@ImportStatic({PGuards.class, CApiGuards.class})
public abstract class GetNativeWrapperNode extends PNodeWithContext {

    public abstract PythonNativeWrapper execute(Object value);

    @Specialization
    static PythonNativeWrapper doString(TruffleString str,
                    @Cached PythonObjectFactory factory,
                    @Exclusive @Cached ConditionProfile noWrapperProfile) {
        return PythonObjectNativeWrapper.wrap(factory.createString(str), noWrapperProfile);
    }

    @Specialization
    PythonNativeWrapper doBoolean(boolean b,
                    @Exclusive @Cached ConditionProfile profile) {
        Python3Core core = PythonContext.get(this);
        PInt boxed = b ? core.getTrue() : core.getFalse();
        DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
        if (profile.profile(nativeWrapper == null)) {
            CompilerDirectives.transferToInterpreter();
            nativeWrapper = PrimitiveNativeWrapper.createBool(b);
            boxed.setNativeWrapper(nativeWrapper);
        }
        return nativeWrapper;
    }

    @Specialization(guards = "isSmallInteger(i)")
    PrimitiveNativeWrapper doIntegerSmall(int i) {
        PythonContext context = getContext();
        if (context.getCApiContext() != null) {
            return context.getCApiContext().getCachedPrimitiveNativeWrapper(i);
        }
        return PrimitiveNativeWrapper.createInt(i);
    }

    @Specialization(guards = "!isSmallInteger(i)")
    static PrimitiveNativeWrapper doInteger(int i) {
        return PrimitiveNativeWrapper.createInt(i);
    }

    static PrimitiveNativeWrapper doLongSmall(long l, PythonContext context) {
        if (context.getCApiContext() != null) {
            return context.getCApiContext().getCachedPrimitiveNativeWrapper(l);
        }
        return PrimitiveNativeWrapper.createLong(l);
    }

    @Specialization(guards = "isSmallLong(l)")
    PrimitiveNativeWrapper doLongSmall(long l) {
        return doLongSmall(l, getContext());
    }

    @Specialization(guards = "!isSmallLong(l)")
    static PrimitiveNativeWrapper doLong(long l) {
        return PrimitiveNativeWrapper.createLong(l);
    }

    @Specialization(guards = "!isNaN(d)")
    static PythonNativeWrapper doDouble(double d) {
        return PrimitiveNativeWrapper.createDouble(d);
    }

    @Specialization(guards = "isNaN(d)")
    PythonNativeWrapper doDoubleNaN(@SuppressWarnings("unused") double d) {
        PFloat boxed = getContext().getNaN();
        DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
        // Use a counting profile since we should enter the branch just once per context.
        if (nativeWrapper == null) {
            // This deliberately uses 'CompilerDirectives.transferToInterpreter()' because this
            // code will happen just once per context.
            CompilerDirectives.transferToInterpreter();
            nativeWrapper = PrimitiveNativeWrapper.createDouble(Double.NaN);
            boxed.setNativeWrapper(nativeWrapper);
        }
        return nativeWrapper;
    }

    static PythonNativeWrapper doSingleton(@SuppressWarnings("unused") PythonAbstractObject object, PythonContext context) {
        PythonNativeWrapper nativeWrapper = context.getSingletonNativeWrapper(object);
        if (nativeWrapper == null) {
            // this will happen just once per context and special singleton
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeWrapper = new PythonObjectNativeWrapper(object);
            // this should keep the native wrapper alive forever
            CApiTransitions.incRef(nativeWrapper, PythonNativeWrapper.IMMORTAL_REFCNT);
            context.setSingletonNativeWrapper(object, nativeWrapper);
        }
        return nativeWrapper;
    }

    @Specialization(guards = "isSpecialSingleton(object)")
    PythonNativeWrapper doSingleton(PythonAbstractObject object) {
        return doSingleton(object, getContext());
    }

    @Specialization
    static PythonNativeWrapper doPythonClassUncached(PythonManagedClass object,
                    @Exclusive @Cached TypeNodes.GetNameNode getNameNode) {
        return PythonClassNativeWrapper.wrap(object, getNameNode.execute(object));
    }

    @Specialization
    static PythonNativeWrapper doPythonTypeUncached(PythonBuiltinClassType object,
                    @Exclusive @Cached TypeNodes.GetNameNode getNameNode) {
        return PythonClassNativeWrapper.wrap(PythonContext.get(getNameNode).lookupType(object), getNameNode.execute(object));
    }

    @Specialization(guards = {"!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"})
    static PythonNativeWrapper runAbstractObject(PythonAbstractObject object,
                    @Exclusive @Cached ConditionProfile noWrapperProfile,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
        assert object != PNone.NO_VALUE;
        return PythonObjectNativeWrapper.wrap(object, noWrapperProfile);
    }

    @Specialization(guards = {"isForeignObjectNode.execute(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "3")
    static PythonNativeWrapper doForeignObject(Object object,
                    @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode) {
        assert !CApiTransitions.isBackendPointerObject(object);
        assert !(object instanceof String);
        return TruffleObjectNativeWrapper.wrap(object);
    }

    protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
        return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
    }

    protected static PythonClassNativeWrapper wrapNativeClassFast(PythonBuiltinClassType object, PythonContext context) {
        return PythonClassNativeWrapper.wrap(context.lookupType(object), GetNameNode.doSlowPath(object));
    }

    protected static boolean isNaN(double d) {
        return Double.isNaN(d);
    }
}
