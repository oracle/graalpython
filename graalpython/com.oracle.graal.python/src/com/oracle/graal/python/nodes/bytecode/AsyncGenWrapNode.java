package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateUncached
public abstract class AsyncGenWrapNode extends PNodeWithContext {
    public abstract Object execute(Object receiver);

    public static AsyncGenWrapNode create() {
        return AsyncGenWrapNodeGen.create();
    }

    public static AsyncGenWrapNode getUncached() {
        return AsyncGenWrapNodeGen.getUncached();
    }

    @Specialization
    public Object makeWrapped(Object receiver,
                    @Cached PythonObjectFactory factory) {
        return factory.createAsyncGeneratorWrappedValue(receiver);
    }
}
