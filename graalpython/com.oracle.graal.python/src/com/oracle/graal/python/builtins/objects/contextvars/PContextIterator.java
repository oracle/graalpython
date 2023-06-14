package com.oracle.graal.python.builtins.objects.contextvars;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.Shape;

public final class PContextIterator extends PythonBuiltinObject {
    public enum ItemKind {
        KEYS,
        VALUES,
        ITEMS;

        public Object apply(Hamt.Entry item, PythonObjectFactory factory) {
            switch (this) {
                case KEYS:
                    return item.key;
                case VALUES:
                    return item.value;
                case ITEMS:
                    return factory.createTuple(new Object[]{item.key, item.value});
                default:
                    throw CompilerDirectives.shouldNotReachHere("null ItemKind in PHamtIterator");
            }
        }
    }

    private final ItemKind kind;
    private final HamtIterator it;

    public PContextIterator(Object cls, Shape instanceShape, PContextVarsContext ctx, ItemKind kind) {
        super(cls, instanceShape);
        this.it = new HamtIterator(ctx.contextVarValues);
        this.kind = kind;

    }

    // can return null
    public Object next(PythonObjectFactory factory) {
        Hamt.Entry item = it.next();
        return item == null ? null : kind.apply(item, factory);
    }
}
