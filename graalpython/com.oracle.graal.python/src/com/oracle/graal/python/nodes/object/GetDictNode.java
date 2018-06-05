package com.oracle.graal.python.nodes.object;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

@ImportStatic(PGuards.class)
public abstract class GetDictNode extends PBaseNode {

    public abstract Object execute(Object o);

    @Specialization
    Object dict(PDict self) {
        return self;
    }

    @Specialization
    Object dict(PythonModule self) {
        PDict dict = self.getDict();
        if (dict == null) {
            dict = factory().createDictFixedStorage(self);
            self.setDict(dict);
        }
        return dict;
    }

    @Fallback
    Object dict(@SuppressWarnings("unused") Object self) {
        return PNone.NONE;
    }

    public static GetDictNode create() {
        return GetDictNodeGen.create();
    }
}
