package com.oracle.graal.python.builtins.modules;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

import static com.oracle.graal.python.builtins.modules.ImpModuleBuiltins.importGetModule;
import static com.oracle.graal.python.builtins.modules.ImpModuleBuiltins.importFrozenModuleObject;

@GenerateUncached
public abstract class InitFrozenHelper extends PNodeWithContext {

    public abstract Object execute(Python3Core core, String name);

    @Specialization
    public Object run(Python3Core core, String name,
                      @Cached ConditionProfile isStringProfile,
                      @Cached PRaiseNode raiseNode) {
        int ret = importFrozenModuleObject(core, name, isStringProfile, raiseNode);

        if (ret < 0 ) {
            return null; // TODO: refactor importFrozenModuleObject
        } else if (ret == 0) {
            return PNone.NONE;
        }

        return importGetModule(core, name);
    }

    public static InitFrozenHelper getUncached() {
        return InitFrozenHelperNodeGen.getUncached();
    }
}