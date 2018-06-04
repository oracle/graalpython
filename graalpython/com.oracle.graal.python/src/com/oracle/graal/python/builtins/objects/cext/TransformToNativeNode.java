package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

abstract class TransformToNativeNode extends PBaseNode {
    @Child private Node isPointerNode;
    @Child private Node toNativeNode;

    protected Object ensureIsPointer(Object value) {
        if (value instanceof TruffleObject) {
            TruffleObject truffleObject = (TruffleObject) value;
            if (isPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPointerNode = insert(Message.IS_POINTER.createNode());
            }
            if (!ForeignAccess.sendIsPointer(isPointerNode, truffleObject)) {
                if (toNativeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    toNativeNode = insert(Message.TO_NATIVE.createNode());
                }
                try {
                    return ForeignAccess.sendToNative(toNativeNode, truffleObject);
                } catch (UnsupportedMessageException e) {
                    throw e.raise();
                }
            }
        }
        return value;
    }

}
