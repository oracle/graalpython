package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class ReadNameNode extends ExpressionNode implements ReadNode {
    @Child private ReadGlobalOrBuiltinNode readGlobalNode;
    protected final IsBuiltinClassProfile keyError = IsBuiltinClassProfile.create();
    protected final String attributeId;

    protected ReadNameNode(String attributeId) {
        this.attributeId = attributeId;
    }

    public static ReadNameNode create(String attributeId) {
        return ReadNameNodeGen.create(attributeId);
    }

    protected boolean hasLocals(VirtualFrame frame) {
        // (tfel): This node will only ever be generated in a module scope
        // where neither generator special args nor a ClassBodyRootNode can
        // occur
        return PArguments.getSpecialArgument(frame) != null;
    }

    protected boolean hasLocalsDict(VirtualFrame frame) {
        return PArguments.getSpecialArgument(frame) instanceof PDict;
    }

    private ReadGlobalOrBuiltinNode getReadGlobalNode() {
        if (readGlobalNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readGlobalNode = insert(ReadGlobalOrBuiltinNode.create(attributeId));
        }
        return readGlobalNode;
    }

    private Object readGlobalsIfKeyError(VirtualFrame frame, PException e) {
        e.expect(PythonBuiltinClassType.KeyError, keyError);
        return getReadGlobalNode().execute(frame);
    }

    @Specialization(guards = "hasLocalsDict(frame)")
    protected Object readFromLocalsDict(VirtualFrame frame,
                    @Cached("create()") HashingStorageNodes.GetItemNode getItem) {
        PDict frameLocals = (PDict) PArguments.getSpecialArgument(frame);
        Object result = getItem.execute(frameLocals.getDictStorage(), attributeId);
        if (result == null) {
            return getReadGlobalNode().execute(frame);
        } else {
            return result;
        }
    }

    @Specialization(guards = "hasLocals(frame)", replaces = "readFromLocalsDict")
    protected Object readFromLocals(VirtualFrame frame,
                    @Cached("create()") GetItemNode getItem) {
        Object frameLocals = PArguments.getSpecialArgument(frame);
        try {
            return getItem.execute(frameLocals, attributeId);
        } catch (PException e) {
            return readGlobalsIfKeyError(frame, e);
        }
    }

    @Specialization(guards = "!hasLocals(frame)")
    protected Object readFromLocals(VirtualFrame frame) {
        return getReadGlobalNode().execute(frame);
    }

    public StatementNode makeWriteNode(ExpressionNode rhs) {
        return WriteNameNode.create(attributeId, rhs);
    }

    public String getAttributeId() {
        return attributeId;
    }
}