package com.oracle.graal.python.nodes.expression;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;

import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class ContainsNode extends BinaryOpNode {
    @Child private LookupAndCallBinaryNode callNode = LookupAndCallBinaryNode.create(__CONTAINS__, null);
    @Child private CastToBooleanNode castBool = CastToBooleanNode.createIfTrueNode();

    @Child private GetIteratorNode getIterator;
    @Child private GetNextNode next;
    @Child private BinaryComparisonNode eqNode;
    @CompilationFinal private IsBuiltinClassProfile errorProfile;

    public static ExpressionNode create(ExpressionNode right, ExpressionNode left) {
        return ContainsNodeGen.create(right, left);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    boolean doBoolean(Object iter, boolean item) throws UnexpectedResultException {
        Object result = callNode.executeObject(iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            Object iterator = getGetIterator().executeWith(iter);
            return sequenceContains(iterator, item);
        }
        return castBool.executeWith(result);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    boolean doInt(Object iter, int item) throws UnexpectedResultException {
        Object result = callNode.executeObject(iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContains(getGetIterator().executeWith(iter), item);
        }
        return castBool.executeWith(result);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    boolean doLong(Object iter, long item) throws UnexpectedResultException {
        Object result = callNode.executeObject(iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContains(getGetIterator().executeWith(iter), item);
        }
        return castBool.executeWith(result);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    boolean doDouble(Object iter, double item) throws UnexpectedResultException {
        Object result = callNode.executeObject(iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContains(getGetIterator().executeWith(iter), item);
        }
        return castBool.executeWith(result);
    }

    @Specialization
    boolean doGeneric(Object iter, Object item) {
        Object result = callNode.executeObject(iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContainsObject(getGetIterator().executeWith(iter), item);
        }
        return castBool.executeWith(result);
    }

    private void handleUnexpectedResult(Object iterator, Object item, UnexpectedResultException e) throws UnexpectedResultException {
        // If we got an unexpected (non-primitive) result from the iterator, we need to compare it
        // and continue iterating with "next" through the generic case. However, we also want the
        // specialization to go away, so we wrap the boolean result in a new
        // UnexpectedResultException. This will cause the DSL to disable the specialization with the
        // primitive value and return the result we got, without iterating again.
        Object result = e.getResult();
        if (getEqNode().executeBool(result, item)) {
            result = true;
        } else {
            result = sequenceContainsObject(iterator, item);
        }
        throw new UnexpectedResultException(result);
    }

    private boolean sequenceContains(Object iterator, boolean item) throws UnexpectedResultException {
        while (true) {
            try {
                if (getNext().executeBoolean(iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(getErrorProfile());
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(iterator, item, e);
            }
        }
    }

    private boolean sequenceContains(Object iterator, int item) throws UnexpectedResultException {
        while (true) {
            try {
                if (getNext().executeInt(iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(getErrorProfile());
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(iterator, item, e);
            }
        }
    }

    private boolean sequenceContains(Object iterator, long item) throws UnexpectedResultException {
        while (true) {
            try {
                if (getNext().executeLong(iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(getErrorProfile());
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(iterator, item, e);
            }
        }
    }

    private boolean sequenceContains(Object iterator, double item) throws UnexpectedResultException {
        while (true) {
            try {
                if (getNext().executeDouble(iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(getErrorProfile());
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(iterator, item, e);
            }
        }
    }

    private boolean sequenceContainsObject(Object iterator, Object item) {
        while (true) {
            try {
                if (getEqNode().executeBool(getNext().execute(iterator), item)) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(getErrorProfile());
                return false;
            }
        }
    }

    private BinaryComparisonNode getEqNode() {
        if (eqNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            eqNode = insert(BinaryComparisonNode.create(__EQ__, __EQ__, "=="));
        }
        return eqNode;
    }

    private IsBuiltinClassProfile getErrorProfile() {
        if (errorProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            errorProfile = IsBuiltinClassProfile.create();
        }
        return errorProfile;
    }

    private GetNextNode getNext() {
        if (next == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            next = insert(GetNextNode.create());
        }
        return next;
    }

    private GetIteratorNode getGetIterator() {
        if (getIterator == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getIterator = insert(GetIteratorNode.create());
        }
        return getIterator;
    }
}
