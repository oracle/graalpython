/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;

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
     * @return {@code true} if the object has a {@code __dict__} attribute
     */
    public boolean hasDict(Object receiver) {
        return false;
    }

    /**
     * Note that not returning a value from this message does not mean that the object cannot have a
     * {@code __dict__}. It may be that the object has inlined the representation of its
     * {@code __dict__} and thus no object is available, yet.
     *
     * @return the value in {@code __dict__} or {@code null}, if there is none.
     * @see #hasDict
     */
    @Abstract(ifExported = "hasDict")
    public PHashingCollection getDict(Object receiver) {
        return null;
    }

    /**
     * Set the {@code __dict__} attribute of the object
     */
    @Abstract(ifExported = "hasDict")
    public void setDict(Object receiver, PHashingCollection dict) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * @return the Python type of the receiver
     */
    @Abstract
    public LazyPythonClass getLazyPythonClass(Object receiver) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError(receiver.getClass().getCanonicalName());
    }

    /**
     * Sets the {@code __class__} value of the receiver. This is not supported for all kinds of
     * objects.
     */
    public void setLazyPythonClass(Object receiver, LazyPythonClass cls) {
        PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, "__class__ assignment only supported for heap types or ModuleType subclasses, not '%p'", receiver);
    }

    /**
     * Checks whether the receiver is a Python iterable object. As described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model</a> and
     * <a href="https://docs.python.org/3/library/collections.abc.html">Abstract Base Classes for
     * Containers</a>
     *
     * <br>
     * Specifically the default implementation checks for the implementation of the <b>__iter__</b>
     * special method. If not defined, it will also check for iterable objects that implement the
     * following special methods: <b>
     * <ul>
     * <li>__getitem__</li>
     * <li>__next__</li>
     * </ul>
     * </b>
     *
     * @param receiver the receiver Object
     * @return True if object is iterable
     */
    public boolean isIterable(Object receiver) {
        return false;
    }

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
     * @return True if object is callable
     */
    public boolean isCallable(Object receiver) {
        return false;
    }

    /**
     * Checks whether the receiver is a Python context manager. As described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model </a>,
     * <a href="https://www.python.org/dev/peps/pep-0343/">PEP 343</a> and
     * <a href="https://docs.python.org/3/library/collections.abc.html">Abstract Base Classes for
     * Containers</a>
     *
     * <br>
     * Specifically the default implementation checks for the implementation of the following
     * special methods: <b>
     * <ul>
     * <li>__enter__</li>
     * <li>__exit__</li>
     * </ul>
     * </b>
     *
     * @param receiver the receiver Object
     * @return True if object is a context manager
     */
    public boolean isContextManager(Object receiver) {
        return false;
    }

    /**
     * Checks whether the receiver is a Python hashable object. As described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model</a> and
     * <a href="https://docs.python.org/3/library/collections.abc.html">Abstract Base Classes for
     * Containers</a>
     *
     * <br>
     * Specifically the default implementation checks for the implementation of the <b>__hash__</b>
     * special method.
     *
     * @param receiver the receiver Object
     * @return True if object is hashable
     */
    public boolean isHashable(Object receiver) {
        return false;
    }

    /**
     * Returns the Python hash to use for the receiver. The {@code threadState} argument must be the
     * result of a {@link PArguments#getThreadState} call. It ensures that we can use fastcalls and
     * pass the thread state in the frame arguments.
     */
    public long hashWithState(Object receiver, ThreadState threadState) {
        if (threadState == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new AbstractMethodError(receiver.getClass().getCanonicalName());
        }
        return hash(receiver);
    }

    /**
     * Potentially slower way to get the Python hash for the receiver. If a Python {@link Frame} is
     * available to the caller, {@link #hashWithState} should be preferred.
     */
    public long hash(Object receiver) {
        return hashWithState(receiver, null);
    }

    @SuppressWarnings("static-method")
    public final long hash(boolean receiver) {
        return DefaultPythonBooleanExports.hash(receiver);
    }

    @SuppressWarnings("static-method")
    public final long hash(int receiver) {
        return DefaultPythonIntegerExports.hash(receiver);
    }

    @SuppressWarnings("static-method")
    public final long hash(long receiver) {
        return DefaultPythonLongExports.hash(receiver);
    }

    @SuppressWarnings("static-method")
    public final long hash(double receiver) {
        return DefaultPythonDoubleExports.hash(receiver);
    }

    private static class DefaultNodes extends Node {
        private static final byte REVERSE_COMP = 0b001;
        private static final byte LEFT_COMPARE = 0b010;
        private static final byte SUBT_COMPARE = 0b100;

        @Child private IsSubtypeNode isSubtype;
        @Child private IsSameTypeNode isSameType;
        @CompilationFinal byte state = 0;

        protected IsSubtypeNode getIsSubtypeNode() {
            if (isSubtype == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                isSubtype = insert(IsSubtypeNode.create());
            }
            return isSubtype;
        }

        protected IsSameTypeNode getIsSameTypeNode() {
            if (isSameType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSameType = insert(IsSameTypeNodeGen.create());
            }
            return isSameType;
        }

        protected void enterReverseCompare() {
            if ((state & REVERSE_COMP) == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                state |= REVERSE_COMP;
            }
        }

        protected void enterLeftCompare() {
            if ((state & LEFT_COMPARE) == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                state |= LEFT_COMPARE;
            }
        }

        protected void enterSubtypeCompare() {
            if ((state & SUBT_COMPARE) == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                state |= SUBT_COMPARE;
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
            return new DefaultNodes();
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

    private final DefaultNodes getDefaultNodes() {
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

        LazyPythonClass leftClass = getLazyPythonClass(receiver);
        LazyPythonClass rightClass = otherLibrary.getLazyPythonClass(other);
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
     * @see #equalsWithState
     */
    public boolean equals(Object receiver, Object other, PythonObjectLibrary otherLibrary) {
        return equalsWithState(receiver, other, otherLibrary, null);
    }

    /**
     * Compare {@code receiver} to {@code other} using {@code __eq__}.
     *
     * @param threadState may be {@code null}
     * @return 0 if not equal, 1 if equal, -1 if {@code __eq__} returns {@code NotImplemented}
     */
    public abstract int equalsInternal(Object receiver, Object other, ThreadState threadState);

    /**
     * Checks whether the receiver is a Python an indexable object. As described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model</a> and
     * <a href="https://docs.python.org/3/library/collections.abc.html">Abstract Base Classes for
     * Containers</a>.
     *
     * <br>
     * Specifically the default implementation checks for the implementation of the <b>__index__</b>
     * special method. This is analogous to {@code PyIndex_Check} in {@code abstract.h}
     *
     * @param receiver the receiver Object
     * @return True if object is indexable
     */
    public boolean canBeIndex(Object receiver) {
        return false;
    }

    /**
     * Coerces the receiver into an index just like {@code PyNumber_AsIndex}.
     *
     * Return a Python int from the receiver. Raise TypeError if the result is not an int or if the
     * object cannot be interpreted as an index.
     */
    public Object asIndexWithState(Object receiver, ThreadState threadState) {
        if (threadState == null) {
            throw PRaiseNode.getUncached().raiseIntegerInterpretationError(receiver);
        }
        return asIndex(receiver);
    }

    /**
     * @see #asIndexWithState
     */
    public Object asIndex(Object receiver) {
        return asIndexWithState(receiver, null);
    }

    /**
     * Return the file system path representation of the object. If the object is str or bytes, then
     * allow it to pass through. If the object defines __fspath__(), then return the result of that
     * method. All other types raise a TypeError.
     */
    public String asPathWithState(Object receiver, ThreadState threadState) {
        if (threadState == null) {
            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, "expected str, bytes or os.PathLike object, not %p", receiver);
        }
        return asPath(receiver);
    }

    /**
     * @see #asPathWithState
     */
    public String asPath(Object receiver) {
        return asPathWithState(receiver, null);
    }

    /**
     * Coerces the receiver into an Python string just like {@code PyObject_Str}.
     * 
     * Return a Python string from the receiver. Raise TypeError if the result is not a string.
     */
    public Object asPStringWithState(Object receiver, ThreadState threadState) {
        if (threadState == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new AbstractMethodError(receiver.getClass().getCanonicalName());
        }
        return asPString(receiver);
    }

    /**
     * @see #asPStringWithState
     */
    public Object asPString(Object receiver) {
        return asPStringWithState(receiver, null);
    }

    /**
     * Coerces a given primitive or object to a file descriptor (i.e. Java {@code int}) just like
     * {@code PyObject_AsFileDescriptor} does.
     * 
     * Converted to int if possible, or if the object defines __fileno__(), then return the result
     * of that method. Raise TypeError otherwise.
     */
    public int asFileDescriptorWithState(Object receiver, ThreadState threadState) {
        if (threadState == null) {
            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, "argument must be an int, or have a fileno() method.");
        }
        return asFileDescriptor(receiver);
    }

    /**
     * @see #asFileDescriptorWithState
     */
    public int asFileDescriptor(Object receiver) {
        return asFileDescriptorWithState(receiver, null);
    }

    /**
     * Looks up an attribute for the given receiver like {@code PyObject_LookupAttr}.
     * 
     * @param receiver
     * @param name attribute name
     * @param inheritedOnly determines whether the lookup should start on the class or on the object
     */
    public Object lookupAttribute(Object receiver, String name, boolean inheritedOnly) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError(receiver.getClass().getCanonicalName());
    }

    /**
     * @see #lookupAttribute
     */
    public final Object lookupAttribute(Object receiver, String name) {
        return lookupAttribute(receiver, name, false);
    }

    /**
     * Checks whether the receiver can be coerced to a Java double.
     *
     * <br>
     * Specifically the default implementation checks for the implementation of the <b>__index__</b>
     * and <b>__float__</b> special methods. This is analogous to the checks made in
     * {@code PyFloat_AsDouble} in {@code floatobject.c}
     *
     * @param receiver the receiver Object
     * @return True if object can be converted to a java double
     */
    @Abstract(ifExported = {"asJavaDoubleWithState", "asJavaDouble"})
    public boolean canBeJavaDouble(Object receiver) {
        return false;
    }

    /**
     * Coerces a given primitive or object to a Java {@code double}. This method follows the
     * semantics of CPython's function {@code PyFloat_AsDouble}.
     */
    public double asJavaDoubleWithState(Object receiver, ThreadState threadState) {
        if (threadState == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new AbstractMethodError(receiver.getClass().getCanonicalName());
        }
        return asJavaDouble(receiver);
    }

    /**
     * @see #asJavaDoubleWithState
     * @see #asJavaDoubleWithStateAndErrHandler
     */
    public double asJavaDouble(Object receiver) {
        return asJavaDoubleWithState(receiver, null);
    }

    /**
     * Coerces the receiver into an index-sized integer, using the same mechanism as
     * {@code PyNumber_AsSsize_t}:
     * <ol>
     * <li>Call <code>__index__</code> if the object is not already a Python int (resp.
     * <code>PyNumber_Index</code>)</li>
     * <li>Do a hard cast to long as per <code>PyLong_AsSsize_t</code></li>
     * </ol>
     *
     * @return <code>-1</code> if the cast fails or overflows the <code>int</code> range
     */
    public int asSizeWithState(Object receiver, LazyPythonClass errorType, ThreadState threadState) {
        if (threadState == null) {
            // this will very likely always raise an integer interpretation error in
            // asIndexWithState
            long result = CastToJavaLongNode.getUncached().execute(asIndexWithState(receiver, null));
            int intResult = (int) result;
            if (intResult == result) {
                return intResult;
            } else if (errorType == null) {
                return result < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            } else {
                throw PRaiseNode.getUncached().raiseNumberTooLarge(errorType, result);
            }
        }
        return asSize(receiver, errorType);
    }

    /**
     * @see #asSizeWithState(Object, LazyPythonClass, ThreadState)
     */
    public final int asSizeWithState(Object receiver, ThreadState threadState) {
        return asSizeWithState(receiver, PythonBuiltinClassType.OverflowError, threadState);
    }

    /**
     * @see #asSizeWithState(Object, LazyPythonClass, ThreadState)
     */
    public int asSize(Object receiver, LazyPythonClass errorClass) {
        return asSizeWithState(receiver, errorClass, null);
    }

    /**
     * @see #asSizeWithState(Object, LazyPythonClass, ThreadState)
     */
    public final int asSize(Object receiver) {
        return asSize(receiver, PythonBuiltinClassType.OverflowError);
    }

    /**
     * Checks whether the receiver is a Python sequence. As described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model</a> and
     * <a href="https://docs.python.org/3/library/collections.abc.html">Abstract Base Classes for
     * Containers</a>
     *
     * <br>
     * See {@link #isSequenceType(Object)}
     *
     * @param receiver the receiver Object
     * @return True if object is a Python sequence object
     */
    public boolean isSequence(Object receiver) {
        return false;
    }

    /**
     * Checks whether this object should be interpreted as {@code
     * true}-ish. Mimics the coercion behaviour of {@code PyObject_IsTrue}, and thus uses both
     * {@code slot_nb_bool} coercion and {@link #length}/{@link #lengthWithState}.
     */
    public boolean isTrueWithState(Object receiver, ThreadState state) {
        if (state == null) {
            return true;
        }
        return isTrue(receiver);
    }

    /**
     * @see #isTrueWithState
     */
    public boolean isTrue(Object receiver) {
        return isTrueWithState(receiver, null);
    }

    /**
     * Implements the logic from {@code PyObject_Size} (to which {@code
     * PySequence_Length} is an alias). The logic which is to try a) {@code
     * sq_length} and b) {@code mp_length}. Each of these can also be reached via
     * {@code PySequence_Length} or {@code PyMapping_Length}, respectively.
     *
     * The implementation for {@code slot_sq_length} is to call {@code __len__} and then to convert
     * it to an index and a size, making sure it's >=0. {@code slot_mp_length} is just an alias for
     * that slot.
     */
    public int lengthWithState(Object receiver, ThreadState state) {
        if (state == null) {
            throw PRaiseNode.getUncached().raiseHasNoLength(receiver);
        }
        return length(receiver);
    }

    /**
     * @see #lengthWithState
     */
    public int length(Object receiver) {
        return lengthWithState(receiver, null);
    }

    /**
     * Checks whether the receiver is a Python mapping. As described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model</a> and
     * <a href="https://docs.python.org/3/library/collections.abc.html">Abstract Base Classes for
     * Containers</a>
     *
     * <br>
     * See {@link #isMappingType(Object)}
     *
     * @param receiver the receiver Object
     * @return True if object is a Python mapping object
     */
    public boolean isMapping(Object receiver) {
        return false;
    }

    /**
     * Checks whether the receiver is a Python sequence type. As described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model</a> and
     * <a href="https://docs.python.org/3/library/collections.abc.html">Abstract Base Classes for
     * Containers</a>
     *
     * <br>
     * Specifically the default implementation checks for the implementation of the following
     * special methods: <b>
     * <ul>
     * <li>__getitem__</li>
     * <li>__len__</li>
     * </ul>
     * </b>
     *
     * @param receiver the receiver Object
     * @return True if a sequence type
     */
    public boolean isSequenceType(Object receiver) {
        return false;
    }

    /**
     * Checks whether the receiver is a Python mapping type. As described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model</a> and
     * <a href="https://docs.python.org/3/library/collections.abc.html">Abstract Base Classes for
     * Containers</a>
     *
     * <br>
     * Specifically the default implementation checks whether the receiver
     * {@link #isSequenceType(Object)} and for the implementation of the following special methods:
     * <b>
     * <ul>
     * <li>keys</li>
     * <li>items</li>
     * <li>values</li>
     * </ul>
     * </b>
     *
     * @param receiver the receiver Object
     * @return True if a mapping type
     */
    public boolean isMappingType(Object receiver) {
        return false;
    }

    @Abstract(ifExported = {"getBufferBytes", "getBufferLength"})
    public boolean isBuffer(Object receiver) {
        return false;
    }

    @Abstract(ifExported = {"isBuffer", "getBufferBytes"})
    public int getBufferLength(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = {"isBuffer", "getBufferLength"})
    public byte[] getBufferBytes(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Checks whether the receiver is a Foreign Object.
     *
     * @see DefaultPythonObjectExports#isForeignObject(Object,
     *      com.oracle.truffle.api.interop.InteropLibrary) {@code DefaultPythonObjectExports}
     *      implements the logic of how an unknown object is being checked.
     *
     * @param receiver
     * @return True if the receiver is a Foreign Object
     */

    public boolean isForeignObject(Object receiver) {
        return false;
    }

    /**
     * When a {@code receiver} is a wrapped primitive object that utilizes a #ReflectionLibrary, the
     * value will appear here as primitive contrary to the value in the call cite which should
     * represent the {@code receiverOrigin}
     *
     * @param receiver the receiver Object
     * @param receiverOrigin also the receiver Object
     * @return True if there has been a reflection
     */
    public boolean isRefelectedObject(Object receiver, Object receiverOrigin) {
        return receiver != receiverOrigin;
    }

    public static boolean checkIsIterable(PythonObjectLibrary library, ContextReference<PythonContext> contextRef, VirtualFrame frame, Object object, IndirectCallNode callNode) {
        PythonContext context = contextRef.get();
        Object state = IndirectCallContext.enter(frame, context, callNode);
        try {
            return library.isIterable(object);
        } finally {
            IndirectCallContext.exit(frame, context, state);
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
