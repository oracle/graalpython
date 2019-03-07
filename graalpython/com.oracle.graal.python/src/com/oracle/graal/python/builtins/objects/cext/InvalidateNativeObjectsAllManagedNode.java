package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.InvalidateNativeObjectsAllManagedNodeFactory.InvalidateNativeObjectsAllManagedCachedNodeGen;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

abstract class InvalidateNativeObjectsAllManagedNode extends PNodeWithContext {

    public abstract void execute();

    abstract static class InvalidateNativeObjectsAllManagedCachedNode extends InvalidateNativeObjectsAllManagedNode {

        @Specialization(assumptions = {"singleContextAssumption()", "nativeObjectsAllManagedAssumption"})
        void doValid(
                        @Cached("nativeObjectsAllManagedAssumption()") Assumption nativeObjectsAllManagedAssumption) {
            nativeObjectsAllManagedAssumption.invalidate();
        }

        @Specialization
        void doInvalid() {
        }
    }

    protected static Assumption nativeObjectsAllManagedAssumption() {
        return PythonLanguage.getContextRef().get().getNativeObjectsAllManagedAssumption();
    }

    static final class InvalidateNativeObjectsAllManagedUncachedNode extends InvalidateNativeObjectsAllManagedNode {
        private static final InvalidateNativeObjectsAllManagedNode.InvalidateNativeObjectsAllManagedUncachedNode INSTANCE = new InvalidateNativeObjectsAllManagedUncachedNode();

        @Override
        public void execute() {
            nativeObjectsAllManagedAssumption().invalidate();
        }

    }

    public static InvalidateNativeObjectsAllManagedNode create() {
        return InvalidateNativeObjectsAllManagedCachedNodeGen.create();
    }

    public static InvalidateNativeObjectsAllManagedNode getUncached() {
        return InvalidateNativeObjectsAllManagedUncachedNode.INSTANCE;
    }
}