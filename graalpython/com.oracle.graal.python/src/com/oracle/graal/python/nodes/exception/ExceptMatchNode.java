package com.oracle.graal.python.nodes.exception;

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PGuards.class)
@GenerateUncached
public abstract class ExceptMatchNode extends Node {
    public abstract boolean executeMatch(Frame frame, Object exception, Object clause);

    private static void raiseIfNoException(VirtualFrame frame, Object clause, ValidExceptionNode isValidException, PRaiseNode raiseNode) {
        if (!isValidException.execute(frame, clause)) {
            raiseNoException(raiseNode);
        }
    }

    private static void raiseNoException(PRaiseNode raiseNode) {
        throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.CATCHING_CLS_NOT_ALLOWED);
    }

    @Specialization(guards = "isClass(clause, lib)", limit = "3")
    static boolean matchPythonSingle(VirtualFrame frame, PException e, Object clause,
                    @SuppressWarnings("unused") @CachedLibrary("clause") InteropLibrary lib,
                    @Cached ValidExceptionNode isValidException,
                    @Cached GetClassNode getClassNode,
                    @Cached IsSubtypeNode isSubtype,
                    @Cached PRaiseNode raiseNode) {
        raiseIfNoException(frame, clause, isValidException, raiseNode);
        return isSubtype.execute(frame, getClassNode.execute(e.getUnreifiedException()), clause);
    }

    @Specialization(guards = {"eLib.isException(e)", "clauseLib.isMetaObject(clause)"}, limit = "3", replaces = "matchPythonSingle")
    @SuppressWarnings("unused")
    static boolean matchJava(VirtualFrame frame, AbstractTruffleException e, Object clause,
                    @Cached ValidExceptionNode isValidException,
                    @CachedLibrary("e") InteropLibrary eLib,
                    @CachedLibrary("clause") InteropLibrary clauseLib,
                    @Cached PRaiseNode raiseNode) {
        // n.b.: we can only allow Java exceptions in clauses, because we cannot tell for other
        // foreign exception types if they *are* exception types
        raiseIfNoException(frame, clause, isValidException, raiseNode);
        try {
            return clauseLib.isMetaInstance(clause, e);
        } catch (UnsupportedMessageException e1) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Specialization
    static boolean matchTuple(VirtualFrame frame, Object e, PTuple clause,
                    @Cached ExceptMatchNode recursiveNode,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
        // check for every type in the tuple
        SequenceStorage storage = clause.getSequenceStorage();
        int length = storage.length();
        for (int i = 0; i < length; i++) {
            Object clauseType = getItemNode.execute(storage, i);
            if (recursiveNode.executeMatch(frame, e, clauseType)) {
                return true;
            }
        }
        return false;
    }

    @Fallback
    @SuppressWarnings("unused")
    static boolean fallback(VirtualFrame frame, Object e, Object clause,
                    @Cached PRaiseNode raiseNode) {
        raiseNoException(raiseNode);
        return false;
    }

    public static ExceptMatchNode create() {
        return ExceptMatchNodeGen.create();
    }

    public static ExceptMatchNode getUncached() {
        return ExceptMatchNodeGen.getUncached();
    }
}
