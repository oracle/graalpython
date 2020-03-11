/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.zip.CRC32;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ToByteArrayNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CoerceToIntegerNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

@CoreFunctions(defineModule = "binascii")
public class BinasciiModuleBuiltins extends PythonBuiltins {
    private static final String INCOMPLETE = "Incomplete";
    private static final String ERROR = "Error";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BinasciiModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        String pre = "binascii.";
        PythonAbstractClass[] errorBases = new PythonAbstractClass[]{core.lookupType(PythonBuiltinClassType.ValueError)};
        builtinConstants.put(ERROR, core.factory().createPythonClass(PythonBuiltinClassType.PythonClass, pre + ERROR, errorBases));
        PythonAbstractClass[] incompleteBases = new PythonAbstractClass[]{core.lookupType(PythonBuiltinClassType.Exception)};
        builtinConstants.put(INCOMPLETE, core.factory().createPythonClass(PythonBuiltinClassType.PythonClass, pre + INCOMPLETE, incompleteBases));
    }

    @Builtin(name = "a2b_base64", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class A2bBase64Node extends PythonUnaryBuiltinNode {
        @Specialization
        PBytes doString(String data) {
            return factory().createBytes(b64decode(data));
        }

        @Specialization
        PBytes doBytesLike(VirtualFrame frame, PIBytesLike data,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            return factory().createBytes(b64decode(toBytesNode.execute(frame, data)));
        }

        @TruffleBoundary
        private static byte[] b64decode(String data) {
            return Base64.decode(data);
        }

        @TruffleBoundary
        private byte[] b64decode(byte[] data) {
            try {
                return Base64.decode(new String(data, "ascii"));
            } catch (UnsupportedEncodingException e) {
                throw raise(ValueError);
            }
        }
    }

    @Builtin(name = "a2b_hex", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class A2bHexNode extends PythonBinaryBuiltinNode {
        private ReadAttributeFromObjectNode readAttrNode;

        private PException raise(LazyPythonClass klass, String string) {
            return raise(factory().createBaseException(klass, string, new Object[0]));
        }

        @Specialization
        @TruffleBoundary
        PBytes a2b(PythonModule self, String data) {
            int length = data.length();
            if (length % 2 != 0) {
                throw oddLengthError(self);
            }
            byte[] output = new byte[length / 2];
            for (int i = 0; i < length / 2; i++) {
                try {
                    output[i] = Byte.valueOf(data.substring(i, i + 2), 16);
                } catch (NumberFormatException e) {
                    throw nonHexError(self);
                }
            }
            return factory().createBytes(output);
        }

        private PException oddLengthError(PythonModule self) {
            return raise((LazyPythonClass) getAttrNode().execute(self, ERROR), "Odd-length string");
        }

        private PException nonHexError(PythonModule self) {
            return raise((LazyPythonClass) getAttrNode().execute(self, ERROR), "Non-hexadecimal digit found");
        }

        private ReadAttributeFromObjectNode getAttrNode() {
            if (readAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readAttrNode = insert(ReadAttributeFromObjectNode.create());
            }
            return readAttrNode;
        }
    }

    @Builtin(name = "b2a_base64", minNumOfPositionalArgs = 1, parameterNames = {"data", "newline"})
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class B2aBase64Node extends PythonBinaryBuiltinNode {

        @Child private SequenceStorageNodes.ToByteArrayNode toByteArray;
        @Child private CoerceToIntegerNode castToIntNode;
        @Child private B2aBase64Node recursiveNode;

        private SequenceStorageNodes.ToByteArrayNode getToByteArrayNode() {
            if (toByteArray == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArray = insert(ToByteArrayNodeGen.create());
            }
            return toByteArray;
        }

        private CoerceToIntegerNode getCastToIntNode() {
            if (castToIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIntNode = insert(CoerceToIntegerNode.create(val -> {
                    throw raise(PythonBuiltinClassType.TypeError, "an integer is required (got type %p)", val);
                }));
            }
            return castToIntNode;
        }

        private B2aBase64Node getRecursiveNode() {
            if (recursiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveNode = insert(BinasciiModuleBuiltinsFactory.B2aBase64NodeFactory.create());
            }
            return recursiveNode;
        }

        @TruffleBoundary
        private PBytes b2a(byte[] data, boolean newline) {
            String encode = Base64.encode(data);
            if (newline) {
                return factory().createBytes((encode + "\n").getBytes());
            }
            return factory().createBytes((encode).getBytes());
        }

        @Specialization(guards = "isNoValue(newline)")
        PBytes b2aBytesLike(PIBytesLike data, @SuppressWarnings("unused") PNone newline) {
            return b2aBytesLike(data, 1);
        }

        @Specialization
        PBytes b2aBytesLike(PIBytesLike data, long newline) {
            return b2a(getToByteArrayNode().execute(data.getSequenceStorage()), newline != 0);
        }

        @Specialization
        PBytes b2aBytesLike(PIBytesLike data, PInt newline) {
            return b2a(getToByteArrayNode().execute(data.getSequenceStorage()), !newline.isZero());
        }

        @Specialization
        PBytes b2aBytesLike(VirtualFrame frame, PIBytesLike data, Object newline) {
            return (PBytes) getRecursiveNode().execute(frame, data, getCastToIntNode().execute(newline));
        }

        @Specialization(guards = "isNoValue(newline)")
        PBytes b2aArray(VirtualFrame frame, PArray data, @SuppressWarnings("unused") PNone newline) {
            return b2aArray(frame, data, 1);
        }

        @Specialization
        PBytes b2aArray(PArray data, long newline) {
            return b2a(getToByteArrayNode().execute(data.getSequenceStorage()), newline != 0);
        }

        @Specialization
        PBytes b2aArray(PArray data, PInt newline) {
            return b2a(getToByteArrayNode().execute(data.getSequenceStorage()), !newline.isZero());
        }

        @Specialization
        PBytes b2aArray(VirtualFrame frame, PArray data, Object newline) {
            return (PBytes) getRecursiveNode().execute(frame, data, getCastToIntNode().execute(newline));
        }

        @Specialization(guards = "isNoValue(newline)")
        PBytes b2aMmeory(VirtualFrame frame, PMemoryView data, @SuppressWarnings("unused") PNone newline,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode toBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile) {
            return b2aMemory(frame, data, 1, toBytesNode, isBytesProfile);
        }

        @Specialization
        PBytes b2aMemory(VirtualFrame frame, PMemoryView data, long newline,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode toBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile) {
            Object bytesObj = toBytesNode.executeObject(frame, data);
            if (isBytesProfile.profile(bytesObj instanceof PBytes)) {
                return b2aBytesLike((PBytes) bytesObj, newline);
            }
            throw raise(SystemError, "could not get bytes of memoryview");
        }

        @Specialization
        PBytes b2aMmeory(VirtualFrame frame, PMemoryView data, PInt newline,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode toBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile) {
            return b2aMemory(frame, data, newline.isZero() ? 0 : 1, toBytesNode, isBytesProfile);
        }

        @Specialization
        PBytes b2aMmeory(VirtualFrame frame, PMemoryView data, Object newline) {
            return (PBytes) getRecursiveNode().execute(frame, data, getCastToIntNode().execute(newline));
        }

        @Fallback
        PBytes b2sGeneral(Object data, @SuppressWarnings("unused") Object newline) {
            throw raise(PythonBuiltinClassType.TypeError, "a bytes-like object is required, not '%p'", data);
        }
    }

    @Builtin(name = "b2a_hex", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class B2aHexNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        private static final StringBuilder newStringBuilder() {
            return new StringBuilder();
        }

        @TruffleBoundary
        private static final void sbAppend(StringBuilder sb, String str) {
            sb.append(str);
        }

        @TruffleBoundary
        private static final String sbToString(StringBuilder sb) {
            return sb.toString();
        }

        @TruffleBoundary
        private static final String toHexString(byte b) {
            return Integer.toHexString(b);
        }

        @Specialization
        String b2a(PBytes data,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArray) {
            byte[] bytes = toByteArray.execute(data.getSequenceStorage());
            StringBuilder sb = newStringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                String hexString = toHexString(bytes[i]);
                if (hexString.length() < 2) {
                    sbAppend(sb, "0");
                }
                sbAppend(sb, hexString);
            }
            return sbToString(sb);
        }
    }

    @Builtin(name = "crc32", minNumOfPositionalArgs = 1, parameterNames = {"data", "crc"})
    @GenerateNodeFactory
    abstract static class Crc32Node extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(crc)")
        long b2a(PBytes data, @SuppressWarnings("unused") PNone crc,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArray) {
            return getCrcValue(toByteArray.execute(data.getSequenceStorage()));
        }

        @TruffleBoundary
        private static final long getCrcValue(byte[] bytes) {
            CRC32 crc32 = new CRC32();
            crc32.update(bytes);
            return crc32.getValue();
        }
    }

    @Builtin(name = "hexlify", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class HexlifyNode extends B2aHexNode {
    }

    @Builtin(name = "unhexlify", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UnhexlifyNode extends A2bHexNode {
    }
}
