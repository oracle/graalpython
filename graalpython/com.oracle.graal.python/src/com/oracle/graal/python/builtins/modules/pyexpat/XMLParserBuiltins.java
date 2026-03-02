/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.pyexpat;

import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_READ;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.Attributes2;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.Locator2;
import org.xml.sax.helpers.DefaultHandler;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.XMLParser)
public final class XMLParserBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = XMLParserBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return XMLParserBuiltinsFactory.getFactories();
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CreateParserNode extends Node {
        abstract Object execute(Node inliningTarget, Object namespaceSeparatorObj, Object intern);

        @Specialization
        static Object doIt(Node inliningTarget, Object namespaceSeparatorObj, Object intern,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached PRaiseNode raiseNode) {
            TruffleString sep = null;
            if (!(namespaceSeparatorObj instanceof PNone)) {
                if (!unicodeCheckNode.execute(inliningTarget, namespaceSeparatorObj)) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.NAMESPACE_SEPARATOR_MUST_BE);
                }
                TruffleString namespaceSeparator = castToTruffleStringNode.castKnownString(inliningTarget, namespaceSeparatorObj);
                int len = codePointLengthNode.execute(namespaceSeparator, TS_ENCODING);
                if (len > 1) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.NAMESPACE_SEPARATOR_MUST_BE);
                }
                sep = namespaceSeparator;
            }
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PXMLParser parser = PFactory.createXMLParser(PythonBuiltinClassType.XMLParser, PythonBuiltinClassType.XMLParser.getInstanceShape(language), sep);
            parser.setIntern(intern);
            return parser;
        }
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "xmlparser", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class NewNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doIt(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached CreateParserNode createParserNode) {
            return createParserNode.execute(inliningTarget, arg2 == PNone.NO_VALUE ? PNone.NONE : arg2, PNone.NONE);
        }
    }

    @Builtin(name = "buffer_text", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class BufferTextNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static boolean get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.isBufferText();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PXMLParser self, Object value,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            self.setBufferText(isTrueNode.execute(frame, value));
            self.setBufferUsed(0);
            return PNone.NONE;
        }
    }

    @Builtin(name = "namespace_prefixes", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class NamespacePrefixesNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static boolean get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.isNamespacePrefixes();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PXMLParser self, Object value,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            self.setNamespacePrefixes(isTrueNode.execute(frame, value));
            return PNone.NONE;
        }
    }

    @Builtin(name = "ordered_attributes", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class OrderedAttributesNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static boolean get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.isOrderedAttributes();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PXMLParser self, Object value,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            self.setOrderedAttributes(isTrueNode.execute(frame, value));
            return PNone.NONE;
        }
    }

    @Builtin(name = "specified_attributes", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class SpecifiedAttributesNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static boolean get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.isSpecifiedAttributes();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PXMLParser self, Object value,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            self.setSpecifiedAttributes(isTrueNode.execute(frame, value));
            return PNone.NONE;
        }
    }

    @Builtin(name = "buffer_size", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class BufferSizeNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static int get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getBufferSize();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(VirtualFrame frame, PXMLParser self, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached PyLongAsLongNode asLongNode) {
            if (!longCheckNode.execute(inliningTarget, value)) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_INTEGER, "buffer_size");
            }
            long newBufferSize = asLongNode.execute(frame, inliningTarget, value);
            if (newBufferSize <= 0) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.S_MUST_BE_GREATER_THAN_ZERO, "buffer_size");
            }
            if (newBufferSize != (int) newBufferSize) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, value);
            }
            self.setBufferSize((int) newBufferSize);
            return PNone.NONE;
        }
    }

    @Builtin(name = "buffer_used", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BufferUsedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int get(PXMLParser self) {
            return self.getBufferUsed();
        }
    }

    @Builtin(name = "CurrentByteIndex", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class CurrentByteIndexNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int get(PXMLParser self) {
            return self.getCurrentByteIndex();
        }
    }

    @Builtin(name = "CurrentLineNumber", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class CurrentLineNumberNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int get(PXMLParser self) {
            return self.getCurrentLineNumber();
        }
    }

    @Builtin(name = "CurrentColumnNumber", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class CurrentColumnNumberNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int get(PXMLParser self) {
            return self.getCurrentColumnNumber();
        }
    }

    @Builtin(name = "ErrorByteIndex", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ErrorByteIndexNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int get(PXMLParser self) {
            return self.getErrorByteIndex();
        }
    }

    @Builtin(name = "ErrorLineNumber", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ErrorLineNumberNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int get(PXMLParser self) {
            return self.getErrorLineNumber();
        }
    }

    @Builtin(name = "ErrorColumnNumber", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ErrorColumnNumberNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int get(PXMLParser self) {
            return self.getErrorColumnNumber();
        }
    }

    @Builtin(name = "intern", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class InternNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PXMLParser self) {
            return self.getIntern();
        }
    }

    @Builtin(name = "StartElementHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class StartElementHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getStartElementHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setStartElementHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "EndElementHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class EndElementHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getEndElementHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setEndElementHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "ProcessingInstructionHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class ProcessingInstructionHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getProcessingInstructionHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setProcessingInstructionHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "CharacterDataHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class CharacterDataHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getCharacterDataHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setCharacterDataHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "UnparsedEntityDeclHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class UnparsedEntityDeclHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getUnparsedEntityDeclHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setUnparsedEntityDeclHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "NotationDeclHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class NotationDeclHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getNotationDeclHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setNotationDeclHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "StartNamespaceDeclHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class StartNamespaceDeclHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getStartNamespaceDeclHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setStartNamespaceDeclHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "EndNamespaceDeclHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class EndNamespaceDeclHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getEndNamespaceDeclHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setEndNamespaceDeclHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "CommentHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class CommentHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getCommentHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setCommentHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "StartCdataSectionHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class StartCdataSectionHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getStartCdataSectionHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setStartCdataSectionHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "EndCdataSectionHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class EndCdataSectionHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getEndCdataSectionHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setEndCdataSectionHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "DefaultHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class DefaultHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getDefaultHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setDefaultHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "DefaultHandlerExpand", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class DefaultHandlerExpandNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getDefaultHandlerExpand();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setDefaultHandlerExpand(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "NotStandaloneHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class NotStandaloneHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getNotStandaloneHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setNotStandaloneHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "ExternalEntityRefHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class ExternalEntityRefHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getExternalEntityRefHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setExternalEntityRefHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "StartDoctypeDeclHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class StartDoctypeDeclHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getStartDoctypeDeclHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setStartDoctypeDeclHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "EndDoctypeDeclHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class EndDoctypeDeclHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getEndDoctypeDeclHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setEndDoctypeDeclHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "EntityDeclHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class EntityDeclHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getEntityDeclHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setEntityDeclHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "XmlDeclHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class XmlDeclHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getXmlDeclHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setXmlDeclHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "ElementDeclHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class ElementDeclHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getElementDeclHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setElementDeclHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "AttlistDeclHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class AttlistDeclHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getAttlistDeclHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setAttlistDeclHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "SkippedEntityHandler", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class SkippedEntityHandlerNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(PXMLParser self, @SuppressWarnings("unused") PNone value) {
            return self.getSkippedEntityHandler();
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set(PXMLParser self, Object value) {
            self.setSkippedEntityHandler(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "Parse", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, parameterNames = {"$cls", "data", "isfinal"})
    @ArgumentClinic(name = "isfinal", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class ParseNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        int parse(VirtualFrame frame, PXMLParser self, Object data, boolean isFinal,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData) {
            Object savedState = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                doParse(self, data, isFinal);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, savedState);
            }
            return 1;
        }

        @TruffleBoundary
        private void doParse(PXMLParser self, Object data, boolean isFinal) {
            if (self.isFinished()) {
                throw raiseExpatError(this, ErrorMessages.PARSING_FINISHED, PXMLParser.XML_ERROR_FINISHED, 0, 1, 0);
            }
            appendData(self, data);
            parseNow(self, !isFinal);
            if (isFinal) {
                self.setFinished(true);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return XMLParserBuiltinsClinicProviders.ParseNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "ParseFile", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ParseFileNode extends PythonBinaryBuiltinNode {
        @Specialization
        int parseFile(VirtualFrame frame, PXMLParser self, Object file,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData) {
            Object savedState = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                doParseFile(self, file);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, savedState);
            }
            self.setFinished(true);
            return 1;
        }

        private void doParseFile(PXMLParser self, Object file) {
            if (self.isFinished()) {
                throw raiseExpatError(this, ErrorMessages.PARSING_FINISHED, PXMLParser.XML_ERROR_FINISHED, 0, 1, 0);
            }
            PythonFileInputStream stream = new PythonFileInputStream(file, self.getBufferSize());
            parseNow(self, false, new InputSource(stream), stream::getBytesRead);
        }
    }

    @Builtin(name = "SetParamEntityParsing", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "flag"})
    @ArgumentClinic(name = "flag", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SetParamEntityParsingNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static int set(PXMLParser self, int value) {
            self.setParamEntityParsing(value);
            return 1;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return XMLParserBuiltinsClinicProviders.SetParamEntityParsingNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "UseForeignDTD", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"$cls", "flag"})
    @ArgumentClinic(name = "flag", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class UseForeignDTDNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static PNone set(PXMLParser self, boolean flag) {
            self.setForeignDTD(flag);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return XMLParserBuiltinsClinicProviders.UseForeignDTDNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "GetReparseDeferralEnabled", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetReparseDeferralEnabledNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean get(PXMLParser self) {
            return self.isReparseDeferralEnabled();
        }
    }

    @Builtin(name = "SetReparseDeferralEnabled", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "enabled"})
    @ArgumentClinic(name = "enabled", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class SetReparseDeferralEnabledNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static PNone set(PXMLParser self, boolean value) {
            self.setReparseDeferralEnabled(value);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return XMLParserBuiltinsClinicProviders.SetReparseDeferralEnabledNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "SetBase", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "base"})
    @ArgumentClinic(name = "base", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class SetBaseNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static PNone set(PXMLParser self, TruffleString base) {
            self.setBase(base);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return XMLParserBuiltinsClinicProviders.SetBaseNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "GetBase", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetBaseNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PXMLParser self) {
            return self.getBase() == null ? PNone.NONE : self.getBase();
        }
    }

    @Builtin(name = "ExternalEntityParserCreate", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class ExternalEntityParserCreateNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object create(PXMLParser self, @SuppressWarnings("unused") Object context, @SuppressWarnings("unused") Object encoding,
                        @Bind Node inliningTarget,
                        @Cached CreateParserNode createParserNode) {
            return createParserNode.execute(inliningTarget, self.getNamespaceSeparator() == null ? PNone.NONE : self.getNamespaceSeparator(), self.getIntern());
        }
    }

    @TruffleBoundary
    private static int appendData(PXMLParser parser, Object data) {
        byte[] existing = parser.getData();
        int existingLen = existing.length;
        byte[] chunk = asByteArray(data);
        int chunkLen = chunk.length;
        if (chunkLen == 0) {
            return 0;
        }
        byte[] merged = Arrays.copyOf(existing, existingLen + chunkLen);
        System.arraycopy(chunk, 0, merged, existingLen, chunkLen);
        parser.setData(merged);
        return chunkLen;
    }

    @TruffleBoundary
    private static byte[] asByteArray(Object data) {
        if (PyUnicodeCheckNode.executeUncached(data)) {
            TruffleString utf8 = CastToTruffleStringNode.castKnownStringUncached(data).switchEncodingUncached(TruffleString.Encoding.UTF_8);
            InternalByteArray internalByteArray = utf8.getInternalByteArrayUncached(TruffleString.Encoding.UTF_8);
            int chunkLen = internalByteArray.getLength();
            if (chunkLen == 0) {
                return EMPTY_BYTE_ARRAY;
            }
            return Arrays.copyOfRange(internalByteArray.getArray(), internalByteArray.getOffset(), internalByteArray.getOffset() + chunkLen);
        }
        Object buffer = PythonBufferAcquireLibrary.getUncached().acquireReadonly(data);
        try {
            PythonBufferAccessLibrary accessLib = PythonBufferAccessLibrary.getUncached();
            int chunkLen = accessLib.getBufferLength(buffer);
            if (chunkLen == 0) {
                return EMPTY_BYTE_ARRAY;
            }
            byte[] bytes = new byte[chunkLen];
            accessLib.readIntoByteArray(buffer, 0, bytes, 0, chunkLen);
            return bytes;
        } finally {
            PythonBufferAccessLibrary.getUncached().release(buffer);
        }
    }

    @TruffleBoundary
    private static void parseNow(PXMLParser parser, boolean swallowErrors) {
        int byteLen = parser.getData().length;
        parseNow(parser, swallowErrors, new InputSource(new ByteArrayInputStream(parser.getData())), () -> byteLen);
    }

    @TruffleBoundary
    private static void parseNow(PXMLParser parser, boolean swallowErrors, InputSource source, ByteIndexSupplier byteIndexSupplier) {
        final class Handler extends DefaultHandler2 {
            int line = 1;
            int col;
            int eventOrdinal;
            Locator locator;
            boolean keepCurrentPositionForNextCall;

            @Override
            public void setDocumentLocator(Locator locator) {
                this.locator = locator;
            }

            Handler() {
                if (parser.isForeignDTD() && parser.getParamEntityParsing() == PXMLParser.XML_PARAM_ENTITY_PARSING_ALWAYS) {
                    call(parser.getExternalEntityRefHandler(), PNone.NONE, PNone.NONE, PNone.NONE, PNone.NONE);
                }
            }

            @Override
            public void startPrefixMapping(String prefix, String uri) {
                call(parser.getStartNamespaceDeclHandler(), toTs(prefix), toTs(uri));
            }

            @Override
            public void endPrefixMapping(String prefix) {
                call(parser.getEndNamespaceDeclHandler(), toTs(prefix));
            }

            @Override
            public void processingInstruction(String target, String data) {
                call(parser.getProcessingInstructionHandler(), toTs(target), toTs(data));
            }

            @Override
            public void startDocument() {
                if (locator instanceof Locator2 locator2) {
                    call(parser.getXmlDeclHandler(), toOptionalTs(locator2.getXMLVersion()), toOptionalTs(locator2.getEncoding()), -1);
                }
            }

            @Override
            public void startDTD(String name, String publicId, String systemId) {
                // We conservatively report an internal subset. This matches minidom builder
                // expectations and enables DTD callback wiring for entity/notation handling.
                call(parser.getStartDoctypeDeclHandler(), toTs(name), toTs(systemId), toTs(publicId), 1);
            }

            @Override
            public void endDTD() {
                call(parser.getEndDoctypeDeclHandler());
            }

            @Override
            public void internalEntityDecl(String name, String value) {
                boolean isParameterEntity = name != null && name.startsWith("%");
                call(parser.getEntityDeclHandler(), toTs(name), isParameterEntity ? 1 : 0, toTs(value), parser.getBase() == null ? PNone.NONE : parser.getBase(), PNone.NONE, PNone.NONE,
                                PNone.NONE);
            }

            @Override
            public void externalEntityDecl(String name, String publicId, String systemId) {
                boolean isParameterEntity = name != null && name.startsWith("%");
                call(parser.getEntityDeclHandler(), toTs(name), isParameterEntity ? 1 : 0, PNone.NONE, parser.getBase() == null ? PNone.NONE : parser.getBase(),
                                toOptionalTs(normalizeSystemId(systemId)),
                                toOptionalTs(publicId), PNone.NONE);
            }

            @Override
            public void notationDecl(String name, String publicId, String systemId) {
                call(parser.getNotationDeclHandler(), toTs(name), parser.getBase() == null ? PNone.NONE : parser.getBase(), toOptionalTs(normalizeSystemId(systemId)), toOptionalTs(publicId));
            }

            @Override
            public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) {
                call(parser.getUnparsedEntityDeclHandler(), toTs(name), parser.getBase() == null ? PNone.NONE : parser.getBase(), toOptionalTs(normalizeSystemId(systemId)), toOptionalTs(publicId),
                                toTs(notationName));
            }

            @Override
            public void elementDecl(String name, String model) {
                call(parser.getElementDeclHandler(), toTs(name), toTs(model));
            }

            @Override
            public void attributeDecl(String eName, String aName, String type, String mode, String value) {
                call(parser.getAttlistDeclHandler(), toTs(eName), toTs(aName), toTs(type), toOptionalTs(mode), toOptionalTs(value));
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attrs) {
                Object attrsObj;
                int attrCount = attrs.getLength();
                if (parser.isSpecifiedAttributes() && attrs instanceof Attributes2 attrs2) {
                    attrCount = 0;
                    for (int i = 0; i < attrs.getLength(); i++) {
                        if (attrs2.isSpecified(i)) {
                            attrCount++;
                        }
                    }
                }
                if (parser.isOrderedAttributes()) {
                    List<Object> l = new ArrayList<>(attrCount * 2);
                    for (int i = 0; i < attrs.getLength(); i++) {
                        if (parser.isSpecifiedAttributes() && attrs instanceof Attributes2 attrs2 && !attrs2.isSpecified(i)) {
                            continue;
                        }
                        l.add(toTs(attributeName(attrs, i)));
                        l.add(toTs(attrs.getValue(i)));
                    }
                    attrsObj = PFactory.createList(PythonLanguage.get(null), l.toArray());
                } else {
                    PDict d = PFactory.createDict(PythonLanguage.get(null));
                    for (int i = 0; i < attrs.getLength(); i++) {
                        if (parser.isSpecifiedAttributes() && attrs instanceof Attributes2 attrs2 && !attrs2.isSpecified(i)) {
                            continue;
                        }
                        d.setItem(toTs(attributeName(attrs, i)), toTs(attrs.getValue(i)));
                    }
                    attrsObj = d;
                }
                call(parser.getStartElementHandler(), toTs(elementName(uri, localName, qName)), attrsObj);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                call(parser.getEndElementHandler(), toTs(elementName(uri, localName, qName)));
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (length > 0) {
                    call(parser.getCharacterDataHandler(), toTs(new String(ch, start, length)));
                }
            }

            @Override
            public void comment(char[] ch, int start, int length) {
                call(parser.getCommentHandler(), toTs(new String(ch, start, length)));
            }

            @Override
            public void startCDATA() {
                call(parser.getStartCdataSectionHandler());
            }

            @Override
            public void endCDATA() {
                call(parser.getEndCdataSectionHandler());
            }

            @Override
            public void skippedEntity(String name) {
                call(parser.getSkippedEntityHandler(), toTs(name), 0);
                if (locator != null) {
                    int entityLen = name.length() + 2; // '&' + ';'
                    line = Math.max(1, locator.getLineNumber());
                    col = Math.max(0, locator.getColumnNumber() - 1 - entityLen);
                    parser.setCurrentLineNumber(line);
                    parser.setCurrentColumnNumber(col);
                    parser.setErrorLineNumber(line);
                    parser.setErrorColumnNumber(col);
                }
                keepCurrentPositionForNextCall = true;
                call(parser.getDefaultHandlerExpand(), toTs("&" + name + ";"));
            }

            private String elementName(String uri, String localName, String qName) {
                if (parser.getNamespaceSeparator() != null && uri != null && !uri.isEmpty()) {
                    String sep = parser.getNamespaceSeparator().toJavaStringUncached();
                    if (parser.isNamespacePrefixes() && qName != null && !qName.isEmpty()) {
                        int colon = qName.indexOf(':');
                        if (colon > 0) {
                            String prefix = qName.substring(0, colon);
                            return uri + sep + localName + sep + prefix;
                        }
                    }
                    return uri + sep + localName;
                }
                return qName == null || qName.isEmpty() ? localName : qName;
            }

            private String attributeName(Attributes attrs, int i) {
                String uri = attrs.getURI(i);
                String localName = attrs.getLocalName(i);
                String qName = attrs.getQName(i);
                if (parser.getNamespaceSeparator() != null && uri != null && !uri.isEmpty()) {
                    String sep = parser.getNamespaceSeparator().toJavaStringUncached();
                    if (parser.isNamespacePrefixes() && qName != null && !qName.isEmpty()) {
                        int colon = qName.indexOf(':');
                        if (colon > 0) {
                            String prefix = qName.substring(0, colon);
                            return uri + sep + localName + sep + prefix;
                        }
                    }
                    return uri + sep + localName;
                }
                return qName == null || qName.isEmpty() ? localName : qName;
            }

            private void call(Object handler, Object... args) {
                boolean shouldDeliver = eventOrdinal++ >= parser.getDeliveredEventCount();
                if (!shouldDeliver) {
                    return;
                }
                if (!keepCurrentPositionForNextCall && locator != null) {
                    line = Math.max(1, locator.getLineNumber());
                    col = Math.max(0, locator.getColumnNumber() - 1);
                    parser.setCurrentLineNumber(line);
                    parser.setCurrentColumnNumber(col);
                    parser.setErrorLineNumber(line);
                    parser.setErrorColumnNumber(col);
                }
                keepCurrentPositionForNextCall = false;
                if (handler != PNone.NONE) {
                    CallNode.executeUncached(handler, args);
                }
            }

            private TruffleString toTs(String s) {
                return s == null ? T_EMPTY_STRING : toTruffleStringUncached(s);
            }

            private Object toOptionalTs(String s) {
                return s == null || s.isEmpty() ? PNone.NONE : toTruffleStringUncached(s);
            }

            private String normalizeSystemId(String systemId) {
                if (systemId == null || parser.getBase() != null) {
                    return systemId;
                }
                String s = systemId;
                while (s.startsWith("file://")) {
                    s = s.substring("file://".length());
                }
                if (s.startsWith("/")) {
                    int idx = s.lastIndexOf('/');
                    if (idx >= 0 && idx + 1 < s.length()) {
                        return s.substring(idx + 1);
                    }
                }
                return systemId;
            }
        }

        Handler handler = new Handler();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(parser.getNamespaceSeparator() != null);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            try {
                reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {
            }
            reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            reader.setContentHandler(handler);
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
            reader.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
            reader.setDTDHandler(handler);
            reader.setErrorHandler(new DefaultHandler());
            reader.parse(source);
            int byteIndex = byteIndexSupplier.get();
            parser.setDeliveredEventCount(handler.eventOrdinal);
            parser.setCurrentByteIndex(byteIndex);
            parser.setCurrentLineNumber(handler.line);
            parser.setCurrentColumnNumber(handler.col);
            parser.setErrorByteIndex(parser.getCurrentByteIndex());
            parser.setErrorLineNumber(parser.getCurrentLineNumber());
            parser.setErrorColumnNumber(parser.getCurrentColumnNumber());
        } catch (SAXParseException e) {
            parser.setCurrentByteIndex(byteIndexSupplier.get());
            parser.setDeliveredEventCount(handler.eventOrdinal);
            parser.setCurrentLineNumber(e.getLineNumber());
            parser.setCurrentColumnNumber(Math.max(0, e.getColumnNumber() - 1));
            parser.setErrorByteIndex(parser.getCurrentByteIndex());
            parser.setErrorLineNumber(parser.getCurrentLineNumber());
            parser.setErrorColumnNumber(parser.getCurrentColumnNumber());
            if (!swallowErrors) {
                TruffleString msg = toTruffleStringUncached(formatErrorMessage(e));
                throw raiseExpatError(null, msg, mapErrorCode(e), parser.getCurrentByteIndex(), parser.getCurrentLineNumber(),
                                parser.getCurrentColumnNumber());
            }
        } catch (PException e) {
            throw e;
        } catch (Exception e) {
            parser.setCurrentByteIndex(byteIndexSupplier.get());
            parser.setDeliveredEventCount(handler.eventOrdinal);
            parser.setErrorByteIndex(parser.getCurrentByteIndex());
            parser.setErrorLineNumber(parser.getCurrentLineNumber());
            parser.setErrorColumnNumber(parser.getCurrentColumnNumber());
            if (!swallowErrors) {
                TruffleString msg = toTruffleStringUncached(e.getMessage() == null ? "unclosed token" : e.getMessage());
                throw raiseExpatError(null, msg, PXMLParser.XML_ERROR_UNCLOSED_TOKEN, parser.getCurrentByteIndex(),
                                parser.getCurrentLineNumber(),
                                parser.getCurrentColumnNumber());
            }
        }
    }

    @FunctionalInterface
    private interface ByteIndexSupplier {
        int get();
    }

    private static final class PythonFileInputStream extends InputStream {
        private final Object file;
        private final int readSize;
        private byte[] buffer = EMPTY_BYTE_ARRAY;
        private int offset;
        private boolean eof;
        private int bytesRead;

        private PythonFileInputStream(Object file, int readSize) {
            this.file = file;
            this.readSize = readSize;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n == -1 ? -1 : b[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (offset >= buffer.length) {
                fillBuffer();
                if (eof) {
                    return -1;
                }
            }
            int n = Math.min(len, buffer.length - offset);
            System.arraycopy(buffer, offset, b, off, n);
            offset += n;
            return n;
        }

        private void fillBuffer() throws IOException {
            if (eof) {
                return;
            }
            try {
                Object chunkObj = PyObjectCallMethodObjArgs.executeUncached(file, T_READ, readSize);
                byte[] chunk = asByteArray(chunkObj);
                if (chunk.length == 0) {
                    eof = true;
                    buffer = EMPTY_BYTE_ARRAY;
                    offset = 0;
                    return;
                }
                buffer = chunk;
                offset = 0;
                bytesRead += chunk.length;
            } catch (PException e) {
                throw new IOException(e);
            }
        }

        int getBytesRead() {
            return bytesRead;
        }
    }

    private static PException raiseExpatError(Node raisingNode, TruffleString msg, int code, int byteIndex, int line, int column) {
        PythonLanguage language = PythonLanguage.get(raisingNode);
        PBaseException exc = PFactory.createBaseException(language, PythonBuiltinClassType.PyExpatError, msg, EMPTY_OBJECT_ARRAY);
        exc.setAttribute(tsLiteral("code"), code);
        exc.setAttribute(tsLiteral("lineno"), line);
        exc.setAttribute(tsLiteral("offset"), column);
        exc.setAttribute(tsLiteral("message"), msg);
        exc.setAttribute(tsLiteral("byteindex"), byteIndex);
        throw PRaiseNode.raiseExceptionObjectStatic(raisingNode, exc, false);
    }

    private static String formatErrorMessage(SAXParseException e) {
        String message = e.getMessage();
        if (message == null) {
            return "syntax error";
        }
        if (message.contains("entity") && message.contains("not declared")) {
            int firstQuote = message.indexOf('"');
            int secondQuote = firstQuote >= 0 ? message.indexOf('"', firstQuote + 1) : -1;
            if (firstQuote >= 0 && secondQuote > firstQuote + 1) {
                String entity = message.substring(firstQuote + 1, secondQuote);
                return "undefined entity &" + entity + ";: line " + e.getLineNumber() + ", column " + Math.max(0, e.getColumnNumber() - 1);
            }
        }
        return message;
    }

    private static int mapErrorCode(SAXParseException e) {
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("start and end within the same entity") || message.contains("premature end of file") || message.contains("must be terminated")) {
                return PXMLParser.XML_ERROR_UNCLOSED_TOKEN;
            }
        }
        return PXMLParser.XML_ERROR_SYNTAX;
    }
}
