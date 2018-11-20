package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class DeleteNameNode extends StatementNode {
    @Child private DeleteGlobalNode deleteGlobalNode;
    protected final IsBuiltinClassProfile keyError = IsBuiltinClassProfile.create();
    protected final String attributeId;

    protected DeleteNameNode(String attributeId) {
        this.attributeId = attributeId;
    }

    public static DeleteNameNode create(String attributeId) {
        return DeleteNameNodeGen.create(attributeId);
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

    private DeleteGlobalNode getDeleteGlobalNode() {
        if (deleteGlobalNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            deleteGlobalNode = insert(DeleteGlobalNode.create(attributeId));
        }
        return deleteGlobalNode;
    }

    private void deleteGlobalIfKeyError(VirtualFrame frame, PException e) {
        e.expect(PythonBuiltinClassType.KeyError, keyError);
        getDeleteGlobalNode().executeVoid(frame);
    }

    @Specialization(guards = "hasLocalsDict(frame)")
    protected void readFromLocalsDict(VirtualFrame frame,
                    @Cached("create()") HashingStorageNodes.DelItemNode delItem) {
        PDict frameLocals = (PDict) PArguments.getSpecialArgument(frame);
        if (!delItem.execute(frameLocals, frameLocals.getDictStorage(), attributeId)) {
            getDeleteGlobalNode().executeVoid(frame);
        }
    }

    @Specialization(guards = "hasLocals(frame)", replaces = "readFromLocalsDict")
    protected void readFromLocals(VirtualFrame frame,
                    @Cached("create()") DeleteItemNode delItem) {
        Object frameLocals = PArguments.getSpecialArgument(frame);
        try {
            delItem.executeWith(frameLocals, attributeId);
        } catch (PException e) {
            deleteGlobalIfKeyError(frame, e);
        }
    }

    @Specialization(guards = "!hasLocals(frame)")
    protected void readFromLocals(VirtualFrame frame) {
        getDeleteGlobalNode().executeVoid(frame);
    }
}