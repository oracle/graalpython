/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * The standard Python object library. This implements a general-purpose Python object interface.
 * The equivalent in CPython would be {@code abstract.[ch]}, in PyPy it is the {@code StdObjSpace}.
 * Most generic operations available on Python objects should eventually be available through this
 * library.
 */
@GenerateLibrary
@DefaultExport(DefaultPythonBooleanExports.class)
@DefaultExport(DefaultPythonIntegerExports.class)
@DefaultExport(DefaultPythonLongExports.class)
@DefaultExport(DefaultPythonDoubleExports.class)
@DefaultExport(DefaultPythonStringExports.class)
@DefaultExport(DefaultPythonObjectExports.class)
@SuppressWarnings("unused")
public abstract class PythonObjectLibrary extends Library {
    /**
     * Checks whether the receiver is a Python callable object. As described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model</a> and
     * <a href="https://docs.python.org/3/library/collections.abc.html">Abstract Base Classes for
     * Containers</a>
     *
     * <br>
     * Specifically the default implementation checks for the implementation of the <b>__call__</b>
     * special method.
     *
     * @param receiver the receiver Object
     * @return True if object has the __CALL__ attribute
     */
    public boolean isCallable(Object receiver) {
        return lookupAttributeOnType(receiver, __CALL__) != PNone.NO_VALUE;
    }

    private abstract static class DefaultNodes extends Node {

        protected abstract IsSubtypeNode getIsSubtypeNode();

        protected abstract IsSameTypeNode getIsSameTypeNode();

        protected abstract GetClassNode getGetClassNode();

        protected abstract PRaiseNode getRaiseNode();

        protected abstract void enterReverseCompare();

        protected abstract void enterLeftCompare();

        protected abstract void enterSubtypeCompare();

        private static final class CachedDefaultNodes extends DefaultNodes {
            private static final byte REVERSE_COMP = 0b001;
            private static final byte LEFT_COMPARE = 0b010;
            private static final byte SUBT_COMPARE = 0b100;

            @Child private IsSubtypeNode isSubtype;
            @Child private IsSameTypeNode isSameType;
            @Child private GetClassNode getClassNode;
            @Child private PRaiseNode raiseNode;
            @CompilationFinal byte state = 0;

            @Override
            protected IsSubtypeNode getIsSubtypeNode() {
                if (isSubtype == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reportPolymorphicSpecialize();
                    isSubtype = insert(IsSubtypeNode.create());
                }
                return isSubtype;
            }

            @Override
            protected IsSameTypeNode getIsSameTypeNode() {
                if (isSameType == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    isSameType = insert(IsSameTypeNodeGen.create());
                }
                return isSameType;
            }

            @Override
            protected GetClassNode getGetClassNode() {
                if (getClassNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getClassNode = insert(GetClassNode.create());
                }
                return getClassNode;
            }

            @Override
            protected PRaiseNode getRaiseNode() {
                if (raiseNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    raiseNode = insert(PRaiseNode.create());
                }
                return raiseNode;
            }

            @Override
            protected void enterReverseCompare() {
                if ((state & REVERSE_COMP) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reportPolymorphicSpecialize();
                    state |= REVERSE_COMP;
                }
            }

            @Override
            protected void enterLeftCompare() {
                if ((state & LEFT_COMPARE) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reportPolymorphicSpecialize();
                    state |= LEFT_COMPARE;
                }
            }

            @Override
            protected void enterSubtypeCompare() {
                if ((state & SUBT_COMPARE) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reportPolymorphicSpecialize();
                    state |= SUBT_COMPARE;
                }
            }
        }

        private static final class Disabled extends DefaultNodes {
            private static final Disabled INSTANCE = new Disabled();

            @Override
            protected IsSubtypeNode getIsSubtypeNode() {
                return IsSubtypeNodeGen.getUncached();
            }

            @Override
            protected IsSameTypeNode getIsSameTypeNode() {
                return IsSameTypeNodeGen.getUncached();
            }

            @Override
            protected GetClassNode getGetClassNode() {
                return GetClassNode.getUncached();
            }

            @Override
            protected PRaiseNode getRaiseNode() {
                return PRaiseNode.getUncached();
            }

            @Override
            protected void enterReverseCompare() {
            }

            @Override
            protected void enterLeftCompare() {
            }

            @Override
            protected void enterSubtypeCompare() {
            }
        }

        private static DefaultNodes create() {
            return new CachedDefaultNodes();
        }

        private static DefaultNodes getUncached() {
            return Disabled.INSTANCE;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }
    }

