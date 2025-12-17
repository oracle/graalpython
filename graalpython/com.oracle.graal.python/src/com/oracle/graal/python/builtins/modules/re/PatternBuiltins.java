/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.re;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TRegexUtil;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromModuleNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.IntArrayBuilder;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static com.oracle.graal.python.builtins.modules.re.TRegexCache.FLAG_ASCII;
import static com.oracle.graal.python.builtins.modules.re.TRegexCache.FLAG_DOTALL;
import static com.oracle.graal.python.builtins.modules.re.TRegexCache.FLAG_IGNORECASE;
import static com.oracle.graal.python.builtins.modules.re.TRegexCache.FLAG_LOCALE;
import static com.oracle.graal.python.builtins.modules.re.TRegexCache.FLAG_MULTILINE;
import static com.oracle.graal.python.builtins.modules.re.TRegexCache.FLAG_UNICODE;
import static com.oracle.graal.python.builtins.modules.re.TRegexCache.FLAG_VERBOSE;
import static com.oracle.graal.python.nodes.BuiltinNames.T__SRE;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_CHAR_IN_GROUP_NAME;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ESCAPE_END_OF_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ESCAPE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_GROUP_REFERENCE;
import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_GROUP_NAME;
import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_LEFT_ANGLE_BRACKET;
import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_RIGHT_ANGLE_BRACKET;
import static com.oracle.graal.python.nodes.ErrorMessages.OCTAL_ESCAPE_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_GROUP_NAME;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING_BINARY;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PPattern})
public final class PatternBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = PatternBuiltinsSlotsGen.SLOTS;

    public static void bailoutUnsupportedRegex(TRegexCache cache) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw CompilerDirectives.shouldNotReachHere("unsupported regular expression: /" + cache.getPattern() + "/" + cache.getFlags());
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PatternBuiltinsFactory.getFactories();
    }

    @Slot(value = Slot.SlotKind.tp_new, isComplex = true)
    @Slot.SlotSignature(name = "re.Pattern", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "source", "flags"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int)
    public abstract static class NewNode extends PythonClinicBuiltinNode {
        private static final String PROP_ASCII = "ASCII";
        private static final String PROP_DOTALL = "DOTALL";
        private static final String PROP_IGNORECASE = "IGNORECASE";
        private static final String PROP_LOCALE = "LOCALE";
        private static final String PROP_MULTILINE = "MULTILINE";
        private static final String PROP_UNICODE = "UNICODE";
        private static final String PROP_VERBOSE = "VERBOSE";

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PatternBuiltinsClinicProviders.NewNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object newPattern(VirtualFrame frame, PythonBuiltinClassType cls, Object source, int flags,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "1") InteropLibrary interop,
                        @Cached PatternNodes.TRegexCompileNode tRegexCompileNodeNode,
                        @Cached TRegexUtil.InteropReadMemberNode interopReadMemberNode) {
            try {
                var cache = new TRegexCache(inliningTarget, source, flags);

                boolean mustAdvance = false;
                Object regexObject = tRegexCompileNodeNode.execute(frame, cache, PythonMethod.Search, mustAdvance);

                Object flagsObject = TRegexUtil.TRegexCompiledRegexAccessor.flags(regexObject, inliningTarget, interopReadMemberNode);

                int flagsInlined = 0;
                if ((Boolean) interop.readMember(flagsObject, PROP_ASCII)) {
                    flagsInlined |= FLAG_ASCII;
                }
                if ((Boolean) interop.readMember(flagsObject, PROP_DOTALL)) {
                    flagsInlined |= FLAG_DOTALL;
                }
                if ((Boolean) interop.readMember(flagsObject, PROP_IGNORECASE)) {
                    flagsInlined |= FLAG_IGNORECASE;
                }
                if ((Boolean) interop.readMember(flagsObject, PROP_LOCALE)) {
                    flagsInlined |= FLAG_LOCALE;
                }
                if ((Boolean) interop.readMember(flagsObject, PROP_MULTILINE)) {
                    flagsInlined |= FLAG_MULTILINE;
                }
                if ((Boolean) interop.readMember(flagsObject, PROP_UNICODE)) {
                    flagsInlined |= FLAG_UNICODE;
                }
                if ((Boolean) interop.readMember(flagsObject, PROP_VERBOSE)) {
                    flagsInlined |= FLAG_VERBOSE;
                }

                int groupsCount = TRegexUtil.TRegexCompiledRegexAccessor.groupCount(regexObject, inliningTarget, interopReadMemberNode) - 1;

                Object groupsObject = TRegexUtil.TRegexCompiledRegexAccessor.namedCaptureGroups(regexObject, inliningTarget, interopReadMemberNode);
                Object membersObject = interop.getMembers(groupsObject);
                int length = (int) interop.getArraySize(membersObject);
                Object[] keysAndValues = new Object[length * 2];

                for (int i = 0; i < length; i++) {
                    // interop protocol guarantees these are strings
                    String name = interop.asString(interop.readArrayElement(membersObject, i));

                    // value is an index of a captured group
                    // expect that value is always Integer, that's RegexObject always provides
                    // Integer group indices
                    Object value = interop.readMember(groupsObject, name);
                    if (!(value instanceof Integer)) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }

                    keysAndValues[i * 2] = name;
                    keysAndValues[i * 2 + 1] = value;
                }

                LinkedHashMap<String, Object> groupsMap = arrayToMap(keysAndValues);

                Shape shape = cls.getInstanceShape(language);
                return new PPattern(cls, shape, source, flags | flagsInlined, groupsCount, groupsMap, cache);
            } catch (UnsupportedMessageException | UnknownIdentifierException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @TruffleBoundary
        static LinkedHashMap<String, Object> arrayToMap(Object[] array) {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();

            for (int i = 0; i < array.length; i += 2) {
                String key = (String) array[i];
                Object value = array[i + 1];

                map.put(key, value);
            }

            return map;
        }
    }

    @Slot(value = Slot.SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends TpSlotRichCompare.RichCmpBuiltinNode {

        @Specialization
        static Object richCmp(VirtualFrame frame, PPattern self, PPattern other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompareBool richCompareBool) {
            if (op != RichCmpOp.Py_EQ && op != RichCmpOp.Py_NE) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            if (self == other) {
                // pattern references equal
                return op == RichCmpOp.Py_EQ;
            }

            // compare flags and sources (either Python str or bytes)
            if (op == RichCmpOp.Py_EQ) {
                return self.flags == other.flags && richCompareBool.execute(frame, inliningTarget, self.source, other.source, op);
            } else {
                return self.flags != other.flags || richCompareBool.execute(frame, inliningTarget, self.source, other.source, op);
            }
        }

        @Fallback
        static PNotImplemented richCmp(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends TpSlotHashFun.HashBuiltinNode {

        @Specialization
        static long hash(VirtualFrame frame, PPattern self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectHashNode hashNode) {
            var content = new Object[]{self.source, self.flags};
            return hashNode.execute(frame, inliningTarget, PFactory.createTuple(language, content));
        }
    }

    @Slot(value = Slot.SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString repr(VirtualFrame frame, PPattern self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            // pattern source string representation
            TruffleString sourceReprTS = reprNode.execute(frame, inliningTarget, self.source);
            String sourceReprString = castToJavaStringNode.execute(sourceReprTS);

            return format(sourceReprString, self.flags, self.cache.isBinary());
        }

        @TruffleBoundary
        private static TruffleString format(String sourceReprString, int flags, boolean isBinary) {
            StringBuilder builder = new StringBuilder();

            builder.append("re.compile(");
            builder.append(sourceReprString, 0, Math.min(sourceReprString.length(), 200));

            ArrayList<String> names = new ArrayList<>();
            int flagsLeft = flags;

            // order replicates an order in CPython implementation
            if ((flags & FLAG_IGNORECASE) != 0) {
                names.add("re.IGNORECASE");
                flagsLeft &= ~FLAG_IGNORECASE;
            }
            if ((flags & FLAG_LOCALE) != 0) {
                names.add("re.LOCALE");
                flagsLeft &= ~FLAG_LOCALE;
            }
            if ((flags & FLAG_MULTILINE) != 0) {
                names.add("re.MULTILINE");
                flagsLeft &= ~FLAG_MULTILINE;
            }
            if ((flags & FLAG_DOTALL) != 0) {
                names.add("re.DOTALL");
                flagsLeft &= ~FLAG_DOTALL;
            }
            if ((flags & FLAG_UNICODE) != 0) {
                // Omit re.UNICODE for valid string patterns
                if (isBinary || (flagsLeft & (FLAG_LOCALE | FLAG_UNICODE | FLAG_ASCII)) != FLAG_UNICODE) {
                    names.add("re.UNICODE");
                }
                flagsLeft &= ~FLAG_UNICODE;
            }
            if ((flags & FLAG_VERBOSE) != 0) {
                names.add("re.VERBOSE");
                flagsLeft &= ~FLAG_VERBOSE;
            }
            if ((flags & FLAG_ASCII) != 0) {
                names.add("re.ASCII");
                flagsLeft &= ~FLAG_ASCII;
            }

            // handle unknown flags
            if (flagsLeft != 0) {
                String unknownFlags = String.format("0x%x", flagsLeft);
                names.add(unknownFlags);
            }

            if (!names.isEmpty()) {
                builder.append(", ");
                builder.append(String.join("|", names));
            }

            builder.append(")");

            return TruffleString.FromJavaStringNode.getUncached().execute(builder.toString(), TS_ENCODING);
        }
    }

    @Builtin(name = "flags", minNumOfPositionalArgs = 1, isGetter = true, doc = "The regex matching flags.")
    @GenerateNodeFactory
    public abstract static class FlagsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getFlags(PPattern self) {
            return self.flags;
        }
    }

    @Builtin(name = "groups", minNumOfPositionalArgs = 1, isGetter = true, doc = "The number of capturing groups in the pattern.")
    @GenerateNodeFactory
    public abstract static class GroupsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getGroups(PPattern self) {
            return self.groupsCount;
        }
    }

    @Builtin(name = "groupindex", minNumOfPositionalArgs = 1, isGetter = true, doc = "A dictionary mapping group names to group numbers.")
    @GenerateNodeFactory
    public abstract static class GroupIndexNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PMappingproxy getGroupIndex(PPattern self,
                        @Bind PythonLanguage language) {
            PDict groupsDictionary = PFactory.createDictFromMap(language, self.groupToIndexMap);
            return PFactory.createMappingproxy(language, groupsDictionary);
        }
    }

    @Builtin(name = "pattern", minNumOfPositionalArgs = 1, isGetter = true, doc = "The pattern string from which the RE object was compiled.")
    @GenerateNodeFactory
    public abstract static class PatternNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getPattern(PPattern self) {
            return self.source;
        }
    }

    @Builtin(name = "search", minNumOfPositionalArgs = 2, parameterNames = {"$self", "string", "pos",
                    "endpos"}, doc = "search($self, /, string, pos=0, endpos=sys.maxsize)\n--\n\nScan through string looking for a match, and return a corresponding match object instance.\n\nReturn None if no position in the string matches.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "pos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @ArgumentClinic(name = "endpos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "Integer.MAX_VALUE")
    public abstract static class SearchNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PatternBuiltinsClinicProviders.SearchNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object search(VirtualFrame frame, PPattern self, Object stringObject, int pos, int endPos,
                        @Bind Node inliningTarget,
                        @Cached PatternNodes.SearchNode searchNode) {
            return searchNode.execute(frame, inliningTarget, self, stringObject, pos, endPos, PythonMethod.Search, false);
        }
    }

    @Builtin(name = "match", minNumOfPositionalArgs = 2, parameterNames = {"$self", "string", "pos",
                    "endpos"}, forceSplitDirectCalls = true, doc = "match($self, /, string, pos=0, endpos=sys.maxsize)\n--\n\nMatches zero or more characters at the beginning of the string.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "pos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @ArgumentClinic(name = "endpos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "Integer.MAX_VALUE")
    public abstract static class MatchNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PatternBuiltinsClinicProviders.MatchNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object match(VirtualFrame frame, PPattern self, Object stringObject, int pos, int endPos,
                        @Bind Node inliningTarget,
                        @Cached PatternNodes.SearchNode searchNode) {
            return searchNode.execute(frame, inliningTarget, self, stringObject, pos, endPos, PythonMethod.Match, false);
        }
    }

    @Builtin(name = "fullmatch", minNumOfPositionalArgs = 2, parameterNames = {"$self", "string", "pos",
                    "endpos"}, forceSplitDirectCalls = true, doc = "fullmatch($self, /, string, pos=0, endpos=sys.maxsize)\n--\n\nMatches against all of the string.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "pos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @ArgumentClinic(name = "endpos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "Integer.MAX_VALUE")
    public abstract static class FullMatchNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PatternBuiltinsClinicProviders.FullMatchNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object fullMatch(VirtualFrame frame, PPattern self, Object stringObject, int pos, int endPos,
                        @Bind Node inliningTarget,
                        @Cached PatternNodes.SearchNode searchNode) {
            return searchNode.execute(frame, inliningTarget, self, stringObject, pos, endPos, PythonMethod.FullMatch, false);
        }
    }

    @Builtin(name = "split", minNumOfPositionalArgs = 2, parameterNames = {"$self", "string",
                    "maxsplit"}, forceSplitDirectCalls = true, doc = "split($self, /, string, maxsplit=0)\n--\n\nSplit string by the occurrences of pattern.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "maxsplit", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    public abstract static class SplitNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PatternBuiltinsClinicProviders.SplitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object split(VirtualFrame frame, PPattern self, Object stringObject, int maxSplit,
                        @Bind Node inliningTarget,
                        @Cached PatternNodes.TRegexCompileNode tRegexCompile,
                        @Cached PatternNodes.RECheckInputTypeNode checkInputTypeNode,
                        @Cached TRegexUtil.InteropReadMemberNode readGroupCountNode,
                        @Cached(inline = true) SplitInnerNode1 innerNode) {
            Object compiledRegex = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, false);
            Object compiledRegexMustAdvance = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, true);

            if (compiledRegex == PNone.NONE || compiledRegexMustAdvance == PNone.NONE) {
                bailoutUnsupportedRegex(self.cache);
            }

            checkInputTypeNode.execute(frame, stringObject, self.cache.isBinary());

            int groupCount = TRegexUtil.TRegexCompiledRegexAccessor.groupCount(compiledRegex, inliningTarget, readGroupCountNode);
            return innerNode.execute(inliningTarget, frame, compiledRegex, compiledRegexMustAdvance, stringObject, maxSplit, self.cache.isBinary(), groupCount);
        }
    }

    @Builtin(name = "findall", minNumOfPositionalArgs = 2, parameterNames = {"$self", "string", "pos",
                    "endpos"}, forceSplitDirectCalls = true, doc = "findall($self, /, string, pos=0, endpos=sys.maxsize)\n--\n\nReturn a list of all non-overlapping matches of pattern in string.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "pos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @ArgumentClinic(name = "endpos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "Integer.MAX_VALUE")
    abstract static class FindAllNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PatternBuiltinsClinicProviders.FindAllNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object findAll(VirtualFrame frame, PPattern self, Object stringObject, int pos, int endPos,
                        @Bind Node inliningTarget,
                        @Cached PatternNodes.TRegexCompileNode tRegexCompile,
                        @Cached PatternNodes.RECheckInputTypeNode checkInputTypeNode,
                        @Cached TRegexUtil.InteropReadMemberNode readGroupCountNode,
                        @Cached(inline = true) FindAllInnerNode1 innerNode) {
            Object compiledRegex = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, false);
            Object compiledRegexMustAdvance = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, true);

            if (compiledRegex == PNone.NONE || compiledRegexMustAdvance == PNone.NONE) {
                bailoutUnsupportedRegex(self.cache);
            }

            checkInputTypeNode.execute(frame, stringObject, self.cache.isBinary());

            int groupCount = TRegexUtil.TRegexCompiledRegexAccessor.groupCount(compiledRegex, inliningTarget, readGroupCountNode);
            return innerNode.execute(inliningTarget, frame, compiledRegex, compiledRegexMustAdvance, stringObject, pos, endPos, self.cache.isBinary(), groupCount);
        }
    }

    @Builtin(name = "finditer", minNumOfPositionalArgs = 2, parameterNames = {"$self", "string", "pos",
                    "endpos"}, forceSplitDirectCalls = true, doc = "finditer($self, /, string, pos=0, endpos=sys.maxsize)\n--\n\nReturn an iterator over all non-overlapping matches for the RE pattern in string.\n\nFor each match, the iterator returns a match object.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "pos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @ArgumentClinic(name = "endpos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "Integer.MAX_VALUE")
    abstract static class FindIterNode extends PythonQuaternaryClinicBuiltinNode {

        private static final TruffleString T_SEARCH = tsLiteral("search");

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PatternBuiltinsClinicProviders.FindIterNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object findIter(VirtualFrame frame, PPattern self, Object stringObject, int pos, int endPos,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PatternNodes.RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached PyObjectGetAttr getAttr) {
            reCheckInputTypeNode.execute(frame, stringObject, self.cache.isBinary());

            // reuse _sre.SREScanner#search() and wrap it into an iterator
            PythonBuiltinClassType cls = PythonBuiltinClassType.SREScanner;
            Shape shape = cls.getInstanceShape(language);
            Object scanner = new PSREScanner(cls, shape, self, stringObject, pos, endPos);

            Object searchFunction = getAttr.execute(frame, inliningTarget, scanner, T_SEARCH);
            return PFactory.createSentinelIterator(language, searchFunction, PNone.NONE);
        }
    }

    @Builtin(name = "sub", minNumOfPositionalArgs = 3, parameterNames = {"$self", "repl", "string",
                    "count"}, forceSplitDirectCalls = true, doc = "sub($self, /, repl, string, count=0)\n--\n\nReturn the string obtained by replacing the leftmost non-overlapping occurrences of pattern in string by the replacement repl.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "count", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    abstract static class SubNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PatternBuiltinsClinicProviders.SubNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object sub(VirtualFrame frame, PPattern self, Object replacement, Object string, int count,
                        @Bind Node inliningTarget,
                        @Cached PatternNodes.TRegexCompileNode tRegexCompile,
                        @Cached PatternNodes.RECheckInputTypeNode checkInputTypeNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached(inline = true) SubnInnerNode1 innerNode1) {
            Object compiledRegex = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, false);
            Object compiledRegexMustAdvance = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, true);

            if (compiledRegex == PNone.NONE || compiledRegexMustAdvance == PNone.NONE) {
                bailoutUnsupportedRegex(self.cache);
            }

            checkInputTypeNode.execute(frame, string, self.cache.isBinary());

            return innerNode1.execute(inliningTarget, frame, self, compiledRegex, compiledRegexMustAdvance, replacement, string, count, self.cache.isBinary(),
                            callableCheckNode.execute(inliningTarget, replacement), false);
        }
    }

    @Builtin(name = "subn", minNumOfPositionalArgs = 3, parameterNames = {"$self", "repl", "string",
                    "count"}, forceSplitDirectCalls = true, doc = "subn($self, /, repl, string, count=0)\n--\n\nReturn the tuple (new_string, number_of_subs_made) found by replacing the leftmost non-overlapping occurrences of pattern with the replacement repl.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "count", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    abstract static class SubNNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PatternBuiltinsClinicProviders.SubNNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object subN(VirtualFrame frame, PPattern self, Object replacement, Object string, int count,
                        @Bind Node inliningTarget,
                        @Cached PatternNodes.TRegexCompileNode tRegexCompile,
                        @Cached PatternNodes.RECheckInputTypeNode checkInputTypeNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached(inline = true) SubnInnerNode1 innerNode1) {
            Object compiledRegex = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, false);
            Object compiledRegexMustAdvance = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, true);

            if (compiledRegex == PNone.NONE || compiledRegexMustAdvance == PNone.NONE) {
                bailoutUnsupportedRegex(self.cache);
            }

            checkInputTypeNode.execute(frame, string, self.cache.isBinary());

            return innerNode1.execute(inliningTarget, frame, self, compiledRegex, compiledRegexMustAdvance, replacement, string, count, self.cache.isBinary(),
                            callableCheckNode.execute(inliningTarget, replacement), true);
        }
    }

    @Builtin(name = "scanner", minNumOfPositionalArgs = 2, parameterNames = {"$self", "string", "pos",
                    "endpos"}, doc = "match($self, /, string, pos=0, endpos=sys.maxsize)\n--\n\nMatches zero or more characters at the beginning of the string.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "pos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @ArgumentClinic(name = "endpos", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "Integer.MAX_VALUE")
    public abstract static class ScannerNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PatternBuiltinsClinicProviders.ScannerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object getScanner(VirtualFrame frame, PPattern self, Object string, int pos, int endPos,
                        @Bind PythonLanguage language,
                        @Cached PatternNodes.RECheckInputTypeNode reCheckInputTypeNode) {
            // raise error immediately
            reCheckInputTypeNode.execute(frame, string, self.cache.isBinary());

            PythonBuiltinClassType cls = PythonBuiltinClassType.SREScanner;
            Shape shape = cls.getInstanceShape(language);
            return new PSREScanner(cls, shape, self, string, pos, endPos);
        }
    }

    @Builtin(name = "__copy__", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, doc = "__copy__($self, /)\n--\n\n")
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonBuiltinNode {

        @Specialization
        static PPattern copy(PPattern self) {
            return self;
        }
    }

    @Builtin(name = "__deepcopy__", minNumOfPositionalArgs = 2, parameterNames = {"$self", "memo"}, doc = "__deepcopy__($self, memo, /)\n--\n\n")
    @GenerateNodeFactory
    public abstract static class DeepCopyNode extends PythonBuiltinNode {

        @Specialization
        static PPattern deepCopy(PPattern self, Object memo) {
            return self;
        }
    }

    /**
     * There are multiple nested inner nodes used in {@link SplitNode}. The number at the end of
     * each inner node indicates the nesting level.
     * <p>
     * First level: separate specializations for strings ({@link TruffleString}, {@link PString})
     * and bytes-objects ({@link PBytesLike}).
     */
    @GenerateInline
    abstract static class SplitInnerNode1 extends Node {

        abstract Object execute(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object input, int maxsplit,
                        boolean binary, int groupCount);

        @Specialization(guards = "!binary")
        static Object doString(Node inliningTarget, @SuppressWarnings("unused") VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object inputObj, int maxsplit,
                        boolean binary, int groupCount,
                        @Cached CastToTruffleStringNode cast,
                        @Cached @Exclusive SplitInnerNode2 innerNode) {
            TruffleString input = cast.castKnownString(inliningTarget, inputObj);
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, maxsplit, binary, groupCount);
        }

        @Specialization(guards = "binary")
        static Object doBytes(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object inputObj, int maxsplit,
                        boolean binary, int groupCount,
                        @Cached PatternNodes.TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached @Exclusive SplitInnerNode2 innerNode) {
            TruffleString input = bufferToTruffleStringNode.execute(frame, inputObj);
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, maxsplit, binary, groupCount);
        }
    }

    /**
     * Second level: separate specializations for regexes with and without capture groups.
     */
    @GenerateInline
    abstract static class SplitInnerNode2 extends Node {

        abstract Object execute(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int maxsplit,
                        boolean binary, int groupCount);

        @Specialization(guards = "groupCount == 1")
        static Object count1(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int maxsplit, boolean binary, int groupCount,
                        @Cached @Exclusive SplitInnerNode3 innerNode) {
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, maxsplit, binary, groupCount, false);
        }

        @Specialization(guards = "groupCount > 1")
        static Object count2(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int maxsplit, boolean binary, int groupCount,
                        @Cached @Exclusive SplitInnerNode3 innerNode) {
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, maxsplit, binary, groupCount, true);
        }
    }

    /**
     * Third level: method implementation.
     */
    @GenerateInline
    abstract static class SplitInnerNode3 extends Node {

        abstract Object execute(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int maxsplit, boolean binary, int groupCount,
                        boolean hasCaptureGroups);

        @Specialization
        static Object doString(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int maxsplit, boolean binary, int groupCount,
                        boolean hasCaptureGroups,
                        @Cached TRegexUtil.InvokeExecMethodNode invokeExecMethodNodeMustAdvance,
                        @Cached TRegexUtil.InvokeExecMethodNode invokeExecMethodNode,
                        @Cached TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode2,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode2,
                        @Cached TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            CompilerAsserts.partialEvaluationConstant(binary);
            CompilerAsserts.partialEvaluationConstant(hasCaptureGroups);
            TruffleString.Encoding encoding = binary ? TS_ENCODING_BINARY : TS_ENCODING;
            int stringLength = toCodepointIndex(input.byteLength(encoding), binary);
            int pos = 0;
            int n = 0;
            boolean mustAdvance = false;
            ArrayBuilder<Object> result = new ArrayBuilder<>(16);
            while ((maxsplit == 0 || n < maxsplit) && pos <= stringLength) {
                final Object searchResult;
                if (mustAdvance) {
                    searchResult = invokeExecMethodNodeMustAdvance.execute(inliningTarget, compiledRegexMustAdvance, input, pos);
                } else {
                    searchResult = invokeExecMethodNode.execute(inliningTarget, compiledRegex, input, pos);
                }
                if (!TRegexUtil.TRegexResultAccessor.isMatch(searchResult, inliningTarget, readIsMatchNode)) {
                    break;
                }
                n++;
                int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, 0, inliningTarget, readStartNode);
                int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, 0, inliningTarget, readEndNode);
                result.add(createSubstring(inliningTarget, input, binary, pos, start, substringByteIndexNode, copyToByteArrayNode));
                if (hasCaptureGroups) {
                    for (int i = 0; i < groupCount - 1; i++) {
                        // using a separate pair of capture group read nodes here, because the first
                        // capture group access may cause a lazy capture group evaluation call
                        // inside TRegex, and we want to avoid that in the inner loop
                        int substringStart = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, i + 1, inliningTarget, readStartNode2);
                        int substringEnd = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, i + 1, inliningTarget, readEndNode2);
                        result.add(createSubstringSplit(inliningTarget, input, binary, substringStart, substringEnd, substringByteIndexNode, copyToByteArrayNode));
                    }
                }
                pos = end;
                mustAdvance = start == end;
            }
            result.add(createSubstring(inliningTarget, input, binary, pos, stringLength, substringByteIndexNode, copyToByteArrayNode));
            return PFactory.createList(PythonLanguage.get(inliningTarget), result.toObjectArray());
        }

        private static Object createSubstringSplit(Node inliningTarget, TruffleString input, boolean binary, int substringStart, int substringEnd,
                        TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            CompilerAsserts.partialEvaluationConstant(binary);
            if (substringStart < 0) {
                return PNone.NONE;
            }
            return createSubstring(inliningTarget, input, binary, substringStart, substringEnd, substringByteIndexNode, copyToByteArrayNode);
        }
    }

    /**
     * There are multiple nested inner nodes used in {@link FindAllNode}. The number at the end of
     * each inner node indicates the nesting level.
     * <p>
     * First level: separate specializations for strings ({@link TruffleString}, {@link PString})
     * and bytes-objects ({@link PBytesLike}).
     */
    @GenerateInline
    abstract static class FindAllInnerNode1 extends Node {

        abstract Object execute(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object input, int pos, int endpos,
                        boolean binary, int groupCount);

        @Specialization(guards = "!binary")
        static Object doString(Node inliningTarget, @SuppressWarnings("unused") VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object inputObj, int pos, int endpos,
                        boolean binary, int groupCount,
                        @Cached CastToTruffleStringNode cast,
                        @Cached @Exclusive FindAllInnerNode2 innerNode) {
            TruffleString input = cast.castKnownString(inliningTarget, inputObj);
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, pos, endpos, binary, groupCount);
        }

        @Specialization(guards = "binary")
        static Object doBytes(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object inputObj, int pos, int endpos,
                        boolean binary, int groupCount,
                        @Cached PatternNodes.TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached @Exclusive FindAllInnerNode2 innerNode) {
            TruffleString input = bufferToTruffleStringNode.execute(frame, inputObj);
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, pos, endpos, binary, groupCount);
        }
    }

    /**
     * Second level: separate specializations for regexes without capture groups, a single capture
     * group, and multiple capture groups.
     */
    @GenerateInline
    abstract static class FindAllInnerNode2 extends Node {

        abstract Object execute(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int pos, int endpos,
                        boolean binary, int groupCount);

        @Specialization(guards = "groupCount == 1")
        static Object count1(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int pos, int endpos, boolean binary, int groupCount,
                        @Cached @Exclusive FindAllInnerNode3 innerNode) {
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, pos, endpos, binary, groupCount, false);
        }

        @Specialization(guards = "groupCount == 2")
        static Object count2(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int pos, int endpos, boolean binary, int groupCount,
                        @Cached @Exclusive FindAllInnerNode3 innerNode) {
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, pos, endpos, binary, groupCount, false);
        }

        @Specialization(guards = "groupCount > 2")
        static Object createTuples(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int pos, int endpos, boolean binary, int groupCount,
                        @Cached @Exclusive FindAllInnerNode3 innerNode) {
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, pos, endpos, binary, groupCount, true);
        }
    }

    /**
     * Third level: method implementation.
     */
    @GenerateInline
    abstract static class FindAllInnerNode3 extends Node {

        abstract Object execute(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int pos, int endpos, boolean binary, int groupCount,
                        boolean createTuples);

        @Specialization
        static Object doString(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int posArg, int endposArg, boolean binary, int groupCount,
                        boolean createTuples,
                        @Cached TRegexUtil.InvokeExecMethodWithMaxIndexNode invokeExecMethodNodeMustAdvance,
                        @Cached TRegexUtil.InvokeExecMethodWithMaxIndexNode invokeExecMethodNode,
                        @Cached TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode2,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode2,
                        @Cached TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            CompilerAsserts.partialEvaluationConstant(binary);
            CompilerAsserts.partialEvaluationConstant(createTuples);
            TruffleString.Encoding encoding = binary ? TS_ENCODING_BINARY : TS_ENCODING;
            int stringLength = toCodepointIndex(input.byteLength(encoding), binary);
            int endpos = endposArg < 0 ? 0 : Math.min(endposArg, stringLength);
            int pos = posArg < 0 ? 0 : Math.min(posArg, endpos);
            boolean mustAdvance = false;
            ArrayBuilder<Object> result = new ArrayBuilder<>(16);
            while (pos <= endpos) {
                final Object searchResult;
                if (mustAdvance) {
                    searchResult = invokeExecMethodNodeMustAdvance.execute(inliningTarget, compiledRegexMustAdvance, input, pos, endpos);
                } else {
                    searchResult = invokeExecMethodNode.execute(inliningTarget, compiledRegex, input, pos, endpos);
                }
                if (!TRegexUtil.TRegexResultAccessor.isMatch(searchResult, inliningTarget, readIsMatchNode)) {
                    break;
                }
                int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, 0, inliningTarget, readStartNode);
                int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, 0, inliningTarget, readEndNode);
                final Object resultEntry;
                if (createTuples) {
                    Object[] tuple = new Object[groupCount - 1];
                    for (int i = 0; i < groupCount - 1; i++) {
                        int substringStart = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, i + 1, inliningTarget, readStartNode2);
                        int substringEnd = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, i + 1, inliningTarget, readEndNode2);
                        tuple[i] = createSubstringFindAll(inliningTarget, input, binary, substringStart, substringEnd, substringByteIndexNode, copyToByteArrayNode);
                    }
                    resultEntry = PFactory.createTuple(PythonLanguage.get(inliningTarget), tuple);
                } else {
                    CompilerAsserts.partialEvaluationConstant(groupCount);
                    final int substringStart;
                    final int substringEnd;
                    if (groupCount == 1) {
                        substringStart = start;
                        substringEnd = end;
                    } else {
                        assert groupCount == 2;
                        substringStart = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, 1, inliningTarget, readStartNode2);
                        substringEnd = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, 1, inliningTarget, readEndNode2);
                    }
                    resultEntry = createSubstringFindAll(inliningTarget, input, binary, substringStart, substringEnd, substringByteIndexNode, copyToByteArrayNode);
                }
                result.add(resultEntry);
                pos = end;
                mustAdvance = start == end;
            }
            return PFactory.createList(PythonLanguage.get(inliningTarget), result.toObjectArray());
        }

        private static Object createSubstringFindAll(Node inliningTarget, TruffleString input, boolean binary, int substringStart, int substringEnd,
                        TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            CompilerAsserts.partialEvaluationConstant(binary);
            if (substringStart < 0) {
                if (binary) {
                    return PFactory.createEmptyBytes(PythonLanguage.get(inliningTarget));
                } else {
                    return TS_ENCODING.getEmpty();
                }
            }
            return createSubstring(inliningTarget, input, binary, substringStart, substringEnd, substringByteIndexNode, copyToByteArrayNode);
        }
    }

    /**
     * There are multiple nested inner nodes used in {@link SubNode}. The number at the end of each
     * inner node indicates the nesting level.
     * <p>
     * First level: separate specializations for strings ({@link TruffleString}, {@link PString})
     * and bytes-objects ({@link PBytesLike}).
     */
    @GenerateInline
    abstract static class SubnInnerNode1 extends Node {

        abstract Object execute(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacement, Object input, int count,
                        boolean binary, boolean isCallable, boolean returnTuple);

        @Specialization(guards = "!binary")
        static Object doString(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacement, Object inputObj, int count,
                        @SuppressWarnings("unused") boolean binary, boolean isCallable, boolean returnTuple,
                        @Cached CastToTruffleStringNode cast,
                        @Cached @Exclusive SubnInnerNode2 innerNode) {
            TruffleString input = cast.castKnownString(inliningTarget, inputObj);
            assert TS_ENCODING == TruffleString.Encoding.UTF_32 : "remove the >> 2 when switching to UTF-8";
            int stringLength = input.byteLength(TS_ENCODING) >> 2;
            TruffleStringBuilderUTF32 result = TruffleStringBuilder.createUTF32(Math.max(32, stringLength));
            return innerNode.execute(inliningTarget, frame, pattern, compiledRegex, compiledRegexMustAdvance, replacement, input, inputObj, count, binary, isCallable, returnTuple, stringLength,
                            result);
        }

        @Specialization(guards = "binary")
        static Object doBytes(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacement, Object inputObj, int count,
                        @SuppressWarnings("unused") boolean binary, boolean isCallable, boolean returnTuple,
                        @Cached PatternNodes.TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached @Exclusive SubnInnerNode2 innerNode) {
            TruffleString input = bufferToTruffleStringNode.execute(frame, inputObj);
            int stringLength = input.byteLength(TS_ENCODING_BINARY);
            TruffleStringBuilder result = TruffleStringBuilder.create(TS_ENCODING_BINARY, Math.max(32, stringLength));
            return innerNode.execute(inliningTarget, frame, pattern, compiledRegex, compiledRegexMustAdvance, replacement, input, inputObj, count, binary, isCallable, returnTuple, stringLength,
                            result);
        }
    }

    /**
     * Second level: Separate specializations for callable and non-callable replacement objects.
     */
    @GenerateInline
    abstract static class SubnInnerNode2 extends Node {

        abstract Object execute(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacement, TruffleString input,
                        Object originalInput, int count, boolean binary, boolean isCallable, boolean returnTuple, int stringLength, TruffleStringBuilder result);

        @Specialization(guards = "isCallable")
        static Object doCallable(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacement, TruffleString input,
                        Object originalInput, int count, boolean binary, @SuppressWarnings("unused") boolean isCallable, boolean returnTuple, int stringLength, TruffleStringBuilder result,
                        @Cached TRegexUtil.InvokeExecMethodNode invokeExecMethodNodeMustAdvance,
                        @Cached TRegexUtil.InvokeExecMethodNode invokeExecMethodNode,
                        @Cached TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached CallNode callNode,
                        @Cached @Exclusive CastToTruffleStringNode cast,
                        @Cached @Exclusive PatternNodes.TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached MatchNodes.NewNode newMatchNode) {
            int n = 0;
            int pos = 0;
            boolean mustAdvance = false;
            while ((count == 0 || n < count) && pos <= stringLength) {
                final Object searchResult;
                if (mustAdvance) {
                    searchResult = invokeExecMethodNodeMustAdvance.execute(inliningTarget, compiledRegexMustAdvance, input, pos);
                } else {
                    searchResult = invokeExecMethodNode.execute(inliningTarget, compiledRegex, input, pos);
                }
                if (!TRegexUtil.TRegexResultAccessor.isMatch(searchResult, inliningTarget, readIsMatchNode)) {
                    break;
                }
                n++;
                int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, 0, inliningTarget, readStartNode);
                int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, 0, inliningTarget, readEndNode);
                appendSubstringNode.execute(result, input, toByteIndex(pos, binary), toByteIndex(start - pos, binary));

                final Object match = newMatchNode.execute(frame, inliningTarget, (PPattern) pattern, searchResult, originalInput, pos, end);
                Object callResult = callNode.execute(frame, replacement, match);

                if (callResult != PNone.NONE) {
                    if (binary) {
                        appendStringNode.execute(result, bufferToTruffleStringNode.execute(frame, callResult));
                    } else {
                        appendStringNode.execute(result, cast.castKnownString(inliningTarget, callResult));
                    }
                }
                pos = end;
                mustAdvance = start == end;
            }
            final TruffleString resultString;
            if (n == 0) {
                resultString = input;
            } else {
                appendSubstringNode.execute(result, input, toByteIndex(pos, binary), toByteIndex(stringLength - pos, binary));
                resultString = toStringNode.execute(result, binary);
            }
            final Object resultObject;
            if (binary) {
                resultObject = PFactory.createBytes(PythonLanguage.get(inliningTarget), copyToByteArrayNode.execute(resultString, TS_ENCODING_BINARY));
            } else {
                resultObject = resultString;
            }
            if (returnTuple) {
                return PFactory.createTuple(PythonLanguage.get(inliningTarget), new Object[]{resultObject, n});
            } else {
                return resultObject;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isCallable", "binary"})
        static Object doBinary(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacementObj, TruffleString input,
                        Object originalInput, int count, boolean binary, boolean isCallable, boolean returnTuple, int stringLength, TruffleStringBuilder result,
                        @Cached @Shared PatternNodes.RECheckInputTypeNode checkInputTypeNode,
                        @Cached @Exclusive PatternNodes.TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached @Exclusive SubnInnerNode3 innerNode) {
            checkInputTypeNode.execute(frame, replacementObj, binary);
            TruffleString replacement = bufferToTruffleStringNode.execute(frame, replacementObj);
            return innerNode.execute(inliningTarget, frame, compiledRegex, compiledRegexMustAdvance, replacement, input, count, binary, returnTuple, stringLength, result);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isCallable", "!binary"})
        static Object doString(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacementObj, TruffleString input,
                        Object originalInput, int count, boolean binary, boolean isCallable, boolean returnTuple, int stringLength, TruffleStringBuilder result,
                        @Cached @Shared PatternNodes.RECheckInputTypeNode checkInputTypeNode,
                        @Cached @Exclusive CastToTruffleStringNode cast,
                        @Cached @Exclusive SubnInnerNode3 innerNode) {
            checkInputTypeNode.execute(frame, replacementObj, binary);
            TruffleString replacement = cast.castKnownString(inliningTarget, replacementObj);
            return innerNode.execute(inliningTarget, frame, compiledRegex, compiledRegexMustAdvance, replacement, input, count, binary, returnTuple, stringLength, result);
        }

    }

    /**
     * Third level: method implementation and caching of non-callable replacement objects.
     */
    @GenerateInline
    abstract static class SubnInnerNode3 extends Node {

        private static final int UNROLL_MAX = 4;

        abstract Object execute(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString replacement, TruffleString input,
                        int count, boolean binary, boolean returnTuple, int stringLength, TruffleStringBuilder result);

        @SuppressWarnings("unused")
        @Specialization(guards = "replacement == cachedReplacement", limit = "2")
        static Object doCached(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString replacement, TruffleString input,
                        int count, boolean binary, boolean returnTuple, int stringLength, TruffleStringBuilder result,
                        @Cached("replacement") TruffleString cachedReplacement,
                        @Cached @Shared ParseReplacementNode parseReplacementNode,
                        @Cached("parseReplacementNode.execute(inliningTarget, frame, compiledRegex, replacement, binary)") ParsedReplacement cachedParsedReplacement,
                        @Cached @Shared TRegexUtil.InvokeExecMethodNode invokeExecMethodNodeMustAdvance,
                        @Cached @Shared TRegexUtil.InvokeExecMethodNode invokeExecMethodNode,
                        @Cached @Shared TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        @Cached @Shared TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached @Shared TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached @Shared TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached @Shared TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                        @Cached @Shared TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached @Shared TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            return doReplace(compiledRegex, compiledRegexMustAdvance, replacement, input, count, binary, returnTuple, stringLength, result, cachedParsedReplacement,
                            inliningTarget,
                            invokeExecMethodNodeMustAdvance,
                            invokeExecMethodNode,
                            readIsMatchNode,
                            readStartNode,
                            readEndNode,
                            appendCodePointNode,
                            appendSubstringNode,
                            toStringNode,
                            copyToByteArrayNode);
        }

        @Specialization(replaces = "doCached")
        static Object doOnTheFly(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString replacement, TruffleString input,
                        int count, boolean binary, boolean returnTuple, int stringLength, TruffleStringBuilder result,
                        @Cached @Shared ParseReplacementNode parseReplacementNode,
                        @Cached @Shared TRegexUtil.InvokeExecMethodNode invokeExecMethodNodeMustAdvance,
                        @Cached @Shared TRegexUtil.InvokeExecMethodNode invokeExecMethodNode,
                        @Cached @Shared TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        @Cached @Shared TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached @Shared TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached @Shared TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached @Shared TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                        @Cached @Shared TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached @Shared TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            ParsedReplacement parsedReplacement = parseReplacementNode.execute(inliningTarget, frame, compiledRegex, replacement, binary);
            return doReplace(compiledRegex, compiledRegexMustAdvance, replacement, input, count, binary, returnTuple, stringLength, result, parsedReplacement,
                            inliningTarget,
                            invokeExecMethodNodeMustAdvance,
                            invokeExecMethodNode,
                            readIsMatchNode,
                            readStartNode,
                            readEndNode,
                            appendCodePointNode,
                            appendSubstringNode,
                            toStringNode,
                            copyToByteArrayNode);
        }

        private static Object doReplace(Object compiledRegex, Object compiledRegexMustAdvance, TruffleString replacement, TruffleString input, int count, boolean binary, boolean returnTuple,
                        int stringLength, TruffleStringBuilder result, ParsedReplacement parsedReplacement, Node inliningTarget,
                        TRegexUtil.InvokeExecMethodNode invokeExecMethodNodeMustAdvance,
                        TRegexUtil.InvokeExecMethodNode invokeExecMethodNode,
                        TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                        TruffleStringBuilder.ToStringNode toStringNode,
                        TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            int n = 0;
            int pos = 0;
            boolean mustAdvance = false;
            while ((count == 0 || n < count) && pos < stringLength) {
                final Object searchResult;
                if (mustAdvance) {
                    searchResult = invokeExecMethodNodeMustAdvance.execute(inliningTarget, compiledRegexMustAdvance, input, pos);
                } else {
                    searchResult = invokeExecMethodNode.execute(inliningTarget, compiledRegex, input, pos);
                }
                if (!TRegexUtil.TRegexResultAccessor.isMatch(searchResult, inliningTarget, readIsMatchNode)) {
                    break;
                }
                n++;
                int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, 0, inliningTarget, readStartNode);
                int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, 0, inliningTarget, readEndNode);
                appendSubstringNode.execute(result, input, toByteIndex(pos, binary), toByteIndex(start - pos, binary));
                if (CompilerDirectives.isPartialEvaluationConstant(parsedReplacement) && parsedReplacement.size() <= UNROLL_MAX) {
                    applyReplacementUnrolled(parsedReplacement, result, replacement, input, searchResult, binary, inliningTarget, readStartNode, readEndNode, appendCodePointNode, appendSubstringNode);
                } else {
                    applyReplacement(parsedReplacement, result, replacement, input, searchResult, binary, inliningTarget, readStartNode, readEndNode, appendCodePointNode, appendSubstringNode);
                }
                pos = end;
                mustAdvance = start == end;
            }
            final TruffleString resultString;
            if (n == 0) {
                resultString = input;
            } else {
                appendSubstringNode.execute(result, input, toByteIndex(pos, binary), toByteIndex(stringLength - pos, binary));
                resultString = toStringNode.execute(result, binary);
            }
            final Object resultObject;
            if (binary) {
                resultObject = PFactory.createBytes(PythonLanguage.get(inliningTarget), copyToByteArrayNode.execute(resultString, TS_ENCODING_BINARY));
            } else {
                resultObject = resultString;
            }
            if (returnTuple) {
                return PFactory.createTuple(PythonLanguage.get(inliningTarget), new Object[]{resultObject, n});
            } else {
                return resultObject;
            }
        }

        @ExplodeLoop
        private static void applyReplacementUnrolled(ParsedReplacement parsedReplacement, TruffleStringBuilder result, TruffleString replacement, TruffleString string,
                        Object searchResult, boolean binary, Node inliningTarget,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode) {
            CompilerAsserts.partialEvaluationConstant(parsedReplacement);
            int size = parsedReplacement.size();
            CompilerAsserts.partialEvaluationConstant(size);
            for (int i = 0; i < size; i++) {
                parsedReplacement.apply(i, replacement, string, result, binary, searchResult, inliningTarget, readStartNode, readEndNode, appendCodePointNode, appendSubstringByteIndexNode);
            }
        }

        private static void applyReplacement(ParsedReplacement parsedReplacement, TruffleStringBuilder result, TruffleString replacement, TruffleString string,
                        Object searchResult, boolean binary, Node inliningTarget,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode) {
            int size = parsedReplacement.size();
            for (int i = 0; i < size; i++) {
                parsedReplacement.apply(i, replacement, string, result, binary, searchResult, inliningTarget, readStartNode, readEndNode, appendCodePointNode, appendSubstringByteIndexNode);
            }
        }
    }

    static final class ParsedReplacement {

        private static final ParsedReplacement EMPTY = new ParsedReplacement(new int[]{});

        private static final int TOKEN_KIND_CODEPOINT = -1;
        private static final int TOKEN_KIND_GROUP_REF = -2;

        @CompilationFinal(dimensions = 1) private final int[] tokens;

        private ParsedReplacement(int[] tokens) {
            this.tokens = tokens;
        }

        private int size() {
            return tokens.length >> 1;
        }

        private void apply(int iToken, TruffleString replacement, TruffleString input, TruffleStringBuilder result, boolean binary, Object searchResult,
                        Node inliningTarget,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode) {
            int tokenPart0 = tokens[iToken << 1];
            int tokenPart1 = tokens[(iToken << 1) + 1];
            if (tokenPart0 == TOKEN_KIND_CODEPOINT) {
                appendCodePointNode.execute(result, tokenPart1);
            } else {
                final int start;
                final int length;
                final TruffleString s;
                if (tokenPart0 == TOKEN_KIND_GROUP_REF) {
                    start = toByteIndex(TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, tokenPart1, inliningTarget, readStartNode), binary);
                    if (start < 0) {
                        return;
                    }
                    length = toByteIndex(TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, tokenPart1, inliningTarget, readEndNode), binary) - start;
                    s = input;
                } else {
                    start = tokenPart0;
                    length = tokenPart1;
                    s = replacement;
                }
                appendSubstringByteIndexNode.execute(result, s, start, length);
            }
        }

        private static final class Builder {
            private final IntArrayBuilder tokens = new IntArrayBuilder();

            void codepoint(int codepoint) {
                tokens.add(TOKEN_KIND_CODEPOINT);
                tokens.add(codepoint);
            }

            void literal(int fromByteIndex, int toByteIndex) {
                tokens.add(fromByteIndex);
                tokens.add(toByteIndex - fromByteIndex);
            }

            void groupReference(int groupNumber) {
                tokens.add(TOKEN_KIND_GROUP_REF);
                tokens.add(groupNumber);
            }

            ParsedReplacement build() {
                return new ParsedReplacement(tokens.toArray());
            }
        }
    }

    @GenerateInline
    abstract static class ParseReplacementNode extends Node {

        abstract ParsedReplacement execute(Node inliningTarget, VirtualFrame frame, Object tregexCompiledRegex, TruffleString replacement, boolean binary);

        @Specialization
        static ParsedReplacement parseReplacement(Node inliningTarget, VirtualFrame frame, Object tregexCompiledRegex, TruffleString replacement, boolean binary,
                        @Cached TruffleString.ByteIndexOfCodePointNode indexOfNode,
                        @Cached TruffleString.CodePointAtByteIndexNode codePointAtByteIndexNode,
                        @Cached TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TRegexUtil.InteropReadMemberNode readGroupCountNode,
                        @Cached TRegexUtil.InteropReadMemberNode readNamedGroupsNode,
                        @CachedLibrary(limit = "3") InteropLibrary genericInteropLib,
                        @Cached PRaiseNode raiseNode,
                        @Cached RaiseRegexErrorNode raiseRegexErrorNode,
                        @Cached StringUtils.IsIdentifierNode isIdentifierNode,
                        @Cached InlinedBranchProfile errorProfile) {
            CompilerAsserts.partialEvaluationConstant(binary);
            assert TS_ENCODING == TruffleString.Encoding.UTF_32 : "replace codepointLengthAscii with 1 when switching to UTF-8";
            int codepointLengthAscii = binary ? 1 : 4;
            TruffleString.Encoding encoding = binary ? TS_ENCODING_BINARY : TS_ENCODING;
            ParsedReplacement.Builder builder = new ParsedReplacement.Builder();
            int length = replacement.byteLength(encoding);
            int numberOfCaptureGroups = TRegexUtil.TRegexCompiledRegexAccessor.groupCount(tregexCompiledRegex, inliningTarget, readGroupCountNode);
            int lastPos = 0;
            int lastLiteralPos = 0;
            while (lastPos < length) {
                int backslashPos = indexOfNode.execute(replacement, '\\', lastPos, length, encoding);
                builder.literal(lastLiteralPos, backslashPos < 0 ? length : backslashPos);
                if (backslashPos < 0) {
                    return builder.build();
                }
                int nextCPPos = backslashPos + codepointLengthAscii;
                if (nextCPPos >= length) {
                    throw raiseRegexErrorNode.execute(frame, BAD_ESCAPE_END_OF_STRING, replacement, toCodepointIndex(length, binary));
                }
                int firstCodepoint = codePointAtByteIndexNode.execute(replacement, nextCPPos, encoding);
                nextCPPos += codepointLengthAscii;
                int secondCodepoint = nextCPPos < length ? codePointAtByteIndexNode.execute(replacement, nextCPPos, encoding) : -1;
                if (firstCodepoint == 'g') {
                    if (secondCodepoint != '<') {
                        throw raiseRegexErrorNode.execute(frame, MISSING_LEFT_ANGLE_BRACKET, replacement, toCodepointIndex(nextCPPos, binary));
                    }
                    int nameStartPos = nextCPPos + codepointLengthAscii;
                    int nameEndPos = 0;
                    if (nameStartPos >= length || (nameEndPos = indexOfNode.execute(replacement, '>', nameStartPos, length, encoding)) < 0 || nameEndPos == nameStartPos) {
                        errorProfile.enter(inliningTarget);
                        throw raiseRegexErrorNode.execute(frame, nameStartPos >= length || nameEndPos == nameStartPos ? MISSING_GROUP_NAME : MISSING_RIGHT_ANGLE_BRACKET, replacement,
                                        toCodepointIndex(nameStartPos, binary));
                    }
                    int nameLength = nameEndPos - nameStartPos;
                    assert nameLength > 0;
                    TruffleString name = substringByteIndexNode.execute(replacement, nameStartPos, nameLength, encoding, true);
                    int groupNumber = -1;
                    boolean ascii = getCodeRangeNode.execute(name, encoding) == TruffleString.CodeRange.ASCII;
                    if (ascii) {
                        groupNumber = 0;
                        for (int i = 0; i < nameLength; i += codepointLengthAscii) {
                            int d = codePointAtByteIndexNode.execute(name, i, encoding);
                            if (isDecimalDigit(d)) {
                                groupNumber = (groupNumber * 10) + digitValue(d);
                            } else {
                                groupNumber = -1;
                                break;
                            }
                            if (groupNumber >= numberOfCaptureGroups) {
                                errorProfile.enter(inliningTarget);
                                throw raiseRegexErrorNode.executeFormatted(frame, INVALID_GROUP_REFERENCE, replacement, toCodepointIndex(nameStartPos, binary), name);
                            }
                        }
                    }
                    if (groupNumber < 0) {
                        if (!isIdentifierNode.execute(inliningTarget, name) || binary && !ascii) {
                            errorProfile.enter(inliningTarget);
                            throw raiseRegexErrorNode.executeFormatted(frame, BAD_CHAR_IN_GROUP_NAME, replacement, toCodepointIndex(nameStartPos, binary),
                                            binary ? PyObjectAsciiNode.executeUncached(name) : PyObjectReprAsTruffleStringNode.executeUncached(name));
                        }
                        Object namedCaptureGroups = TRegexUtil.TRegexCompiledRegexAccessor.namedCaptureGroups(tregexCompiledRegex, inliningTarget, readNamedGroupsNode);
                        if (!TRegexUtil.TRegexNamedCaptureGroupsAccessor.hasGroup(namedCaptureGroups, name, genericInteropLib)) {
                            throw raiseNode.raise(inliningTarget, PythonErrorType.IndexError, UNKNOWN_GROUP_NAME, name);
                        }
                        groupNumber = TRegexUtil.TRegexNamedCaptureGroupsAccessor.getGroupNumber(namedCaptureGroups, name, genericInteropLib);
                    }
                    builder.groupReference(groupNumber);
                    nextCPPos = nameEndPos + codepointLengthAscii;
                    lastPos = lastLiteralPos = nextCPPos;
                } else if (firstCodepoint == '0') {
                    int octalEscape;
                    if (isOctalDigit(secondCodepoint)) {
                        nextCPPos += codepointLengthAscii;
                        octalEscape = digitValue(secondCodepoint);
                        if (nextCPPos < length) {
                            int thirdCodepoint = codePointAtByteIndexNode.execute(replacement, nextCPPos, encoding);
                            if (isOctalDigit(thirdCodepoint)) {
                                nextCPPos += codepointLengthAscii;
                                octalEscape = (octalEscape * 8) + digitValue(thirdCodepoint);
                            }
                        }
                    } else {
                        octalEscape = 0;
                    }
                    builder.codepoint(octalEscape);
                    lastPos = lastLiteralPos = nextCPPos;
                } else if (isDecimalDigit(firstCodepoint)) {
                    int groupNumber = digitValue(firstCodepoint);
                    if (isDecimalDigit(secondCodepoint)) {
                        nextCPPos += codepointLengthAscii;
                        int thirdCodepoint;
                        if (Math.max(firstCodepoint, secondCodepoint) <= '7' && nextCPPos < length &&
                                        isOctalDigit(thirdCodepoint = codePointAtByteIndexNode.execute(replacement, nextCPPos, encoding))) {
                            nextCPPos += codepointLengthAscii;
                            // Single and double-digit escapes are group references, but three-digit
                            // escapes are octal character codes. Hopefully this will be deprecated
                            // at some point
                            int octalEscape = digitValue(firstCodepoint) * 64 + digitValue(secondCodepoint) * 8 + digitValue(thirdCodepoint);
                            if (octalEscape > 0xff) {
                                errorProfile.enter(inliningTarget);
                                TruffleString octalEscapeString = replacement.substringByteIndexUncached(backslashPos, nextCPPos - backslashPos, encoding, true);
                                throw raiseRegexErrorNode.executeFormatted(frame, OCTAL_ESCAPE_OUT_OF_RANGE, replacement, toCodepointIndex(backslashPos, binary), octalEscapeString);
                            }
                            builder.codepoint(octalEscape);
                            groupNumber = -1;
                        } else {
                            groupNumber = groupNumber * 10 + digitValue(secondCodepoint);
                        }
                    }
                    if (groupNumber >= 0) {
                        if (groupNumber >= numberOfCaptureGroups) {
                            errorProfile.enter(inliningTarget);
                            throw raiseRegexErrorNode.executeFormatted(frame, INVALID_GROUP_REFERENCE, replacement, toCodepointIndex(backslashPos + codepointLengthAscii, binary), groupNumber);
                        }
                        builder.groupReference(groupNumber);
                    }
                    lastPos = lastLiteralPos = nextCPPos;
                } else {
                    int escape = switch (firstCodepoint) {
                        case 'a' -> '\u0007';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'v' -> '\u000b';
                        case '\\' -> '\\';
                        default -> {
                            // check if character is in [A-Za-z]
                            int lowercased = firstCodepoint | 0x20;
                            if ('a' <= lowercased && lowercased <= 'z') {
                                // nextCPPos points at a character next to firstCodepoint
                                int startAt = toCodepointIndex(nextCPPos, binary) - 2;
                                throw raiseRegexErrorNode.executeFormatted(frame, BAD_ESCAPE_S, replacement, startAt, toEscapeSequence(firstCodepoint));
                            } else {
                                yield -1;
                            }
                        }
                    };
                    if (escape >= 0) {
                        // valid escape sequence
                        builder.codepoint(escape);
                        lastLiteralPos = nextCPPos;
                    }
                    lastPos = nextCPPos;
                }
            }

            builder.literal(lastLiteralPos, length);
            return builder.build();
        }

        @TruffleBoundary
        static String toEscapeSequence(int codepoint) {
            return "\\" + (char) codepoint;
        }
    }

    @GenerateInline(false) // Only for errors
    @GenerateUncached
    abstract static class RaiseRegexErrorNode extends Node {
        private static final TruffleString T_ERROR = tsLiteral("error");

        public final PException execute(VirtualFrame frame, TruffleString message, Object pattern, int position) {
            return executeWithPatternAndPosition(frame, message, pattern, position);
        }

        public final PException executeFormatted(VirtualFrame frame, TruffleString message, Object pattern, int position, Object... formatArgs) {
            return execute(frame, doFormat(message, formatArgs), pattern, position);
        }

        @TruffleBoundary
        private static TruffleString doFormat(TruffleString message, Object[] formatArgs) {
            return TruffleString.fromJavaStringUncached(ErrorMessageFormatter.format(message, formatArgs), TS_ENCODING);
        }

        public abstract PException executeWithPatternAndPosition(VirtualFrame frame, TruffleString message, Object pattern, Object position);

        public static PException executeWithPatternAndPositionUncached(TruffleString message, Object pattern, Object position, Node location) {
            return createAndRaise(null, message, pattern, position, location, PythonContext.get(location), ReadAttributeFromModuleNode.getUncached(), CallNode.getUncached());
        }

        @Specialization
        static PException createAndRaise(VirtualFrame frame, TruffleString message, Object pattern, Object position,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached ReadAttributeFromModuleNode readAttribute,
                        @Cached CallNode callNode) {
            PythonModule module = context.lookupBuiltinModule(T__SRE);
            Object errorType = readAttribute.execute(module, T_ERROR);
            assert !(errorType instanceof PNone);
            Object exception = callNode.execute(frame, errorType, message, pattern, position);
            throw PRaiseNode.raiseExceptionObjectStatic(inliningTarget, exception);
        }
    }

    static Object createSubstring(Node inliningTarget, TruffleString input, boolean binary, int substringStart, int substringEnd,
                    TruffleString.SubstringByteIndexNode substringByteIndexNode,
                    TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        CompilerAsserts.partialEvaluationConstant(binary);
        assert substringStart >= 0 && substringEnd >= substringStart;
        int byteIndexStart = toByteIndex(substringStart, binary);
        int byteLength = toByteIndex(substringEnd - substringStart, binary);
        if (binary) {
            byte[] bytes = new byte[byteLength];
            copyToByteArrayNode.execute(input, byteIndexStart, bytes, 0, byteLength, TS_ENCODING_BINARY);
            return PFactory.createBytes(PythonLanguage.get(inliningTarget), bytes);
        } else {
            return substringByteIndexNode.execute(input, byteIndexStart, byteLength, TS_ENCODING, false);
        }
    }

    private static int toByteIndex(int index, boolean binary) {
        assert TS_ENCODING == TruffleString.Encoding.UTF_32 : "remove this method when switching to UTF-8";
        return binary ? index : index << 2;
    }

    private static int toCodepointIndex(int i, boolean binary) {
        assert TS_ENCODING == TruffleString.Encoding.UTF_32 : "remove this when switching to UTF-8";
        return binary ? i : i >> 2;
    }

    private static int digitValue(int d) {
        assert isDecimalDigit(d);
        return d - '0';
    }

    private static boolean isDecimalDigit(int d) {
        return '0' <= d && d <= '9';
    }

    private static boolean isOctalDigit(int d) {
        return '0' <= d && d <= '7';
    }
}
