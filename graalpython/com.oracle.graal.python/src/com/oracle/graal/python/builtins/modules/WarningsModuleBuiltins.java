/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.BuiltinNames.J__WARNINGS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MODULE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MODULES;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SYS;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WARNINGS;
import static com.oracle.graal.python.nodes.BuiltinNames.T___MAIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___LOADER__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_CLEAR;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_GET;
import static com.oracle.graal.python.nodes.StringLiterals.T_COLON;
import static com.oracle.graal.python.nodes.StringLiterals.T_DEFAULT;
import static com.oracle.graal.python.nodes.StringLiterals.T_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_PY_EXTENSION;
import static com.oracle.graal.python.nodes.StringLiterals.T_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRING_SOURCE;
import static com.oracle.graal.python.nodes.StringLiterals.T_VALUE_UNKNOWN;
import static com.oracle.graal.python.nodes.StringLiterals.T_VERSION;
import static com.oracle.graal.python.nodes.StringLiterals.T_WARNINGS;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.IllegalFormatException;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsSubClassNode;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltinsClinicProviders.WarnBuiltinNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltinsFactory.WarnBuiltinNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictFromGlobalsNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__WARNINGS)
public final class WarningsModuleBuiltins extends PythonBuiltins {
    private static final TruffleString T___WARNINGREGISTRY__ = tsLiteral("__warningregistry__");

    private static final String J_WARN = "warn";
    private static final String J_WARN_EXPLICIT = "warn_explicit";
    public static final TruffleString T_WARN = tsLiteral(J_WARN);
    public static final TruffleString T_WARN_EXPLICIT = tsLiteral(J_WARN_EXPLICIT);

