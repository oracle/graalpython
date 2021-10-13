package com.oracle.graal.python.builtins.objects.partial;

import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.object.Shape;

public final class PPartial extends PythonBuiltinObject {
    private Object fn;
    private Object[] args;
    private PTuple argsTuple;
    private PKeyword[] kw;
    private PDict kwDict;

    public PPartial(Object cls, Shape instanceShape, Object fn, Object[] args, PKeyword[] kw) {
        super(cls, instanceShape);
        this.fn = fn;
        this.args = args;
        this.kw = kw;
    }

    public Object getFn() {
        return fn;
    }

    public void setFn(Object fn) {
        this.fn = fn;
    }

    public Object[] getArgs() {
        return args;
    }

    public PTuple getArgsTuple(PythonObjectFactory factory) {
        if (argsTuple == null) {
            this.argsTuple = factory.createTuple(args);
        }
        return argsTuple;
    }

    public void setArgs(PTuple args, SequenceNodes.GetSequenceStorageNode storageNode, SequenceStorageNodes.GetInternalObjectArrayNode arrayNode) {
        this.argsTuple = args;
        this.args = arrayNode.execute(storageNode.execute(args));
    }

    public PKeyword[] getKw() {
        return kw;
    }

    public PDict getKwDict(PythonObjectFactory factory) {
        if (kwDict == null) {
            this.kwDict = factory.createDict(kw);
        }
        return kwDict;
    }

    public void setKw(PDict kwArgs, ExpandKeywordStarargsNode expandNode) {
        this.kwDict = kwArgs;
        this.kw = expandNode.execute(kwArgs);
    }
}
