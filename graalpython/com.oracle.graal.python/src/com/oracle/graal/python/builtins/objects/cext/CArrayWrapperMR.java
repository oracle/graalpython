/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.objects.cext.CArrayWrapperMRFactory.GetTypeIDNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CExtBaseNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMR.InvalidateNativeObjectsAllManagedNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = CArrayWrapper.class)
public class CArrayWrapperMR {

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        public char access(CStringWrapper object, int idx) {
            String s = object.getString();
            if (idx >= 0 && idx < s.length()) {
                return s.charAt(idx);
            } else if (idx == s.length()) {
                return '\0';
            }
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(Integer.toString(idx));
        }

        public char access(CStringWrapper object, long idx) {
            try {
                return access(object, PInt.intValueExact(idx));
            } catch (ArithmeticException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(Long.toString(idx));
            }
        }

        public byte access(CByteArrayWrapper object, int idx) {
            byte[] arr = object.getByteArray();
            if (idx >= 0 && idx < arr.length) {
                return arr[idx];
            } else if (idx == arr.length) {
                return (byte) 0;
            }
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(Integer.toString(idx));
        }

        public byte access(CByteArrayWrapper object, long idx) {
            try {
                return access(object, PInt.intValueExact(idx));
            } catch (ArithmeticException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(Long.toString(idx));
            }
        }
    }

    @SuppressWarnings("unknown-message")
    @Resolve(message = "com.oracle.truffle.llvm.spi.GetDynamicType")
    abstract static class GetDynamicTypeNode extends Node {
        @Child private GetTypeIDNode getTypeId = GetTypeIDNodeGen.create();

        public Object access(CStringWrapper object) {
            return getTypeId.execute(object);
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    abstract static class GetTypeIDNode extends CExtBaseNode {

        @Child private PCallCapiFunction callUnaryNode;

        @CompilationFinal private TruffleObject funGetByteArrayTypeID;

        public abstract Object execute(Object delegate);

        @Specialization
        Object doTuple(CStringWrapper object) {
            return callGetByteArrayTypeID(object.getString().length());
        }

        private Object callGetByteArrayTypeID(long len) {
            if (callUnaryNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callUnaryNode = insert(PCallCapiFunction.create(NativeCAPISymbols.FUN_GET_BYTE_ARRAY_TYPE_ID));
            }
            return callUnaryNode.call(funGetByteArrayTypeID, new Object[]{len});
        }
    }

    @Resolve(message = "HAS_SIZE")
    abstract static class HasSizeNode extends Node {
        boolean access(@SuppressWarnings("unused") CArrayWrapper obj) {
            return true;
        }
    }

    @Resolve(message = "GET_SIZE")
    abstract static class GetSizeNode extends Node {
        long access(CStringWrapper obj) {
            return obj.getString().length();
        }

        int access(CByteArrayWrapper obj) {
            return obj.getByteArray().length;
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        @Child private CExtNodes.IsPointerNode pIsPointerNode = CExtNodes.IsPointerNode.create();

        boolean access(CArrayWrapper obj) {
            return pIsPointerNode.execute(obj);
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private Node asPointerNode;

        long access(CArrayWrapper obj) {
            // the native pointer object must either be a TruffleObject or a primitive
            Object nativePointer = obj.getNativePointer();
            if (nativePointer instanceof TruffleObject) {
                if (asPointerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asPointerNode = insert(Message.AS_POINTER.createNode());
                }
                try {
                    return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) nativePointer);
                } catch (UnsupportedMessageException e) {
                    throw e.raise();
                }
            }
            return (long) nativePointer;

        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private CExtNodes.AsCharPointer asCharPointerNode;
        @Child private InvalidateNativeObjectsAllManagedNode invalidateNode = InvalidateNativeObjectsAllManagedNode.create();

        Object access(CArrayWrapper obj) {
            invalidateNode.execute();
            if (!obj.isNative()) {
                if (asCharPointerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asCharPointerNode = insert(CExtNodes.AsCharPointer.create());
                }
                Object ptr = asCharPointerNode.execute(obj.getDelegate());
                obj.setNativePointer(ptr);
            }
            return obj;
        }
    }

}
