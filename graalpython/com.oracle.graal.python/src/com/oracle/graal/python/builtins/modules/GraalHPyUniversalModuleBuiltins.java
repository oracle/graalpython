/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ALL__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.GraalHPyUniversalModuleBuiltinsClinicProviders.HPyUniversalLoadBootstrapNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.GraalHPyUniversalModuleBuiltinsClinicProviders.HPyUniversalLoadNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyMode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = GraalHPyUniversalModuleBuiltins.J_HPY_UNIVERSAL)
@GenerateNodeFactory
public final class GraalHPyUniversalModuleBuiltins extends PythonBuiltins {

    static final String J_HPY_UNIVERSAL = "_hpy_universal";
    private static final TruffleString T_HPY_UNIVERSAL = tsLiteral(J_HPY_UNIVERSAL);
    private static final TruffleString T_HPY = tsLiteral("HPY");

    private static final TruffleString[] ALL_ARRAY;

    static {
        List<TruffleString> allList = new ArrayList<>();
        for (HPyMode mode : HPyMode.values()) {
            allList.add(tsLiteral(mode.name()));
        }
        allList.add(tsLiteral("load"));
        allList.add(tsLiteral("_load_bootstrap"));
        ALL_ARRAY = allList.toArray(new TruffleString[0]);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GraalHPyUniversalModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        for (HPyMode mode : HPyMode.values()) {
            addBuiltinConstant(mode.name(), mode.getValue());
        }
        super.initialize(core);
    }

    @Override
    public void postInitialize(Python3Core core) {
        PythonModule module = core.lookupBuiltinModule(T_HPY_UNIVERSAL);
        module.setAttribute(T___ALL__, core.factory().createTuple(ALL_ARRAY));
    }

    @Builtin(name = "load", parameterNames = {"name", "path", "spec", "debug", "mode"}, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "path", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "debug", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "-1")
    abstract static class HPyUniversalLoadNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return HPyUniversalLoadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object doGeneric(VirtualFrame frame, TruffleString name, TruffleString path, Object spec, boolean debug, int mode,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                HPyMode hmode = debug ? HPyMode.MODE_DEBUG : HPyMode.MODE_UNIVERSAL;
                // 'mode' just overwrites 'debug'
                if (mode > 0) {
                    hmode = HPyMode.fromValue(mode);
                }
                return GraalHPyContext.loadHPyModule(inliningTarget, context, name, path, spec, hmode);
            } catch (ApiInitException ie) {
                throw ie.reraise(frame, inliningTarget, constructAndRaiseNode);
            } catch (ImportException ie) {
                throw ie.reraise(frame, inliningTarget, constructAndRaiseNode);
            } catch (IOException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, e, eqNode);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }
    }

    @Builtin(name = "_load_bootstrap", parameterNames = {"name", "ext_name", "package", "file", "loader", "spec", "env"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "ext_name", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "file", conversion = ClinicConversion.TString)
    abstract static class HPyUniversalLoadBootstrapNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return HPyUniversalLoadBootstrapNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object doGeneric(VirtualFrame frame, TruffleString name, TruffleString extName, Object pkg, TruffleString file, Object loader, Object spec, Object env,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object module;

            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                HPyMode hmode = getHPyModeFromEnviron(name, env);
                module = GraalHPyContext.loadHPyModule(inliningTarget, context, name, file, spec, hmode);
            } catch (CannotCastException e) {
                // thrown by getHPyModeFromEnviron if value is not a string
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.HPY_MODE_VALUE_MUST_BE_STRING);
            } catch (ApiInitException ie) {
                throw ie.reraise(frame, inliningTarget, constructAndRaiseNode);
            } catch (ImportException ie) {
                throw ie.reraise(frame, inliningTarget, constructAndRaiseNode);
            } catch (IOException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, e, eqNode);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }

            writeAttrNode.execute(module, SpecialAttributeNames.T___FILE__, file);
            writeAttrNode.execute(module, SpecialAttributeNames.T___LOADER__, loader);
            writeAttrNode.execute(module, SpecialAttributeNames.T___NAME__, extName);
            writeAttrNode.execute(module, SpecialAttributeNames.T___PACKAGE__, pkg);
            writeAttrNode.execute(spec, ImpModuleBuiltins.T_ORIGIN, file);
            writeAttrNode.execute(module, SpecialAttributeNames.T___SPEC__, spec);
            return module;
        }

        /**
         * <pre>
         *     HPY_MODE := MODE | (MODULE_NAME ':' MODE { ',' MODULE_NAME ':' MODE })
         *     MODULE_NAME :=
         *     IDENTIFIER MODE := 'debug' | 'trace' | 'universal'
         * </pre>
         */
        @TruffleBoundary
        private static HPyMode getHPyModeFromEnviron(TruffleString moduleName, Object env) throws CannotCastException {
            Object result;
            try {
                result = PyObjectGetItem.executeUncached(env, T_HPY);
            } catch (PException e) {
                e.expect(null, PythonBuiltinClassType.KeyError, IsBuiltinObjectProfile.getUncached());
                // this is not an error; it just means that the key was not present in 'env'
                return HPyMode.MODE_UNIVERSAL;
            }

            String s = CastToJavaStringNode.getUncached().execute(result);

            int colonIdx = s.indexOf(':');
            if (colonIdx != -1) {
                // case 2: modes are specified per module
                String[] moduleParts = s.split(",");
                String sModuleName = moduleName.toJavaStringUncached();
                for (String modulePars : moduleParts) {
                    String[] def = modulePars.split(":");
                    if (sModuleName.equals(def[0])) {
                        return HPyMode.valueOf("MODE_" + def[1].toUpperCase());
                    }
                }
            } else {
                // case 1: mode was globally specified
                return HPyMode.valueOf("MODE_" + s.toUpperCase());
            }
            return HPyMode.MODE_UNIVERSAL;
        }
    }
}
