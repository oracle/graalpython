package com.oracle.graal.python.builtins.objects.type;

import com.oracle.graal.python.builtins.objects.type.PythonClass.FlagsContainer;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public abstract class GetFlagsNode extends Node {

    public abstract long execute(PythonClass c);

    @Specialization(guards = "isInitialized(c.getFlagsContainer())")
    long doInitialized(PythonClass c) {
        return c.getFlagsContainer().flags;
    }

    @Fallback
    @TruffleBoundary
    long doUninitialized(PythonClass c) {
        FlagsContainer flagsContainer = c.getFlagsContainer();
        // This method is only called from C code, i.e., the flags of the initial super class
        // must be available.
        if (flagsContainer.initialDominantBase != null) {
            assert flagsContainer != flagsContainer.initialDominantBase.getFlagsContainer();
            flagsContainer.flags = flagsContainer.initialDominantBase.getFlagsContainer().getValue();
            flagsContainer.initialDominantBase = null;
        }
        return flagsContainer.flags;
    }

    protected static boolean isInitialized(FlagsContainer c) {
        return c.initialDominantBase == null;
    }

    public static GetFlagsNode create() {
        return GetFlagsNodeGen.create();
    }

}
