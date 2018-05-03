package com.oracle.graal.python.nodes.datamodel;

import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.truffle.api.dsl.ImportStatic;

@ImportStatic({PGuards.class, SpecialMethodNames.class})
public abstract class PDataModelEmulationNode extends PBaseNode {
    public abstract boolean execute(Object object);
}