    // to conserve memory, any default nodes are children of this field, so we
    // only have one additional word per PythonObjectLibrary if these are not
    // used.
    @Child private DefaultNodes defaultNodes;

    // Profiling a frame is needed in many calls so it's separate from the above
    @CompilationFinal private ConditionProfile hasFrameProfile;

    private DefaultNodes getDefaultNodes() {
        if (isAdoptable()) {
            if (defaultNodes == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                defaultNodes = insert(DefaultNodes.create());
            }
            return defaultNodes;
        } else {
            return DefaultNodes.getUncached();
        }
    }

    private ThreadState getStateFromFrame(VirtualFrame frame) {
        if (profileHasFrame(frame)) {
            return PArguments.getThreadState(frame);
        } else {
            return null;
        }
    }

    private boolean profileHasFrame(VirtualFrame frame) {
        if (isAdoptable()) {
            if (hasFrameProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasFrameProfile = ConditionProfile.create();
            }
            return hasFrameProfile.profile(frame != null);
        } else {
            return frame != null;
        }
    }

    /**
     * Compare {@code receiver} to {@code other} using CPython pointer comparison semantics.
     */
    public abstract boolean isSame(Object receiver, Object other);

    /**
     * Compare {@code receiver} to {@code other}. If the receiver does not know how to compare
     * itself to the argument, the comparison is tried in reverse. This implements
     * {@code PyObject_RichCompareBool} (which calls {@code do_richcompare}) for the {@code __eq__}
     * operator.
     *
     * Exporters of this library can override this message for performance.
     *
     * @param receiver - the lhs, tried first
     * @param other - the rhs, tried only if lhs does not know how to compare itself here
     * @param otherLibrary - a PythonObjectLibrary that accepts {@code other}. Used for the reverse
     *            dispatch.
     */
    public boolean equalsWithState(Object receiver, Object other, PythonObjectLibrary otherLibrary, ThreadState threadState) {
        if (isSame(receiver, other)) {
            return true; // guarantee
        }

        boolean checkedReverseOp = false;

        Object leftClass = getDefaultNodes().getGetClassNode().execute(receiver);
        Object rightClass = otherLibrary.getDefaultNodes().getGetClassNode().execute(other);
        int result;
        boolean isSameType = getDefaultNodes().getIsSameTypeNode().execute(leftClass, rightClass);
        if (!isSameType && getDefaultNodes().getIsSubtypeNode().execute(rightClass, leftClass)) {
            getDefaultNodes().enterSubtypeCompare();
            checkedReverseOp = true;
            result = otherLibrary.equalsInternal(other, receiver, threadState);
            if (result != -1) {
                return result == 1;
            }
        }
        getDefaultNodes().enterLeftCompare();
        result = equalsInternal(receiver, other, threadState);
        if (result != -1) {
            return result == 1;
        }
        if (!isSameType && !checkedReverseOp) {
            getDefaultNodes().enterReverseCompare();
            result = otherLibrary.equalsInternal(other, receiver, threadState);
        }

        // we already checked for identity equality above, so if neither side
        // knows what to do, they are not equal
        return result == 1;
    }

    /**
     * Compare {@code receiver} to {@code other} using {@code __eq__}.
     *
     * @param threadState may be {@code null}
     * @return 0 if not equal, 1 if equal, -1 if {@code __eq__} returns {@code NotImplemented}
     */
    public abstract int equalsInternal(Object receiver, Object other, ThreadState threadState);

