package com.oracle.graal.python.builtins.modules.csv;

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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.List;

import static com.oracle.graal.python.builtins.modules.csv.CSVDialectBuiltins.QUOTE_NONE;

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
                   @Cached GetNextNode getNext,
                   @Cached GetClassNode getClass,
                   @Cached CallUnaryMethodNode callNode) {
            Object iter, field;

            try {
                iter = getIter.execute(frame, seq);
            } catch (PException e) { //TODO: should be TypeError?
                throw raise(PythonBuiltinClassType.CSVError, ErrorMessages.EXPECTED_ITERABLE_NOT_S, getClass.execute(seq));
            }

            // Join all fields of passed in sequence in internal buffer.
            self.joinReset();

            PythonLanguage language = PythonLanguage.get(this);
            Object state = IndirectCallContext.enter(frame, language, getContext(), this);
            try {
                while (true) {
                    try {
                        field = getNext.execute(frame, iter);
                        self.joinField(field);
                    } catch (PException e) {
                        e.expectStopIteration(IsBuiltinClassProfile.getUncached());
                        break;
                    }
                }
            } finally {
                IndirectCallContext.exit(frame, language, getContext(), state);
            }

            if (self.numFields > 0 && self.rec.length() == 0) {
                if (self.dialect.quoting == QUOTE_NONE) {
                    throw raise(PythonBuiltinClassType.CSVError, ErrorMessages.EMPTY_FIELD_RECORD_MUST_BE_QUOTED);
                }
                self.numFields--;
                self.joinAppend(null, true);
            }

            self.joinAppendLineterminator();

            return callNode.executeObject(frame, self.write, self.rec.toString());
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
