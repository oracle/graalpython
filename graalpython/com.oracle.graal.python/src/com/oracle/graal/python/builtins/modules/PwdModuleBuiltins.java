/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.StringOrBytesToOpaquePathNode;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.UidConversionNode;
import com.oracle.graal.python.builtins.modules.PwdModuleBuiltinsClinicProviders.GetpwnamNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.PwdModuleBuiltinsFactory.GetpwnamNodeFactory;
import com.oracle.graal.python.builtins.modules.PwdModuleBuiltinsFactory.GetpwuidNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PwdResult;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "pwd")
public final class PwdModuleBuiltins extends PythonBuiltins {

    private static final TruffleString T_NOT_AVAILABLE = tsLiteral("NOT_AVAILABLE");
    static final StructSequence.BuiltinTypeDescriptor STRUCT_PASSWD_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PStructPasswd,
                    // @formatter:off The formatter joins these lines making it less readable
                    "pwd.struct_passwd: Results from getpw*() routines.\n\n" +
                    "This object may be accessed either as a tuple of\n" +
                    "  (pw_name,pw_passwd,pw_uid,pw_gid,pw_gecos,pw_dir,pw_shell)\n" +
                    "or via the object attributes as named in the above tuple.",
                    // @formatter:on
                    7,
                    new String[]{
                                    "pw_name", "pw_passwd", "pw_uid", "pw_gid", "pw_gecos", "pw_dir", "pw_shell",
                    },
                    new String[]{
                                    "user name", "password", "user id", "group id", "real name", "home directory", "shell program"
                    });

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        PosixSupportLibrary posixLib = PosixSupportLibrary.getUncached();
        boolean hasGetpwentries = posixLib.hasGetpwentries(PythonContext.get(null).getPosixSupport());
        if (hasGetpwentries) {
            return PwdModuleBuiltinsFactory.getFactories();
        } else {
            // Do not forget to add new builtins to this list if applicable
            assert PwdModuleBuiltinsFactory.getFactories().size() == 3;
            return Arrays.asList(GetpwuidNodeFactory.getInstance(), GetpwnamNodeFactory.getInstance());
        }
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        StructSequence.initType(core, STRUCT_PASSWD_DESC);
    }

    private static Object[] createPwuidObject(Node inliningTarget, PwdResult pwd, PythonObjectFactory factory, InlinedConditionProfile unsignedConversionProfile) {
        return new Object[]{
                        pwd.name,
                        T_NOT_AVAILABLE,
                        PInt.createPythonIntFromUnsignedLong(inliningTarget, factory, unsignedConversionProfile, pwd.uid),
                        PInt.createPythonIntFromUnsignedLong(inliningTarget, factory, unsignedConversionProfile, pwd.gid),
                        /* gecos: */ T_EMPTY_STRING,
                        pwd.dir,
                        pwd.shell
        };
    }

    @Builtin(name = "getpwuid", minNumOfPositionalArgs = 1, parameterNames = {"uid"})
    @GenerateNodeFactory
    public abstract static class GetpwuidNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGetpwuid(VirtualFrame frame, Object uidObj,
                        @Bind("this") Node inliningTarget,
                        @Cached UidConversionNode uidConversionNode,
                        @Cached IsBuiltinObjectProfile classProfile,
                        @Cached GilNode gil,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile unsignedConversionProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            long uid;
            try {
                uid = uidConversionNode.executeLong(frame, uidObj);
            } catch (PException ex) {
                if (classProfile.profileException(inliningTarget, ex, PythonBuiltinClassType.OverflowError)) {
                    throw raiseUidNotFound();
                }
                throw ex;
            }
            PwdResult pwd;
            try {
                gil.release(true);
                try {
                    pwd = posixLib.getpwuid(getPosixSupport(), uid);
                } finally {
                    gil.acquire(getContext());
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            if (pwd == null) {
                throw raiseUidNotFound();
            }
            return factory.createStructSeq(STRUCT_PASSWD_DESC, createPwuidObject(inliningTarget, pwd, factory, unsignedConversionProfile));
        }

        private PException raiseUidNotFound() {
            throw raise(PythonBuiltinClassType.KeyError, ErrorMessages.GETPWUID_NOT_FOUND);
        }
    }

    @Builtin(name = "getpwnam", minNumOfPositionalArgs = 1, parameterNames = {"name"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class GetpwnamNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return GetpwnamNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doGetpwname(VirtualFrame frame, TruffleString name,
                        @Bind("this") Node inliningTarget,
                        @Cached GilNode gil,
                        @Cached StringOrBytesToOpaquePathNode encodeFSDefault,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile unsignedConversionProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            // Note: CPython also takes only Strings, not bytes, and then encodes the String
            // StringOrBytesToOpaquePathNode already checks for embedded '\0'
            Object nameEncoded = encodeFSDefault.execute(inliningTarget, name);
            PwdResult pwd;
            try {
                gil.release(true);
                try {
                    pwd = posixLib.getpwnam(getPosixSupport(), nameEncoded);
                } finally {
                    gil.acquire(getContext());
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            if (pwd == null) {
                throw raise(PythonBuiltinClassType.KeyError, ErrorMessages.GETPWNAM_NAME_NOT_FOUND, name);
            }
            return factory.createStructSeq(STRUCT_PASSWD_DESC, createPwuidObject(inliningTarget, pwd, factory, unsignedConversionProfile));
        }
    }

    @Builtin(name = "getpwall")
    @GenerateNodeFactory
    public abstract static class GetpwallNode extends PythonBuiltinNode {
        @Specialization
        Object doGetpall(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile unsignedConversionProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            // We cannot release the GIL, because the underlying POSIX calls are not thread safe
            PwdResult[] entries;
            try {
                entries = posixLib.getpwentries(getPosixSupport());
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            Object[] result = new Object[entries.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = factory.createStructSeq(STRUCT_PASSWD_DESC, createPwuidObject(inliningTarget, entries[i], factory, unsignedConversionProfile));
            }
            return factory.createList(result);
        }
    }
}
