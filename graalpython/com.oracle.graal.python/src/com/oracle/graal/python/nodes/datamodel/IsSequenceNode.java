package com.oracle.graal.python.nodes.datamodel;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class IsSequenceNode extends PDataModelEmulationNode {
    @Child private LookupInheritedAttributeNode getGetItemNode = LookupInheritedAttributeNode.create();
    @Child private LookupInheritedAttributeNode getLenNode = LookupInheritedAttributeNode.create();
    private final ConditionProfile lenProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile getItemProfile = ConditionProfile.createBinaryProfile();

    @Specialization
    public boolean isSequence(Object object) {
        Object len = getLenNode.execute(object, __LEN__);
        if (lenProfile.profile(len != PNone.NO_VALUE)) {
            return getItemProfile.profile(getGetItemNode.execute(object, __GETITEM__) != PNone.NO_VALUE);
        }
        return false;
    }

    public static IsSequenceNode create() {
        return IsSequenceNodeGen.create();
    }
}
