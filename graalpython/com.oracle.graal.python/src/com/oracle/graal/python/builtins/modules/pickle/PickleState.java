package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public class PickleState {
    // copyreg.dispatch_table, {type_object: pickling_function}
    Object dispatchTable;

    // For the extension opcodes EXT1, EXT2 and EXT4.
    // copyreg._extension_registry, {(module_name, function_name): code}
    Object extensionRegistry;
    // copyreg._extension_cache, {code: object}
    Object extensionCache;
    // copyreg._inverted_registry, {code: (module_name, function_name)}
    Object invertedRegistry;

    // codecs.encode, used for saving bytes in older protocols
    Object codecsEncode;
    // builtins.getattr, used for saving nested names with protocol < 4
    Object getattr;

    // functools.partial, used for implementing __newobj_ex__ with protocols 2 and 3
    Object partial;

    // Import mappings for compatibility with Python 2.x
    // * _compat_pickle.NAME_MAPPING, {(oldmodule, oldname): (newmodule, newname)}
    Object nameMapping2To3;
    // _compat_pickle.IMPORT_MAPPING, {oldmodule: newmodule}
    Object importMapping2To3;
    // Same, but with REVERSE_NAME_MAPPING / REVERSE_IMPORT_MAPPING
    Object nameMapping3To2;
    Object importMapping3To2;

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 36 -> 18
    public abstract static class PickleStateInitNode extends Node {

        public static final TruffleString T_GETATTR = tsLiteral("getattr");

        public abstract void execute(PickleState state);

        @Specialization
        void init(PickleState state,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyCallableCheckNode callableCheck) {
            PythonContext context = PythonContext.get(this);
            final PythonModule builtins = context.getBuiltins();
            state.getattr = getAttr.execute(null, inliningTarget, builtins, T_GETATTR);

            final Object copyreg = PickleUtils.importSimpleModule(PickleUtils.T_MOD_COPYREG);
            state.dispatchTable = getAttr.execute(null, inliningTarget, copyreg, PickleUtils.T_ATTR_DISPATCH_TABLE);
            if (!PGuards.isDict(state.dispatchTable)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "copyreg.dispatch_table", "dict", state.dispatchTable);
            }

            state.extensionRegistry = getAttr.execute(null, inliningTarget, copyreg, PickleUtils.T_ATTR_EXT_REGISTRY);
            if (!PGuards.isDict(state.extensionRegistry)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "copyreg._extension_registry", "dict", state.extensionRegistry);
            }

            state.invertedRegistry = getAttr.execute(null, inliningTarget, copyreg, PickleUtils.T_ATTR_INV_REGISTRY);
            if (!PGuards.isDict(state.invertedRegistry)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "copyreg._inverted_registry", "dict", state.invertedRegistry);
            }

            state.extensionCache = getAttr.execute(null, inliningTarget, copyreg, PickleUtils.T_ATTR_EXT_CACHE);
            if (!PGuards.isDict(state.extensionCache)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "copyreg._extension_cache", "dict", state.extensionCache);
            }

            final Object codecs = PickleUtils.importSimpleModule(PickleUtils.T_MOD_CODECS);
            state.codecsEncode = getAttr.execute(null, inliningTarget, codecs, PickleUtils.T_METHOD_ENCODE);
            if (!callableCheck.execute(inliningTarget, state.codecsEncode)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "codecs.encode", "callable", state.codecsEncode);
            }

            final Object functools = PickleUtils.importSimpleModule(PickleUtils.T_MOD_FUNCTOOLS);
            state.partial = getAttr.execute(null, inliningTarget, functools, PickleUtils.T_METHOD_PARTIAL);

            // Load the 2.x -> 3.x stdlib module mapping tables
            Object compatPickle = PickleUtils.importSimpleModule(PickleUtils.T_MOD_COMPAT_PICKLE);
            state.nameMapping2To3 = getAttr.execute(null, inliningTarget, compatPickle, PickleUtils.T_ATTR_NAME_MAPPING);
            if (!PGuards.isDict(state.nameMapping2To3)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, PickleUtils.T_CP_NAME_MAPPING, "dict", state.nameMapping2To3);
            }
            state.importMapping2To3 = getAttr.execute(null, inliningTarget, compatPickle, PickleUtils.T_ATTR_IMPORT_MAPPING);
            if (!PGuards.isDict(state.nameMapping2To3)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, PickleUtils.T_CP_IMPORT_MAPPING, "dict", state.importMapping2To3);
            }

            // ... and the 3.x -> 2.x mapping tables
            state.nameMapping3To2 = getAttr.execute(null, inliningTarget, compatPickle, PickleUtils.T_ATTR_REVERSE_NAME_MAPPING);
            if (!PGuards.isDict(state.nameMapping2To3)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, PickleUtils.T_CP_REVERSE_NAME_MAPPING, "dict",
                                state.nameMapping3To2);
            }
            state.importMapping3To2 = getAttr.execute(null, inliningTarget, compatPickle, PickleUtils.T_ATTR_REVERSE_IMPORT_MAPPING);
            if (!PGuards.isDict(state.nameMapping2To3)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, PickleUtils.T_CP_REVERSE_IMPORT_MAPPING, "dict",
                                state.importMapping3To2);
            }
        }
    }
}
