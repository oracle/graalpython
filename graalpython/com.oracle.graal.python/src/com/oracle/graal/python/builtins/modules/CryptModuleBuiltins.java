package com.oracle.graal.python.builtins.modules;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "_crypt")
public class CryptModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CryptModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "crypt", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"word", "salt"})
    @ArgumentClinic(name = "word", conversion = ArgumentClinic.ClinicConversion.String)
    @ArgumentClinic(name = "salt", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    static abstract class CryptNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object crypt(VirtualFrame frame, String word, String salt,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.crypt(getPosixSupport(), word, salt);
            } catch (PosixSupportLibrary.PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CryptModuleBuiltinsClinicProviders.CryptNodeClinicProviderGen.INSTANCE;
        }
    }
}
