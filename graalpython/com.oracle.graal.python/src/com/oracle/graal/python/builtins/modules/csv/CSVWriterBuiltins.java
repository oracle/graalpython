package com.oracle.graal.python.builtins.modules.csv;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CSVWriter)
public class CSVWriterBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVWriterBuiltinsFactory.getFactories();
    }

    @Builtin(name = "writerow", parameterNames = {"$self", "seq"}, minNumOfPositionalArgs = 2,
            doc = WRITEROW_DOC)
    @GenerateNodeFactory
    public abstract static class WriteRowNode extends PythonBinaryBuiltinNode {
        @Specialization
       Object doIt(VirtualFrame frame, CSVWriter self, Object seq,
                   @Cached PyObjectGetIter getIter,
                   @Cached GetClassNode getClass,
                   @Cached IsBuiltinClassProfile errorProfile,
                   @Cached CallUnaryMethodNode callNode) {
           Object iter;

            try {
                iter = getIter.execute(frame, seq);
            } catch (PException e) {
                e.expect(PythonBuiltinClassType.TypeError, errorProfile);
                throw raise(PythonBuiltinClassType.CSVError, ErrorMessages.EXPECTED_ITERABLE_NOT_S, getClass.execute(seq));
            }

            // Join all fields of passed in sequence in internal buffer.
            PythonLanguage language = PythonLanguage.get(this);
            Object state = IndirectCallContext.enter(frame, language, getContext(), this);
            try {
                self.joinFields(iter);
            } finally {
                IndirectCallContext.exit(frame, language, getContext(), state);
            }

            return callNode.executeObject(frame, self.write, PythonUtils.sbToString(self.rec));
        }
    }

    @Builtin(name = "writerows", parameterNames = {"$self", "seqseq"}, minNumOfPositionalArgs = 2,
            doc = WRITEROWS_DOC)
    @GenerateNodeFactory
    public abstract static class WriteRowsNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doIt(VirtualFrame frame, CSVWriter self, Object seq,
                    @Cached PyObjectGetIter getIter,
                    @Cached GetNextNode getNext,
                    @Cached IsBuiltinClassProfile isBuiltinClassProfile,
                    @Cached WriteRowNode writeRow) {
            Object iter, row;

            iter = getIter.execute(frame, seq);

            while (true) {
                try {
                    row = getNext.execute(frame, iter);
                    writeRow.execute(frame, self, row);
                } catch (PException e){
                    e.expectStopIteration(isBuiltinClassProfile);
                    break;
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "dialect", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetDialectNode extends PythonUnaryBuiltinNode {
        @Specialization
        static CSVDialect doIt(CSVWriter self) {
            return self.dialect;
        }
    }

    private static final String WRITEROW_DOC =
        "writerow(iterable)\n" +
        "\n" +
        "Construct and write a CSV record from an iterable of fields.  Non-string\n" +
        "elements will be converted to string.";

    private static final String WRITEROWS_DOC =
        "writerows(iterable of iterables)\n" +
        "\n" +
        "Construct and write a series of iterables to a csv file.  Non-string\n" +
        "elements will be converted to string.";

}
