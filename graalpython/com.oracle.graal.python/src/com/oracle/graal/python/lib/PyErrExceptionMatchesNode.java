package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

/**
 * Equivalent of {@code PyErr_GivenExceptionMatches}. Note that if you just want to match against a
 * single specific exception type, this node is an overkill and you should use
 * {@link IsBuiltinObjectProfile} instead. Only use when you need to handle tuples of types as well.
 */
@GenerateInline(inlineByDefault = true)
@GenerateUncached
public abstract class PyErrExceptionMatchesNode extends Node {
    public abstract boolean execute(Node inliningTarget, Object exception, Object typeOrTuple);

    public static boolean executeUncached(Object exception, Object typeOrTuple) {
        return PyErrExceptionMatchesNodeGen.getUncached().execute(null, exception, typeOrTuple);
    }

    // Truffle complains about the limit, but it's needed for the fallback
    @SuppressWarnings("truffle-unused")
    @Specialization(guards = "isExceptionType(inliningTarget, type, isTypeNode, isBuiltinClassProfile)", limit = "1")
    static boolean doException(@SuppressWarnings("unused") Node inliningTarget, Object exception, Object type,
                    @SuppressWarnings("unused") @Cached BuiltinClassProfiles.IsBuiltinClassProfile isBuiltinClassProfile,
                    @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Cached PyExceptionInstanceCheckNode exceptionCheck,
                    @Cached GetClassNode getClassNode,
                    @Cached(inline = false) IsSubtypeNode isSubtypeNode) {
        if (exceptionCheck.execute(inliningTarget, exception)) {
            return isSubtypeNode.execute(getClassNode.execute(inliningTarget, exception), type);
        } else if (isTypeNode.execute(inliningTarget, exception)) {
            return isSubtypeNode.execute(exception, type);
        } else {
            return false;
        }
    }

    @Specialization(guards = "tupleCheck.execute(inliningTarget, tuple)", limit = "1")
    static boolean doTuple(Node inliningTarget, Object exception, Object tuple,
                    @SuppressWarnings("unused") @Cached PyTupleCheckNode tupleCheck,
                    @Cached TupleNodes.GetTupleStorage getTupleStorage,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                    @Cached InlinedLoopConditionProfile loopConditionProfile,
                    @Cached(inline = false) PyErrExceptionMatchesNode recursive) {
        SequenceStorage storage = getTupleStorage.execute(inliningTarget, tuple);
        int len = storage.length();
        loopConditionProfile.profileCounted(inliningTarget, len);
        for (int i = 0; loopConditionProfile.inject(inliningTarget, i < len); i++) {
            if (recursive.execute(null, exception, getItemScalarNode.execute(inliningTarget, storage, i))) {
                return true;
            }
        }
        return false;
    }

    @Fallback
    @SuppressWarnings("unused")
    static boolean fallback(Object exception, Object invalid) {
        return false;
    }

    protected static boolean isExceptionType(Node inliningTarget, Object type, TypeNodes.IsTypeNode isTypeNode, BuiltinClassProfiles.IsBuiltinClassProfile isBuiltinClassProfile) {
        return isTypeNode.execute(inliningTarget, type) && isBuiltinClassProfile.profileClass(inliningTarget, type, PythonBuiltinClassType.PBaseException);
    }
}
