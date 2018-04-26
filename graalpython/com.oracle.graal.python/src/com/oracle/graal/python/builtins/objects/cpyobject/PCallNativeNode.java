package com.oracle.graal.python.builtins.objects.cpyobject;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

public class PCallNativeNode extends PBaseNode {
    private final int arity;

    @Child private Node executeNode;

    public PCallNativeNode(int arity) {
        this.arity = arity;
    }

    private Node getExecuteNode() {
        if (executeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            executeNode = insert(Message.createExecute(arity).createNode());
        }
        return executeNode;
    }

    public Object execute(PythonAbstractObject arg, TruffleObject func) {
        try {
            return ForeignAccess.sendExecute(getExecuteNode(), func, arg);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e.raise();
        }
    }

    public static PCallNativeNode create() {
        return new PCallNativeNode(1);
    }
}
