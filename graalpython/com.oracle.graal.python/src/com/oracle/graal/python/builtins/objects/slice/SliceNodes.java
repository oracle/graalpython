package com.oracle.graal.python.builtins.objects.slice;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class SliceNodes {
    @GenerateUncached
    public abstract static class CreateSliceNode extends PNodeWithContext {
        public abstract PSlice execute(Object start, Object stop, Object step);

        @SuppressWarnings("unused")
        static PSlice doInt(int start, int stop, PNone step,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createIntSlice(start, stop, 1, false, true);
        }

        @Specialization
        static PSlice doInt(int start, int stop, int step,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createIntSlice(start, stop, step);
        }

        @Specialization
        @SuppressWarnings("unused")
        static PSlice doInt(PNone start, int stop, PNone step,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createIntSlice(0, stop, 1, true, true);
        }

        @Specialization
        @SuppressWarnings("unused")
        static PSlice doInt(PNone start, int stop, int step,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createIntSlice(0, stop, step, true, false);
        }

        @Fallback
        static PSlice doGeneric(Object start, Object stop, Object step,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createObjectSlice(start, stop, step);
        }

        public static CreateSliceNode create() {
            return SliceNodesFactory.CreateSliceNodeGen.create();
        }

        public static CreateSliceNode getUncached() {
            return SliceNodesFactory.CreateSliceNodeGen.getUncached();
        }
    }
}