    /**
     * Return the file system path representation of the object. If the object is str or bytes, then
     * allow it to pass through. If the object defines __fspath__(), then return the result of that
     * method. All other types raise a TypeError.
     */
    public String asPathWithState(Object receiver, @SuppressWarnings("unused") ThreadState threadState) {
        throw getDefaultNodes().getRaiseNode().raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_STR_BYTE_OSPATHLIKE_OBJ, receiver);
    }

    /**
     * @see #asPathWithState
     */
    public final String asPath(Object receiver) {
        return asPathWithState(receiver, null);
    }

    /**
     * Looks up an attribute for the given receiver like {@code PyObject_LookupAttr}.
     *
     * @param receiver self
     * @param name attribute name
     * @return found attribute object or {@link PNone#NO_VALUE}
     */
    public final Object lookupAttribute(Object receiver, VirtualFrame frame, String name) {
        return lookupAttributeInternal(receiver, getStateFromFrame(frame), name, false);
    }

    /**
     * @see #lookupAttribute(Object, VirtualFrame, String)
     */
    public final Object lookupAttributeWithState(Object receiver, ThreadState state, String name) {
        return lookupAttributeInternal(receiver, state, name, false);
    }

    /**
     * Looks up an attribute for the given receiver like {@code PyObject_GetAttr}. Raises an
     * {@code AttributeError} if not found.
     *
     * @param receiver self
     * @param name attribute name
     * @return found attribute object
     */
    public final Object lookupAttributeStrict(Object receiver, VirtualFrame frame, String name) {
        return lookupAttributeInternal(receiver, getStateFromFrame(frame), name, true);
    }

    /**
     * @see #lookupAttributeStrict(Object, VirtualFrame, String)
     */
    public final Object lookupAttributeStrictWithState(Object receiver, ThreadState state, String name) {
        return lookupAttributeInternal(receiver, state, name, true);
    }

    /**
     * Method to implement {@link #lookupAttribute} and {@link #lookupAttributeStrict} for the
     * library. Implementor note: state may be null.
     */
    protected Object lookupAttributeInternal(Object receiver, ThreadState state, String name, boolean strict) {
        // Default implementation for objects that only want to provide special methods
        return lookupAttributeOnTypeInternal(receiver, name, strict);
    }

    /**
     * Lookup an attribute directly in MRO of the receiver's type. Doesn't bind the attribute to the
     * object. Typically used to lookup special methods and attributes. Equivalent of CPython's
     * {@code _PyType_Lookup} or {@code lookup_maybe_method}.
     *
     * @param receiver self
     * @param name attribute name
     * @return found attribute or {@code PNone#NO_VALUE}
     */
    public final Object lookupAttributeOnType(Object receiver, String name) {
        return lookupAttributeOnTypeInternal(receiver, name, false);
    }

    /**
     * Like {@link #lookupAttributeOnType(Object, String)}, but raises an {@code AttributeError}
     * when the attribute is not found.
     */
    public final Object lookupAttributeOnTypeStrict(Object receiver, String name) {
        return lookupAttributeOnTypeInternal(receiver, name, true);
    }

    /**
     * Method to implement {@link #lookupAttributeOnType} and {@link #lookupAttributeOnTypeStrict}
     * for the library.
     */
    protected abstract Object lookupAttributeOnTypeInternal(Object receiver, String name, boolean strict);

    /**
     * Call a callable object.
     */
    public final Object callObject(Object callable, VirtualFrame frame, Object... arguments) {
        ThreadState state = null;
        if (profileHasFrame(frame)) {
            state = PArguments.getThreadState(frame);
        }
        return callObjectWithState(callable, state, arguments);
    }

    /**
     * Call a callable object.
     */
    public Object callObjectWithState(Object callable, ThreadState state, Object... arguments) {
        throw getDefaultNodes().getRaiseNode().raise(TypeError, ErrorMessages.OBJ_ISNT_CALLABLE, callable);
    }

    /**
     * Call an unbound method or other unbound descriptor using given receiver. Will first call
     * {@code __get__} to bind the descriptor, then call the bound object. There are optimized
     * implementations for plain and builtin functions that avoid creating the intermediate bound
     * object. Typically called on a result of {@link #lookupAttributeOnType}. Equivalent of
     * CPython's {@code call_unbound}.
     *
     * @param method unbound method or descriptor object whose {@code __get__} hasn't been called
     * @param receiver self
     */
    public final Object callUnboundMethod(Object method, VirtualFrame frame, Object receiver, Object... arguments) {
        ThreadState state = null;
        if (profileHasFrame(frame)) {
            state = PArguments.getThreadState(frame);
        }
        return callUnboundMethodWithState(method, state, receiver, arguments);
    }

    /**
     * @see #callUnboundMethod(Object, VirtualFrame, Object, Object...)
     */
    public Object callUnboundMethodWithState(Object method, ThreadState state, Object receiver, Object... arguments) {
        throw getDefaultNodes().getRaiseNode().raise(TypeError, ErrorMessages.OBJ_ISNT_CALLABLE, method);
    }

    /**
     * Like {@link #callUnboundMethod(Object, VirtualFrame, Object, Object...)}, but ignores
     * possible python exception in the @{code __get__} call and returns {@link PNone#NO_VALUE} in
     * that case.
     */
    public final Object callUnboundMethodIgnoreGetException(Object method, VirtualFrame frame, Object self, Object... arguments) {
        ThreadState state = null;
        if (profileHasFrame(frame)) {
            state = PArguments.getThreadState(frame);
        }
        return callUnboundMethodIgnoreGetExceptionWithState(method, state, self, arguments);
    }

    /**
     * @see #callUnboundMethodIgnoreGetException(Object, VirtualFrame, Object, Object...)
     */
    public Object callUnboundMethodIgnoreGetExceptionWithState(Object method, ThreadState state, Object self, Object... arguments) {
        return callUnboundMethodWithState(method, state, method, arguments);
    }

    /**
     * Call a special method on an object. Raises {@code AttributeError} if no such method was
     * found.
     */
    public final Object lookupAndCallSpecialMethod(Object receiver, VirtualFrame frame, String methodName, Object... arguments) {
        ThreadState state = null;
        if (profileHasFrame(frame)) {
            state = PArguments.getThreadState(frame);
        }
        return lookupAndCallSpecialMethodWithState(receiver, state, methodName, arguments);
    }

    /**
     * @see #lookupAndCallSpecialMethod(Object, VirtualFrame, String, Object...)
     */
    public abstract Object lookupAndCallSpecialMethodWithState(Object receiver, ThreadState state, String methodName, Object... arguments);

    /**
     * Call a regular (not special) method on an object. Raises {@code AttributeError} if no such
     * method was found.
     */
    public final Object lookupAndCallRegularMethod(Object receiver, VirtualFrame frame, String methodName, Object... arguments) {
        ThreadState state = null;
        if (profileHasFrame(frame)) {
            state = PArguments.getThreadState(frame);
        }
        return lookupAndCallRegularMethodWithState(receiver, state, methodName, arguments);
    }

    /**
     * @see #lookupAndCallRegularMethod(Object, VirtualFrame, String, Object...)
     */
    public abstract Object lookupAndCallRegularMethodWithState(Object receiver, ThreadState state, String methodName, Object... arguments);

    /**
     * Checks whether the reciever is a buffer, e.g. bytes-like, object storage.
     *
     * @return true if the receiver is a buffer, false otherwise.
     */
    @Abstract(ifExported = {"getBufferBytes", "getBufferLength"})
    public boolean isBuffer(Object receiver) {
        return false;
    }

    /**
     * Returns the length of the buffer, i.e. number of bytes.
     *
     * @param receiver a buffer object. Use {@link #isBuffer(Object)} to check if the receiver is a
     *            buffer or not.
     * @return Returns the length of the buffer
     * @throws UnsupportedMessageException if the object is not a buffer. Use
     *             {@link #isBuffer(Object)} to check if the receiver is a buffer or not.
     */
    @Abstract(ifExported = {"isBuffer", "getBufferBytes"})
    public int getBufferLength(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns a copy of the byte[] array of the buffer. Any changes to the returned byte array
     * won't be reflected in the receiver's storage.
     * 
     * @param receiver a buffer object.
     * @return a byte array copy of the receiver's storage.
     * @throws UnsupportedMessageException if the object is not a buffer. Use
     *             {@link #isBuffer(Object)} to check if the receiver is a buffer or not.
     */
    @Abstract(ifExported = {"isBuffer", "getBufferLength"})
    public byte[] getBufferBytes(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * When a {@code receiver} is a wrapped primitive object that utilizes a #ReflectionLibrary,
     * this message will return the delegated value of the receiver.
     *
     * @param receiver the receiver Object
     * @return the delegated value of the receiver
     */
    public Object getDelegatedValue(Object receiver) {
        return receiver;
    }

    /**
     * Equivalent of CPython's {@code PyObject_TypeCheck}. Performs a strict isinstance check
     * without calling to python or considering changed {@code __class__}
     *
     * @param receiver the instance to be checked
     * @param type the class to be checked against
     */
    public boolean typeCheck(Object receiver, Object type) {
        Object clazz = getDefaultNodes().getGetClassNode().execute(receiver);
        return getDefaultNodes().getIsSameTypeNode().execute(clazz, type) ||
                        getDefaultNodes().getIsSubtypeNode().execute(clazz, type);
    }

    /**
     * Implements the logic from {@code PyObject_GetIter}.
     */
    public Object getIteratorWithState(Object receiver, ThreadState state) {
        throw getDefaultNodes().getRaiseNode().raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, receiver);
    }

    /**
     * @see #getIteratorWithState
     */
    public final Object getIterator(Object receiver) {
        return getIteratorWithState(receiver, null);
    }

    /**
     * @see #getIteratorWithState
     */
    public final Object getIteratorWithFrame(Object receiver, VirtualFrame frame) {
        if (profileHasFrame(frame)) {
            return getIteratorWithState(receiver, PArguments.getThreadState(frame));
        } else {
            return getIterator(receiver);
        }
    }

    static final LibraryFactory<PythonObjectLibrary> FACTORY = LibraryFactory.resolve(PythonObjectLibrary.class);

    public static LibraryFactory<PythonObjectLibrary> getFactory() {
        return FACTORY;
    }

    public static PythonObjectLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
