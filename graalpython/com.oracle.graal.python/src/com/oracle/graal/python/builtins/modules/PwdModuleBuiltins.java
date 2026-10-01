/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Builtin;
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
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.PosixSupportNodes;
import com.oracle.graal.python.nodes.util.PosixSupportNodes.DecodeFilesystemStringNode;
import com.oracle.graal.python.runtime.EmulatedPosixSupport.EmulatedPwdResult;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.NativePosixSupport.NativePwdResult;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupport.Buffer;
import com.oracle.graal.python.runtime.PosixSupport.PosixException;
import com.oracle.graal.python.runtime.PosixSupport.PwdResult;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "pwd")
public final class PwdModuleBuiltins extends PythonBuiltins {

    private static final TruffleString T_NOT_AVAILABLE = tsLiteral("NOT_AVAILABLE");
    static final StructSequence.BuiltinTypeDescriptor STRUCT_PASSWD_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PStructPasswd,
                    7,
                    new String[]{
                                    "pw_name", "pw_passwd", "pw_uid", "pw_gid", "pw_gecos", "pw_dir", "pw_shell",
                    },
                    new String[]{
                                    "user name", "password", "user id", "group id", "real name", "home directory", "shell program"
                    });

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        boolean hasGetpwentries = PythonContext.get(null).getPosixSupport().hasGetpwentries();
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

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PwdResultToStructSeqNode extends com.oracle.graal.python.nodes.PNodeWithContext {
        public abstract Object[] execute(Node inliningTarget, PosixSupport posixSupport, PwdResult pwd);

        @Specialization
        static Object[] doNative(Node inliningTarget, PosixSupport posixSupport, NativePwdResult pwd,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached InlinedConditionProfile unsignedConversionProfile,
                        @Cached DecodeFilesystemStringNode decodeNameNode,
                        @Cached DecodeFilesystemStringNode decodeDirNode,
                        @Cached DecodeFilesystemStringNode decodeShellNode) {
            return createObject(inliningTarget, language, unsignedConversionProfile,
                            decodeNameNode.execute(inliningTarget, posixSupport, pwd.name),
                            pwd.uid, pwd.gid,
                            decodeDirNode.execute(inliningTarget, posixSupport, pwd.dir),
                            decodeShellNode.execute(inliningTarget, posixSupport, pwd.shell));
        }

        @Specialization
        static Object[] doEmulated(@SuppressWarnings("unused") Node inliningTarget, @SuppressWarnings("unused") PosixSupport posixSupport, EmulatedPwdResult pwd,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached InlinedConditionProfile unsignedConversionProfile) {
            return createObject(inliningTarget, language, unsignedConversionProfile,
                            TruffleString.fromJavaStringUncached(pwd.name, com.oracle.graal.python.util.PythonUtils.TS_ENCODING),
                            pwd.uid, pwd.gid,
                            TruffleString.fromJavaStringUncached(pwd.dir, com.oracle.graal.python.util.PythonUtils.TS_ENCODING),
                            TruffleString.fromJavaStringUncached(pwd.shell, com.oracle.graal.python.util.PythonUtils.TS_ENCODING));
        }

        private static Object[] createObject(Node inliningTarget, PythonLanguage language, InlinedConditionProfile unsignedConversionProfile,
                        TruffleString name, long uid, long gid, TruffleString dir, TruffleString shell) {
            return new Object[]{
                            name,
                            T_NOT_AVAILABLE,
                            PInt.createPythonIntFromUnsignedLong(inliningTarget, language, unsignedConversionProfile, uid),
                            PInt.createPythonIntFromUnsignedLong(inliningTarget, language, unsignedConversionProfile, gid),
                            /* gecos: */ T_EMPTY_STRING,
                            dir,
                            shell
            };
        }
    }

    @Builtin(name = "getpwuid", minNumOfPositionalArgs = 1, parameterNames = {"uid"})
    @GenerateNodeFactory
    public abstract static class GetpwuidNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGetpwuid(VirtualFrame frame, Object uidObj,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached UidConversionNode uidConversionNode,
                        @Cached IsBuiltinObjectProfile classProfile,
                        @Cached GilNode gil,
                        @Cached PwdResultToStructSeqNode resultNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            long uid;
            try {
                uid = uidConversionNode.executeLong(frame, uidObj);
            } catch (PException ex) {
                if (classProfile.profileException(inliningTarget, ex, PythonBuiltinClassType.OverflowError)) {
                    throw raiseUidNotFound(inliningTarget, raiseNode);
                }
                throw ex;
            }
            PwdResult pwd;
            try {
                gil.release(true);
                try {
                    pwd = context.getPosixSupport().getpwuid(uid);
                } finally {
                    gil.acquire(context);
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            if (pwd == null) {
                throw raiseUidNotFound(inliningTarget, raiseNode);
            }
            PythonLanguage language = context.getLanguage(inliningTarget);
            return PFactory.createStructSeq(language, STRUCT_PASSWD_DESC, resultNode.execute(inliningTarget, context.getPosixSupport(), pwd));
        }

        private static PException raiseUidNotFound(Node inliningTarget, PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.KeyError, ErrorMessages.GETPWUID_NOT_FOUND);
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
        static Object doGetpwname(VirtualFrame frame, TruffleString name,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached GilNode gil,
                        @Cached StringOrBytesToOpaquePathNode encodeFSDefault,
                        @Cached PosixSupportNodes.GetPathAsBytesNode getPathAsBytesNode,
                        @Cached PwdResultToStructSeqNode resultNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            // Note: CPython also takes only Strings, not bytes, and then encodes the String
            // StringOrBytesToOpaquePathNode already checks for embedded '\0'
            Object pathEncoded = encodeFSDefault.execute(inliningTarget, name);
            Buffer nameBytes = getPathAsBytesNode.execute(inliningTarget, context.getPosixSupport(), pathEncoded);
            Object nameEncoded = context.getPosixSupport().createCStringFromBytes(nameBytes.data);
            PwdResult pwd;
            try {
                gil.release(true);
                try {
                    pwd = context.getPosixSupport().getpwnam(nameEncoded);
                } finally {
                    gil.acquire(context);
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            if (pwd == null) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.KeyError, ErrorMessages.GETPWNAM_NAME_NOT_FOUND, name);
            }
            return PFactory.createStructSeq(language, STRUCT_PASSWD_DESC, resultNode.execute(inliningTarget, context.getPosixSupport(), pwd));
        }
    }

    @Builtin(name = "getpwall")
    @GenerateNodeFactory
    public abstract static class GetpwallNode extends PythonBuiltinNode {
        @Specialization
        static Object doGetpall(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached PwdResultToStructSeqNode resultNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            // We cannot release the GIL, because the underlying POSIX calls are not thread safe
            PwdResult[] entries;
            try {
                entries = context.getPosixSupport().getpwentries();
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            PythonLanguage language = context.getLanguage(inliningTarget);
            Object[] result = new Object[entries.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = PFactory.createStructSeq(language, STRUCT_PASSWD_DESC,
                                resultNode.execute(inliningTarget, context.getPosixSupport(), entries[i]));
            }
            LoopNode.reportLoopCount(inliningTarget, result.length);
            return PFactory.createList(language, result);
        }
    }
}
