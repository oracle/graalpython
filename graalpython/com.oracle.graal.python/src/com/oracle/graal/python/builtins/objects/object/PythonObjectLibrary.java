/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;

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
     * Checks whether the receiver is a Python an indexable object. As described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model</a> and
     * <a href="https://docs.python.org/3/library/collections.abc.html">Abstract Base Classes for
     * Containers</a>
     *
     * <br>
     * Specifically the default implementation checks for the implementation of the <b>__index__</b>
     * special method.
     *
     * @param receiver the receiver Object
     * @return True if object is indexable
     */
    public boolean canBeIndex(Object receiver) {
        return false;
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

    public static boolean checkIsIterable(PythonObjectLibrary library, ContextReference<PythonContext> contextRef, VirtualFrame frame, Object object, Node callNode) {
        PythonContext context = contextRef.get();
        PException caughtException = IndirectCallContext.enter(frame, context, callNode);
        try {
            return library.isIterable(object);
        } finally {
            IndirectCallContext.exit(frame, context, caughtException);
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