    private static final TruffleString T_ERROR = tsLiteral("error");
    private static final TruffleString T_ALWAYS = tsLiteral("always");
    private static final TruffleString T_ONCE = tsLiteral("once");
    private static final TruffleString T_IGNORE = tsLiteral("ignore");
    private static final TruffleString T_MATCH = tsLiteral("match");
    private static final TruffleString T_GET_SOURCE = tsLiteral("get_source");
    private static final TruffleString T__SHOWWARNMSG = tsLiteral("_showwarnmsg");
    private static final TruffleString T_ONCEREGISTRY = tsLiteral("onceregistry");
    private static final TruffleString T_DEFAULTACTION = tsLiteral("defaultaction");
    private static final TruffleString T_FILTERS = tsLiteral("filters");
    private static final TruffleString T_WARNING_MESSAGE = tsLiteral("WarningMessage");
    private static final TruffleString T_UNKNOWN_SOURCE = tsLiteral("<unknown source>");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WarningsModuleBuiltinsFactory.getFactories();
    }

    // _Warnings_InitState done via initialize and postInitialize
    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant(SpecialAttributeNames.T___DOC__, "_warnings provides basic warning filtering support.\n" +
                        "It is a helper module to speed up interpreter start-up.");
        // we need to copy the attrs, since they must still be available even if the user `del`s the
        // attrs
        addBuiltinConstant("_defaultaction", T_DEFAULT);
        PDict onceregistry = core.factory().createDict();
        addBuiltinConstant("_onceregistry", onceregistry);
        PList filters = initFilters(core.factory());
        addBuiltinConstant("filters", filters);
        ModuleState moduleState = new ModuleState();
        moduleState.filtersVersion = 0L;
        moduleState.filters = filters;
        moduleState.onceRegistry = onceregistry;
        moduleState.defaultAction = T_DEFAULT;
        core.lookupBuiltinModule(T__WARNINGS).setModuleState(moduleState);
        super.initialize(core);
    }

    private static PTuple createFilter(PythonObjectSlowPathFactory factory, PythonBuiltinClassType cat, TruffleString id, Object mod) {
        return factory.createTuple(new Object[]{id, PNone.NONE, cat, mod, 0});
    }

    // init_filters
    private static PList initFilters(PythonObjectSlowPathFactory factory) {
        return factory.createList(new Object[]{
                        createFilter(factory, PythonBuiltinClassType.DeprecationWarning, T_DEFAULT, T___MAIN__),
                        createFilter(factory, PythonBuiltinClassType.DeprecationWarning, T_IGNORE, PNone.NONE),
                        createFilter(factory, PythonBuiltinClassType.PendingDeprecationWarning, T_IGNORE, PNone.NONE),
                        createFilter(factory, PythonBuiltinClassType.ImportWarning, T_IGNORE, PNone.NONE),
                        createFilter(factory, PythonBuiltinClassType.ResourceWarning, T_IGNORE, PNone.NONE)});
    }

    static final class WarningsModuleNode extends Node {
        @Child CastToTruffleStringNode castStr;
        @Child PRaiseNode raiseNode;
        @Child PyObjectRichCompareBool.EqNode eqNode;
        @Child GetClassNode getClassNode;
        @Child PyNumberAsSizeNode asSizeNode;
        @Child PyObjectIsTrueNode isTrueNode;
        @Child PythonObjectFactory factory;
        @Child IsSubClassNode isSubClassNode;
        @Child GetOrCreateDictNode getDictNode;
        @Child GetDictFromGlobalsNode getDictFromGlobalsNode;
        @Child ReadCallerFrameNode readCallerNode;
        @Child PyObjectLookupAttr lookupAttrNode;
        @Child PyObjectCallMethodObjArgs callMethodNode;
        @Child PyDictGetItem dictGetItemNode;
        @Child PyObjectSetItem setItemNode;
        @Child PyObjectStrAsObjectNode strNode;
        @Child CallNode callNode;
        @Child SequenceStorageNodes.GetItemScalarNode sequenceGetItemNode;
        @Child TypeNodes.IsTypeNode isTypeNode;
        @Child TruffleString.CodePointLengthNode codePointLengthNode;
        @Child TruffleString.RegionEqualNode regionEqualNode;
        @Child TruffleString.EqualNode equalNode;
        @Child TruffleString.SubstringNode substringNode;

        @NeverDefault
        static WarningsModuleNode create() {
            return new WarningsModuleNode();
        }

        private static Object tryImport() {
            return AbstractImportNode.importModule(T_WARNINGS);
        }

        private PythonLanguage getLanguage() {
            return PythonLanguage.get(this);
        }

        private PythonContext getContext() {
            return PythonContext.get(this);
        }

        private PyObjectRichCompareBool.EqNode getEqNode() {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(PyObjectRichCompareBool.EqNode.create());
            }
            return eqNode;
        }

        private TypeNodes.IsTypeNode getIsTypeNode() {
            if (isTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isTypeNode = insert(TypeNodes.IsTypeNode.create());
            }
            return isTypeNode;
        }

        private PyObjectLookupAttr getLookupAttrNode() {
            if (lookupAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupAttrNode = insert(PyObjectLookupAttr.create());
            }
            return lookupAttrNode;
        }

        private PyObjectCallMethodObjArgs getCallMethodNode() {
            if (callMethodNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callMethodNode = insert(PyObjectCallMethodObjArgs.create());
            }
            return callMethodNode;
        }

        private PyDictGetItem getDictGetItemNode() {
            if (dictGetItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dictGetItemNode = insert(PyDictGetItem.create());
            }
            return dictGetItemNode;
        }

        private PyObjectSetItem getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(PyObjectSetItem.create());
            }
            return setItemNode;
        }

        private PyObjectStrAsObjectNode getStrNode() {
            if (strNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                strNode = insert(PyObjectStrAsObjectNode.create());
            }
            return strNode;
        }

        private CallNode getCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(CallNode.create());
            }
            return callNode;
        }

        private SequenceStorageNodes.GetItemScalarNode getSequenceGetItemNode() {
            if (sequenceGetItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                sequenceGetItemNode = insert(SequenceStorageNodes.GetItemScalarNode.create());
            }
            return sequenceGetItemNode;
        }

        private Object getPythonClass(Object object) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode.executeCached(object);
        }

        private PyNumberAsSizeNode getAsSizeNode() {
            if (asSizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asSizeNode = insert(PyNumberAsSizeNode.create());
            }
            return asSizeNode;
        }

        private PyObjectIsTrueNode getIsTrueNode() {
            if (isTrueNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isTrueNode = insert(PyObjectIsTrueNode.create());
            }
            return isTrueNode;
        }

        private CastToTruffleStringNode getCastStr() {
            if (castStr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                castStr = insert(CastToTruffleStringNode.create());
            }
            return castStr;
        }

        private TruffleString.CodePointLengthNode getCodePointLengthNode() {
            if (codePointLengthNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                codePointLengthNode = insert(TruffleString.CodePointLengthNode.create());
            }
            return codePointLengthNode;
        }

        private TruffleString.RegionEqualNode getRegionEqualNode() {
            if (regionEqualNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                regionEqualNode = insert(TruffleString.RegionEqualNode.create());
            }
            return regionEqualNode;
        }

        private TruffleString.EqualNode getEqualNode() {
            if (equalNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equalNode = insert(TruffleString.EqualNode.create());
            }
            return equalNode;
        }

        private TruffleString.SubstringNode getSubstringNode() {
            if (substringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                substringNode = insert(TruffleString.SubstringNode.create());
            }
            return substringNode;
        }

        private PRaiseNode getRaise() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

        private PythonObjectFactory getFactory() {
            if (factory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                factory = insert(PythonObjectFactory.create());
            }
            return factory;
        }

        private IsSubClassNode getIsSubClass() {
            if (isSubClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                isSubClassNode = insert(IsSubClassNode.create());
            }
            return isSubClassNode;
        }

        private PDict getSysDict() {
            if (getDictNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                getDictNode = insert(GetOrCreateDictNode.create());
            }
            return getDictNode.executeCached(getContext().lookupBuiltinModule(T_SYS));
        }

        private PDict getGlobalsDict(Object globals) {
            if (getDictFromGlobalsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                getDictFromGlobalsNode = insert(GetDictFromGlobalsNode.create());
            }
            return getDictFromGlobalsNode.executeCached(globals);
        }

        private PFrame getCallerFrame(VirtualFrame frame, int stackLevel) {
            if (readCallerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                readCallerNode = insert(ReadCallerFrameNode.create());
            }
            return readCallerNode.executeWith(PArguments.getCurrentFrameInfo(frame), ReadCallerFrameNode.FrameSelector.SKIP_INTERNAL, stackLevel);
        }

        // _Warnings_GetState split up

        /**
         * May be on the fast path, depending on the passed node, which must be either cached or
         * uncached.
         */
        private static long getStateFiltersVersion(PythonModule warningsModule) {
            return warningsModule.getModuleState(ModuleState.class).filtersVersion;
        }

        /**
         * On fast path.
         */
        private Object getStateFilters(PythonModule warningsModule) {
            return warningsModule.getModuleState(ModuleState.class).filters;
        }

        /**
         * On slow path.
         */
        private static Object getStateOnceRegistry(PythonModule warningsModule) {
            return warningsModule.getModuleState(ModuleState.class).onceRegistry;
        }

        /**
         * On fast path.
         */
        private Object getStateDefaultAction(PythonModule warningsModule) {
            return warningsModule.getModuleState(ModuleState.class).defaultAction;
        }

        /**
         * On fast path.
         */
        private boolean checkMatched(VirtualFrame frame, Object obj, Object arg) {
            if (obj == PNone.NONE) {
                return true;
            }
            try {
                TruffleString objStr = getCastStr().executeCached(obj);
                try {
                    return getEqualNode().execute(objStr, getCastStr().executeCached(arg), TS_ENCODING);
                } catch (CannotCastException e) {
                    // Python calls PyUnicode_Compare directly, which raises this error
                    throw getRaise().raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANT_COMPARE, obj, arg);
                }
            } catch (CannotCastException e) {
                Object result = getCallMethodNode().executeCached(frame, obj, T_MATCH, arg);
                return getIsTrueNode().executeCached(frame, result);
            }
        }

        /**
         * On fast path. Never tries to import the warnings module.
         */
        private Object getWarningsAttr(VirtualFrame frame, TruffleString attr) {
            return getWarningsAttr(frame, attr, false, getLookupAttrNode(), getCallMethodNode(), getContext());
        }

        /**
         * Slow path. Sometimes may try to import the warnings module.
         */
        private static Object getWarningsAttr(PythonContext context, TruffleString attr, boolean tryImport) {
            return getWarningsAttr(null, attr, tryImport, PyObjectLookupAttr.getUncached(), PyObjectCallMethodObjArgs.getUncached(), context);
        }

        /**
         * Used on both fast and slow path.
         */
        private static Object getWarningsAttr(VirtualFrame frame, TruffleString attr, boolean tryImport,
                        PyObjectLookupAttr lookup, PyObjectCallMethodObjArgs callMethod, PythonContext context) {
            Object warningsModule;
            if (tryImport) {
                try {
                    warningsModule = tryImport();
                } catch (PException e) {
                    e.expect(null, PythonBuiltinClassType.ImportError, IsBuiltinObjectProfile.getUncached());
                    return null;
                }
            } else {
                Object sys = context.lookupBuiltinModule(T_SYS);
                Object modules = lookup.executeCached(frame, sys, T_MODULES);
                try {
                    warningsModule = callMethod.executeCached(frame, modules, T_GET, T_WARNINGS, PNone.NONE);
                } catch (PException e) {
                    return null;
                }
                if (warningsModule == PNone.NONE) {
                    return null;
                }
            }
            Object result = lookup.executeCached(frame, warningsModule, attr);
            if (result == PNone.NO_VALUE) {
                return null;
            } else {
                return result;
            }
        }

        /**
         * On slow path.
         */
        private static PDict getOnceRegistry(Node node, PythonContext context, PythonModule module) {
            Object registry = getWarningsAttr(context, T_ONCEREGISTRY, false);
            if (registry == null) {
                registry = getStateOnceRegistry(module);
            }
            if (!(registry instanceof PDict)) {
                throw PRaiseNode.raiseUncached(node, PythonBuiltinClassType.TypeError, ErrorMessages.WARN_ONCE_REG_MUST_BE_DICT, registry);
            }
            return (PDict) registry;
        }

        /**
         * On fast path.
         */
        private TruffleString getDefaultAction(VirtualFrame frame, PythonModule module) {
            Object defaultAction = getWarningsAttr(frame, T_DEFAULTACTION);
            if (defaultAction == null) {
                defaultAction = getStateDefaultAction(module);
            }
            try {
                return getCastStr().executeCached(defaultAction);
            } catch (CannotCastException e) {
                throw getRaise().raise(PythonBuiltinClassType.TypeError, ErrorMessages.WARN_DEF_ACTION_MUST_BE_STRING, defaultAction);
            }
        }

        /**
         * On fast path.
         */
        private TruffleString getFilter(VirtualFrame frame, PythonModule _warnings, Object category, Object text, int lineno, Object module, Object[] item) {
            Object filters = getWarningsAttr(frame, T_FILTERS);
            if (filters != null) {
                _warnings.getModuleState(ModuleState.class).filters = filters;
            } else {
                filters = getStateFilters(_warnings);
            }
            if (!(filters instanceof PList)) {
                throw getRaise().raise(PythonBuiltinClassType.ValueError, ErrorMessages.WARN_FILTERS_MUST_BE_LIST);
            }
            SequenceStorage filtersStorage = ((PList) filters).getSequenceStorage();
            SequenceStorageNodes.GetItemScalarNode sequenceGetItem = getSequenceGetItemNode();
            for (int i = 0; i < filtersStorage.length(); i++) {
                Object tmpItem = sequenceGetItem.executeCached(filtersStorage, i);
                if (!(tmpItem instanceof PTuple)) {
                    throw getRaise().raise(PythonBuiltinClassType.ValueError, ErrorMessages.WARN_FILTERS_IETM_ISNT_5TUPLE, i);
                }
                SequenceStorage tmpStorage = ((PTuple) tmpItem).getSequenceStorage();
                if (tmpStorage.length() != 5) {
                    throw getRaise().raise(PythonBuiltinClassType.ValueError, ErrorMessages.WARN_FILTERS_IETM_ISNT_5TUPLE, i);
                }

                Object actionObj = sequenceGetItem.executeCached(tmpStorage, 0);
                TruffleString action;
                try {
                    action = getCastStr().executeCached(actionObj);
                } catch (CannotCastException e) {
                    // CPython does this check after the other __getitem__ calls, but we know it's a
                    // tuple so...
                    throw getRaise().raise(PythonBuiltinClassType.TypeError, ErrorMessages.ACTION_MUST_BE_STRING, actionObj);
                }
                Object msg = sequenceGetItem.executeCached(tmpStorage, 1);
                Object cat = sequenceGetItem.executeCached(tmpStorage, 2);
                Object mod = sequenceGetItem.executeCached(tmpStorage, 3);
                Object lnObj = sequenceGetItem.executeCached(tmpStorage, 4);

                boolean goodMsg = checkMatched(frame, msg, text);
                boolean goodMod = checkMatched(frame, mod, module);
                boolean isSubclass = getIsSubClass().executeBoolean(frame, category, cat);
                int ln = getAsSizeNode().executeExactCached(frame, lnObj);
                if (goodMsg && isSubclass && goodMod && (ln == 0 || lineno == ln)) {
                    // if we're ignoring warnings, the first action will match all and the loop
                    // count would always be 1, so let's report here and hope that Graal will unroll
                    // it
                    com.oracle.truffle.api.nodes.LoopNode.reportLoopCount(this, i + 1);
                    item[0] = tmpItem;
                    return action;
                }
            }

            TruffleString action = getDefaultAction(frame, _warnings);
            item[0] = PNone.NONE;
            return action;
        }

        /**
         * The variant of alreadyWarned that should not set and that must be on the fast path.
         */
        private boolean alreadyWarnedShouldNotSet(VirtualFrame frame, PythonModule _warnings, PDict registry, Object key) {
            return alreadyWarned(frame, _warnings, registry, key, false, getEqNode(), getCallMethodNode(), getDictGetItemNode(), getSetItemNode(), getIsTrueNode());
        }

        /**
         * The variant of alreadyWarned that should set and that's on the slow path where the
         * warnings will be printed.
         */
        private static boolean alreadyWarnedShouldSet(PythonModule _warnings, PDict registry, Object key) {
            return alreadyWarned(null, _warnings, registry, key, true, PyObjectRichCompareBool.EqNode.getUncached(), PyObjectCallMethodObjArgs.getUncached(), PyDictGetItem.getUncached(),
                            PyObjectSetItem.getUncached(), PyObjectIsTrueNode.getUncached());
        }

        /**
         * Used on both fast and slow path.
         */
        private static boolean alreadyWarned(VirtualFrame frame, PythonModule _warnings, PDict registry, Object key, boolean shouldSet, PyObjectRichCompareBool.EqNode eqNode,
                        PyObjectCallMethodObjArgs callMethod, PyDictGetItem getItem, PyObjectSetItem setItem, PyObjectIsTrueNode isTrueNode) {
            Object versionObj = getItem.executeCached(frame, registry, T_VERSION);
            long stateFiltersVersion = getStateFiltersVersion(_warnings);
            if (versionObj == null || !eqNode.compareCached(frame, stateFiltersVersion, versionObj)) {
                callMethod.executeCached(frame, registry, T_CLEAR);
                setItem.executeCached(frame, registry, T_VERSION, stateFiltersVersion);
            } else {
                Object alreadyWarned = getItem.executeCached(frame, registry, key);
                if (alreadyWarned != null) {
                    return isTrueNode.executeCached(frame, alreadyWarned);
                }
            }
            if (shouldSet) {
                setItem.executeCached(frame, registry, key, true);
            }
            return false;
        }

        /**
         * On the fast path.
         */
        private TruffleString normalizeModule(TruffleString filename) {
            final int extLen = 3;
            assert extLen == T_PY_EXTENSION.codePointLengthUncached(TS_ENCODING);

            if (filename.isEmpty()) {
                return T_VALUE_UNKNOWN;
            }
            int length = getCodePointLengthNode().execute(filename, TS_ENCODING);
            if (length >= extLen && getRegionEqualNode().execute(filename, length - extLen, T_PY_EXTENSION, 0, extLen, TS_ENCODING)) {
                return getSubstringNode().execute(filename, 0, length - extLen, TS_ENCODING, false);
            } else {
                return filename;
            }
        }

        @TruffleBoundary
        private static boolean updateRegistry(PythonObjectSlowPathFactory factory, PythonModule _warnings, PDict registry, Object text, Object category, boolean addZero) {
            PTuple altKey;
            if (addZero) {
                altKey = factory.createTuple(new Object[]{text, category, 0});
            } else {
                altKey = factory.createTuple(new Object[]{text, category});
            }
            return alreadyWarnedShouldSet(_warnings, registry, altKey);
        }

        @TruffleBoundary
        private static void showWarning(Object filename, int lineno, Object text, Object category, TruffleString sourceline) {
            Object name;
            if (category instanceof PythonBuiltinClassType) {
                name = ((PythonBuiltinClassType) category).getName();
            } else {
                name = PyObjectLookupAttr.executeUncached(category, SpecialAttributeNames.T___NAME__);
            }
            Object stderr = PythonContext.get(null).getStderr();

            // tfel: I've inlined PyFile_WriteObject, which just calls the "write" method and
            // decides if we should use "repr" or "str" - in this case its always "str" for objects

            // Print "filename:lineno: category: text\n"
            PyObjectCallMethodObjArgs call = PyObjectCallMethodObjArgs.getUncached();
            PyObjectStrAsObjectNode str = PyObjectStrAsObjectNode.getUncached();
            call.execute(null, null, stderr, T_WRITE, str.execute(null, filename));
            call.execute(null, null, stderr, T_WRITE, T_COLON);
            call.execute(null, null, stderr, T_WRITE, TruffleString.fromLongUncached(lineno, TS_ENCODING, true));
            call.execute(null, null, stderr, T_WRITE, T_COLON);
            call.execute(null, null, stderr, T_WRITE, str.execute(null, name));
            call.execute(null, null, stderr, T_WRITE, T_COLON);
            call.execute(null, null, stderr, T_WRITE, T_SPACE);
            call.execute(null, null, stderr, T_WRITE, str.execute(null, text));
            call.execute(null, null, stderr, T_WRITE, T_NEWLINE);

            // Print " source_line\n"
            if (sourceline != null) {
                // CPython goes through the trouble of getting a substring of sourceline with
                // leading whitespace removed, but then ignores the substring and prints the full
                // sourceline anyway...
                call.execute(null, null, stderr, T_WRITE, sourceline);
                call.execute(null, null, stderr, T_WRITE, T_NEWLINE);
            } else {
                // TODO: _Py_DisplaySourceLine(f_stderr, filename, lineno, indent 2);
            }
        }

        @TruffleBoundary
        private static void callShowWarning(PythonContext context, Object category, Object text, Object message,
                        TruffleString filename, int lineno, TruffleString sourceline, Object sourceIn) {
            PRaiseNode raise = PRaiseNode.getUncached();

            Object showFn = getWarningsAttr(context, T__SHOWWARNMSG, sourceIn != null);
            if (showFn == null) {
                showWarning(filename, lineno, text, category, sourceline);
                return;
            }

            if (!PyCallableCheckNode.executeUncached(showFn)) {
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.WARN_MUST_BE_SET_CALLABLE);
            }

            Object warnmsgCls = getWarningsAttr(context, T_WARNING_MESSAGE, false);
            if (warnmsgCls == null) {
                throw raise.raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.UNABLE_GET_WARN_MSG);
            }
            Object source = sourceIn == null ? PNone.NONE : sourceIn;

            assert message != null && category != null && filename != null && source != null;
            assert message != PNone.NO_VALUE && category != PNone.NO_VALUE && source != PNone.NO_VALUE;
            Object msg = CallNode.getUncached().execute(warnmsgCls, message, category, filename, lineno, PNone.NONE, PNone.NONE, source);
            CallNode.getUncached().execute(showFn, msg);
        }

        /**
         * This is the main part that checks if the warning will be printed at all. We shouldn't put
         * it behind a boundary, and we should try to ensure it compiles away nicely when warnings
         * are set to ignore. On the fast path.
         */
        private void warnExplicit(VirtualFrame frame, PythonModule warnings,
                        Object categoryIn, Object messageIn, TruffleString filename, int lineno, Object moduleIn,
                        Object registryObj, PDict globals /* see comment in method */, Object source,
                        IndirectCallData indirectCallData) {
            // CPython passes the sourceline directly here where we pass the globals argument. If
            // it's not null, and we need the source line eventually, we will get it on the slow
            // path.
            Object module = moduleIn;
            Object category = categoryIn;
            Object message = messageIn;

            if (module == PNone.NONE) {
                return;
            }

            PDict registry;
            if (registryObj == null || registryObj == PNone.NONE) {
                registry = null;
            } else if (registryObj instanceof PDict) {
                registry = (PDict) registryObj;
            } else {
                throw getRaise().raise(PythonBuiltinClassType.TypeError, ErrorMessages.REGISTRY_MUST_BE_DICT);
            }

            if (module == null) {
                module = normalizeModule(filename);
            }

            // Python code uses PyObject_IsInstance but on the built-in Warning class, so we know
            // what __instancecheck__ does
            Object text;
            if (getIsSubClass().executeBoolean(frame, getPythonClass(message), PythonBuiltinClassType.Warning)) {
                text = getStrNode().executeCached(frame, message);
                category = getPythonClass(message);
            } else {
                text = message;
                message = getCallNode().execute(frame, category, message);
            }

            Object key = getFactory().createTuple(new Object[]{text, category, lineno});
            if (registry != null) {
                if (alreadyWarnedShouldNotSet(frame, warnings, registry, key)) {
                    return;
                }
            }
            // TODO: branch profile?

            Object[] item = new Object[1];
            TruffleString action = getFilter(frame, warnings, category, text, lineno, module, item);

            // CPython first checks for the "error" case, but since we want to optimize for ignored
            // warnings, we swap those checks
            if (getEqualNode().execute(T_IGNORE, action, TS_ENCODING)) {
                return;
            }

            // the rest of this function is behind a TruffleBoundary, since we don't care so much
            // about performance when warnings are enabled.
            Object state = IndirectCallContext.enter(frame, getLanguage(), getContext(), indirectCallData);
            try {
                warnExplicitPart2(PythonContext.get(this), this, warnings, filename, lineno, registry, globals, source, category, message, text, key, item[0], action);
            } finally {
                IndirectCallContext.exit(frame, getLanguage(), getContext(), state);
            }
        }

        @TruffleBoundary
        private static void warnExplicitPart2(PythonContext context, Node node, PythonModule warnings, TruffleString filename, int lineno, PDict registry, PDict globals, Object source,
                        Object category, Object message, Object text, Object key, Object item, TruffleString action) {

            if (action.equalsUncached(T_ERROR, TS_ENCODING)) {
                if (!PyExceptionInstanceCheckNode.executeUncached(message)) {
                    throw PRaiseNode.raiseUncached(node, PythonBuiltinClassType.SystemError, ErrorMessages.EXCEPTION_NOT_BASEEXCEPTION,
                                    PyObjectReprAsTruffleStringNode.executeUncached(message));
                } else {
                    throw PRaiseNode.raiseExceptionObject(node, message);
                }
            }

            if (!action.equalsUncached(T_ALWAYS, TS_ENCODING)) {
                if (registry != null) {
                    PyObjectSetItem.executeUncached(registry, key, true);
                }

                boolean alreadyWarned = false;
                if (action.equalsUncached(T_ONCE, TS_ENCODING)) {
                    if (registry == null) {
                        PDict currentRegistry = getOnceRegistry(node, context, warnings);
                        alreadyWarned = updateRegistry(context.factory(), warnings, currentRegistry, text, category, false);
                    } else {
                        alreadyWarned = updateRegistry(context.factory(), warnings, registry, text, category, false);
                    }
                } else if (action.equalsUncached(T_MODULE, TS_ENCODING)) {
                    if (registry != null) {
                        alreadyWarned = updateRegistry(context.factory(), warnings, registry, text, category, false);
                    }
                } else if (!action.equalsUncached(T_DEFAULT, TS_ENCODING)) {
                    throw PRaiseNode.raiseUncached(node, PythonBuiltinClassType.RuntimeError, ErrorMessages.UNRECOGNIZED_ACTION_IN_WARNINGS, action,
                                    PyObjectReprAsTruffleStringNode.executeUncached(item));
                }

                if (alreadyWarned) {
                    return;
                }
            }

            // CPython does this part eagerly in warn_explicit before ever getting here, but we try
            // to delay it
            TruffleString sourceline = null;
            if (globals != null) {
                sourceline = getSourceLine(node, globals, lineno);
            }

            callShowWarning(context, category, text, message, filename, lineno, sourceline, source);
        }

        /**
         * Used from doWarn. On the fast path.
         */
        private void setupContext(VirtualFrame frame, int stackLevel, TruffleString[] filename, int[] lineno, TruffleString[] module, Object[] registry) {
            // the stack level for the intrinsified version is off-by-one compared to the Python
            // version
            PFrame f = frame == null ? null : getCallerFrame(frame, stackLevel - 1);
            PDict globals;
            if (f == null || f.getGlobals() == null) {
                globals = getSysDict();
                filename[0] = T_SYS;
                lineno[0] = 1;
            } else {
                globals = getGlobalsDict(f.getGlobals());
                lineno[0] = f.getLine();
                RootCallTarget ct = f.getTarget();
                if (ct != null) {
                    filename[0] = PCode.extractFileName(ct.getRootNode());
                } else {
                    filename[0] = T_UNKNOWN_SOURCE;
                }
            }

            registry[0] = getDictGetItemNode().executeCached(frame, globals, T___WARNINGREGISTRY__);
            if (registry[0] == null) {
                registry[0] = getFactory().createDict();
                getSetItemNode().executeCached(frame, globals, T___WARNINGREGISTRY__, registry[0]);
            }
            Object moduleObj = getDictGetItemNode().executeCached(frame, globals, SpecialAttributeNames.T___NAME__);
            if (moduleObj == null) {
                module[0] = null;
            } else {
                try {
                    module[0] = getCastStr().executeCached(moduleObj);
                } catch (CannotCastException e) {
                    module[0] = T_STRING_SOURCE;
                }
            }
        }

        /**
         * Used from the "warn" function. On the fast path.
         */
        private Object getCategory(VirtualFrame frame, Object message, Object category) {
            Object messageType = getPythonClass(message);
            if (getIsSubClass().executeBoolean(frame, messageType, PythonBuiltinClassType.Warning)) {
                return messageType;
            } else if (category == null || category == PNone.NONE) {
                return PythonBuiltinClassType.UserWarning;
            } else if (!getIsTypeNode().executeCached(category) || !getIsSubClass().executeBoolean(frame, category, PythonBuiltinClassType.Warning)) {
                throw getRaise().raise(PythonBuiltinClassType.TypeError, ErrorMessages.CATEGORY_MUST_BE_WARN_SUBCLS, category);
            } else {
                return category;
            }
        }

        /**
         * Entry point for module functions. On the fast path.
         */
        private void doWarn(VirtualFrame frame, PythonModule warnings,
                        Object message, Object category, int stackLevel, Object source,
                        IndirectCallData indirectCallData) {
            TruffleString[] filename = new TruffleString[1];
            int[] lineno = new int[1];
            TruffleString[] module = new TruffleString[1];
            Object[] registry = new Object[1];
            setupContext(frame, stackLevel, filename, lineno, module, registry);
            warnExplicit(frame, warnings, category, message, filename[0], lineno[0], module[0], registry[0], null, source, indirectCallData);
        }

        /**
         * Slow path.
         */
        @TruffleBoundary
        private static TruffleString getSourceLine(Node node, PDict globals, int lineno) {
            Object loader = PyDictGetItem.executeUncached(globals, T___LOADER__);
            if (loader == null) {
                return null;
            }
            Object moduleName = PyDictGetItem.executeUncached(globals, T___NAME__);
            if (moduleName == null) {
                return null;
            }
            Object source;
            try {
                source = PyObjectCallMethodObjArgs.executeUncached(loader, T_GET_SOURCE, moduleName);
            } catch (PException e) {
                return null;
            }
            if (source == PNone.NONE) {
                return null;
            }
            String src;
            try {
                src = CastToJavaStringNode.getUncached().execute(source);
            } catch (CannotCastException e) {
                throw PRaiseNode.raiseUncached(node, PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_S_NOT_P, "str", source);
            }
            String[] lines = src.split("\n");
            if (lines.length >= lineno) {
                return toTruffleStringUncached(lines[lineno - 1]);
            } else {
                throw PRaiseNode.raiseUncached(node, PythonBuiltinClassType.IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS);
            }
        }
    }

    @ReportPolymorphism
    @NodeInfo(shortName = "warnings_warn_impl", description = "implements warnings_warn_impl and the clinic wrapper")
    @Builtin(name = J_WARN, minNumOfPositionalArgs = 2, parameterNames = {"$mod", "message", "category", "stacklevel", "source"}, declaresExplicitSelf = true, alwaysNeedsCallerFrame = true)
    @ArgumentClinic(name = "category", defaultValue = "PNone.NONE")
    @ArgumentClinic(name = "stacklevel", conversion = ClinicConversion.Int, defaultValue = "1")
    @ArgumentClinic(name = "source", defaultValue = "PNone.NONE")
    @GenerateNodeFactory
    public abstract static class WarnBuiltinNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return WarnBuiltinNodeClinicProviderGen.INSTANCE;
        }

        public abstract Object execute(VirtualFrame frame, PythonModule mod, Object message, Object category, int stacklevel, Object source);

        @Specialization
        Object doWarn(VirtualFrame frame, PythonModule mod, Object message, Object category, int stacklevel, Object source,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached WarningsModuleNode moduleFunctionsNode) {
            // warnings_warn_impl
            moduleFunctionsNode.doWarn(frame, mod, message, moduleFunctionsNode.getCategory(frame, message, category), stacklevel, source, indirectCallData);
            return PNone.NONE;
        }

        @NeverDefault
        public static WarnBuiltinNode create() {
            return WarnBuiltinNodeFactory.create(null);
        }

    }

    @ReportPolymorphism
    @NodeInfo(shortName = "warnings_warn_explicit")
    @Builtin(name = J_WARN_EXPLICIT, minNumOfPositionalArgs = 5, //
                    parameterNames = {"$mod", "message", "category", "filename", "lineno", "module", "registry", "module_globals", "source"}, declaresExplicitSelf = true)
    @ArgumentClinic(name = "lineno", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class WarnExplicitBuiltinNode extends PythonClinicBuiltinNode {
        @Specialization
        static Object doWarn(VirtualFrame frame, PythonModule mod, Object message, Object category, Object flname,
                        int lineno, Object module, Object registry, Object globals, Object source,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached WarningsModuleNode moduleFunctionsNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString filename;
            try {
                filename = castStr.execute(inliningTarget, flname);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "warn_explicit()", 3, "str", flname);
            }
            PDict globalsDict;
            if (globals instanceof PNone) {
                globalsDict = null;
            } else if (globals instanceof PDict) {
                globalsDict = (PDict) globals;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.MOD_GLOBALS_MUST_BE_DICT, globals);
            }
            // CPython calls get_source_line here. But since that's potentially slow, maybe we can
            // get away with doing that lazily
            moduleFunctionsNode.warnExplicit(frame, mod, category, message, filename, lineno,
                            module == PNone.NO_VALUE ? null : module,
                            registry == PNone.NO_VALUE ? null : registry,
                            globalsDict,
                            source == PNone.NO_VALUE ? null : source,
                            indirectCallData);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return WarningsModuleBuiltinsClinicProviders.WarnExplicitBuiltinNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "_filters_mutated", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class FiltersMutated extends PythonBuiltinNode {
        @Specialization
        static PNone mutate(PythonModule self) {
            ModuleState moduleState = self.getModuleState(ModuleState.class);
            moduleState.filtersVersion++;
            return PNone.NONE;
        }
    }

    /**
     * Our replacement for PyErr_WarnFormat, warn_unicode and related functions.
     */
    public abstract static class WarnNode extends Node {
        private static final WarnNode UNCACHED = new WarnNodeUncached();

        @NeverDefault
        public static WarnNode create() {
            return new WarnNodeCached();
        }

        public static WarnNode getUncached() {
            return UNCACHED;
        }

        public final void warnUnicode(Frame frame, Object category, TruffleString message, int stackLevel, Object source) {
            execute(frame, source, category == null ? PythonBuiltinClassType.RuntimeWarning : category, message, stackLevel);
        }

        public final void warnFormat(Frame frame, Object category, TruffleString message, Object... formatArgs) {
            warnFormat(frame, null, category, 1, message, formatArgs);
        }

        public final void warnFormat(Frame frame, Object source, Object category, int stackLevel, TruffleString message, Object... formatArgs) {
            execute(frame, source, category == null ? PythonBuiltinClassType.RuntimeWarning : category, message, stackLevel, formatArgs);
        }

        public final void resourceWarning(Frame frame, Object source, int stackLevel, TruffleString message, Object... formatArgs) {
            execute(frame, source, PythonBuiltinClassType.ResourceWarning, message, stackLevel, formatArgs);
        }

        public final void warnEx(Frame frame, Object category, TruffleString message, int stackLevel) {
            execute(frame, null, category == null ? PythonBuiltinClassType.RuntimeWarning : category, message, stackLevel);
        }

        public final void warn(Frame frame, Object category, TruffleString message) {
            warnEx(frame, category, message, 1);
        }

        protected abstract void execute(Frame frame, Object source, Object category, TruffleString format, int stackLevel, Object... formatArgs);

        private static final class WarnNodeCached extends WarnNode {
            @CompilationFinal BranchProfile noFrame = BranchProfile.create();
            @Child WarningsModuleNode moduleFunctionsNode;
            @Child TruffleString.FromJavaStringNode fromJavaStringNode;
            final IndirectCallData indirectCallData = IndirectCallData.createFor(this);

            @Override
            protected void execute(Frame frame, Object source, Object category, TruffleString format, int stackLevel, Object... formatArgs) {
                if (frame == null) {
                    noFrame.enter();
                    UNCACHED.execute(null, source, category, format, stackLevel, formatArgs);
                    return;
                }
                assert frame instanceof VirtualFrame;
                PythonModule _warnings = PythonContext.get(this).lookupBuiltinModule(T__WARNINGS);
                if (fromJavaStringNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    fromJavaStringNode = insert(TruffleString.FromJavaStringNode.create());
                }
                TruffleString message = fromJavaStringNode.execute(formatMessage(format, formatArgs), TS_ENCODING);
                if (moduleFunctionsNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    moduleFunctionsNode = insert(WarningsModuleNode.create());
                }
                moduleFunctionsNode.doWarn((VirtualFrame) frame, _warnings, message, category, stackLevel, source, indirectCallData);
            }

            /*
             * Unfortunately, this has to be done eagerly for now, because of the way that the
             * action filters filter by message text. So we cannot easily wait until we find "ah,
             * this warning will be ignored" and then format behind the TruffleBoundary at the end
             * of warnExplicit, since matching the filters needs the text. We could very carefully
             * delay this formatting if e.g. there's a catch-all ignore filter in the filters list,
             * but that's a bit involved and might not be worth it.
             */
            @TruffleBoundary
            private static String formatMessage(TruffleString format, Object... formatArgs) {
                String message;
                try {
                    message = ErrorMessageFormatter.format(format, formatArgs);
                } catch (IllegalFormatException e) {
                    throw CompilerDirectives.shouldNotReachHere("error while formatting \"" + format + "\"", e);
                }
                return message;
            }

        }

        private static final class WarnNodeUncached extends WarnNode {
            @Override
            public boolean isAdoptable() {
                return false;
            }

            @Override
            protected void execute(Frame frame, Object source, Object category, TruffleString format, int stackLevel, Object... formatArgs) {
                executeImpl(source, category, format, stackLevel, formatArgs);
            }

            @TruffleBoundary
            private void executeImpl(Object source, Object category, TruffleString format, int stackLevel, Object... formatArgs) {
                PythonModule _warnings = PythonContext.get(this).lookupBuiltinModule(T__WARNINGS);
                Object warn = ReadAttributeFromPythonObjectNode.executeUncached(_warnings, T_WARN, PNone.NONE);
                TruffleString message;
                try {
                    message = TruffleString.fromJavaStringUncached(ErrorMessageFormatter.format(format, formatArgs), TS_ENCODING);
                } catch (IllegalFormatException e) {
                    throw CompilerDirectives.shouldNotReachHere("error while formatting \"" + format + "\"", e);
                }
                CallNode.getUncached().execute(warn, message, category, stackLevel, source);
            }
        }
    }

    private static class ModuleState {
        long filtersVersion;
        Object filters;
        TruffleString defaultAction;
        PDict onceRegistry;
    }
}
