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
import com.oracle.graal.python.builtins.modules.re.SREModuleBuiltins.PythonMethod;
import com.oracle.graal.python.builtins.modules.re.SREModuleBuiltins.TRegexCache;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static com.oracle.graal.python.builtins.modules.re.SREModuleBuiltins.TRegexCache.FLAG_ASCII;
import static com.oracle.graal.python.builtins.modules.re.SREModuleBuiltins.TRegexCache.FLAG_DOTALL;
import static com.oracle.graal.python.builtins.modules.re.SREModuleBuiltins.TRegexCache.FLAG_IGNORECASE;
import static com.oracle.graal.python.builtins.modules.re.SREModuleBuiltins.TRegexCache.FLAG_LOCALE;
import static com.oracle.graal.python.builtins.modules.re.SREModuleBuiltins.TRegexCache.FLAG_MULTILINE;
import static com.oracle.graal.python.builtins.modules.re.SREModuleBuiltins.TRegexCache.FLAG_UNICODE;
import static com.oracle.graal.python.builtins.modules.re.SREModuleBuiltins.TRegexCache.FLAG_VERBOSE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PPattern})
public final class PatternBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = PatternBuiltinsSlotsGen.SLOTS;

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
        static Object newPattern(VirtualFrame frame, Object cls, Object source, int flags,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "1") InteropLibrary interop,
                        @Cached SREModuleBuiltins.TRegexCompileInner tRegexCompileInnerNode,
                        @Cached TRegexUtil.InteropReadMemberNode interopReadMemberNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            try {
                var cache = new TRegexCache(inliningTarget, source, flags);

                boolean mustAdvance = false;
                Object regexObject = tRegexCompileInnerNode.execute(frame, cache, PythonMethod.Search, mustAdvance);

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

                Shape shape = getInstanceShape.execute(cls);
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
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            // pattern source string representation
            Object sourceReprObject = callMethodObjArgs.execute(frame, inliningTarget, self.source, T___REPR__);
            String sourceReprString = castToJavaStringNode.execute(sourceReprObject);

            return format(sourceReprString, self.flags, self.cache.isBinary());
        }

        @TruffleBoundary
        static private TruffleString format(String sourceReprString, int flags, boolean isBinary) {
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
                        @Cached SREModuleBuiltins.TRegexCompileInner tRegexCompile,
                        @Cached SREModuleBuiltins.RECheckInputTypeNode checkInputTypeNode,
                        @Cached TRegexUtil.InteropReadMemberNode readGroupCountNode,
                        @Cached(inline = true) SREModuleBuiltins.TRegexRESplitInnerNode1 innerNode) {
            Object compiledRegex = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, false);
            Object compiledRegexMustAdvance = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, true);

            if (compiledRegex == PNone.NONE || compiledRegexMustAdvance == PNone.NONE) {
                SREModuleBuiltins.bailoutUnsupportedRegex(self.cache);
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
                        @Cached SREModuleBuiltins.TRegexCompileInner tRegexCompile,
                        @Cached SREModuleBuiltins.RECheckInputTypeNode checkInputTypeNode,
                        @Cached TRegexUtil.InteropReadMemberNode readGroupCountNode,
                        @Cached(inline = true) SREModuleBuiltins.TRegexREFindAllInnerNode1 innerNode) {
            Object compiledRegex = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, false);
            Object compiledRegexMustAdvance = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, true);

            if (compiledRegex == PNone.NONE || compiledRegexMustAdvance == PNone.NONE) {
                SREModuleBuiltins.bailoutUnsupportedRegex(self.cache);
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
                        @Cached SREModuleBuiltins.RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PyObjectGetAttr getAttr) {
            reCheckInputTypeNode.execute(frame, stringObject, self.cache.isBinary());

            // reuse _sre.SREScanner#search() and wrap it into an iterator
            Object cls = PythonBuiltinClassType.SREScanner;
            Shape shape = getInstanceShape.execute(cls);
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
                        @Cached SREModuleBuiltins.TRegexCompileInner tRegexCompile,
                        @Cached SREModuleBuiltins.RECheckInputTypeNode checkInputTypeNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached(inline = true) SREModuleBuiltins.TRegexRESubnInnerNode1 innerNode1) {
            Object compiledRegex = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, false);
            Object compiledRegexMustAdvance = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, true);

            if (compiledRegex == PNone.NONE || compiledRegexMustAdvance == PNone.NONE) {
                SREModuleBuiltins.bailoutUnsupportedRegex(self.cache);
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
                        @Cached SREModuleBuiltins.TRegexCompileInner tRegexCompile,
                        @Cached SREModuleBuiltins.RECheckInputTypeNode checkInputTypeNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached(inline = true) SREModuleBuiltins.TRegexRESubnInnerNode1 innerNode1) {
            Object compiledRegex = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, false);
            Object compiledRegexMustAdvance = tRegexCompile.execute(frame, self.cache, PythonMethod.Search, true);

            if (compiledRegex == PNone.NONE || compiledRegexMustAdvance == PNone.NONE) {
                SREModuleBuiltins.bailoutUnsupportedRegex(self.cache);
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
                        @Cached SREModuleBuiltins.RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            // raise error immediately
            reCheckInputTypeNode.execute(frame, string, self.cache.isBinary());

            Object cls = PythonBuiltinClassType.SREScanner;
            Shape shape = getInstanceShape.execute(cls);
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
}
