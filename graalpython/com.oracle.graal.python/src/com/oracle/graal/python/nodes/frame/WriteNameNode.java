package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.SetItemNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "rhs", type = ExpressionNode.class)
public abstract class WriteNameNode extends StatementNode implements WriteNode {
    protected final String attributeId;

    protected WriteNameNode(String attributeId) {
        this.attributeId = attributeId;
    }

    public static WriteNameNode create(String attributeId, ExpressionNode rhs) {
        return WriteNameNodeGen.create(attributeId, rhs);
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

    @Specialization(guards = "hasLocalsDict(frame)")
    protected void writeLocalsDict(VirtualFrame frame, Object value,
                    @Cached("create()") HashingCollectionNodes.SetItemNode setItem) {
        PDict frameLocals = (PDict) PArguments.getSpecialArgument(frame);
        setItem.execute(frameLocals, attributeId, value);
    }

    @Specialization(guards = "hasLocals(frame)")
    protected void writeLocal(VirtualFrame frame, Object value,
                    @Cached("create()") SetItemNode setItem) {
        Object frameLocals = PArguments.getSpecialArgument(frame);
        setItem.executeWith(frameLocals, attributeId, value);
    }

    @Specialization(guards = "!hasLocals(frame)")
    protected void writeGlobal(VirtualFrame frame, Object value,
                    @Cached("create(attributeId)") WriteGlobalNode setItem) {
        setItem.executeWithValue(frame, value);
    }

    public void doWrite(VirtualFrame frame, boolean value) {
        executeWithValue(frame, value);
    }

    public void doWrite(VirtualFrame frame, int value) {
        executeWithValue(frame, value);
    }

    public void doWrite(VirtualFrame frame, long value) {
        executeWithValue(frame, value);
    }

    public void doWrite(VirtualFrame frame, double value) {
        executeWithValue(frame, value);
    }

    public void doWrite(VirtualFrame frame, Object value) {
        executeWithValue(frame, value);
    }

    public abstract void executeWithValue(VirtualFrame frame, boolean value);

    public abstract void executeWithValue(VirtualFrame frame, int value);

    public abstract void executeWithValue(VirtualFrame frame, long value);

    public abstract void executeWithValue(VirtualFrame frame, double value);

    public abstract void executeWithValue(VirtualFrame frame, Object value);

}