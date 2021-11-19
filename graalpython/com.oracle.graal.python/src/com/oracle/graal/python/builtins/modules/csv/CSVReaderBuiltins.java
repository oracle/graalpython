package com.oracle.graal.python.builtins.modules.csv;


import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.ArrayList;
import java.util.List;

import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.IN_QUOTED_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.START_RECORD;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CSVReader)
public class CSVReaderBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVReaderBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterReaderNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object iter(CSVReader self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextReaderNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object nextPos(VirtualFrame frame, CSVReader self,
                       @Cached GetNextNode nextNode,
                       @Cached CastToJavaStringNode castToJavaStringNode,
                       @Cached PythonObjectFactory factory,
                       @Cached GetClassNode getClassNode) {

            self.parseReset(); // TODO: Error checks needed?

            PythonLanguage language = PythonLanguage.get(this);
            Object state = IndirectCallContext.enter(frame, language, getContext(), this);

            try {

                do {
                    Object lineObj;
                    try {
                        lineObj = nextNode.execute(frame, self.inputIter);
                    } catch (PException e) {
                        e.expectStopIteration(IsBuiltinClassProfile.getUncached());

                        if ( self.field.length() != 0 || self.state == IN_QUOTED_FIELD) {
                            if (self.dialect.strict) {
                                throw raise(PythonBuiltinClassType.CSVError, ErrorMessages.UNEXPECTED_END_OF_DATA);
                            } else {
                                try {
                                    self.parseSaveField();
                                } catch (AbstractTruffleException ignored) {
                                    throw e.getExceptionForReraise();
                                }
                                break;
                            }
                        }
                        throw raise(PythonBuiltinClassType.StopIteration);
                    }


                    String line;
                    try {
                        line = castToJavaStringNode.execute(lineObj);
                    } catch (CannotCastException e) {
                        throw raise(PythonBuiltinClassType.CSVError, ErrorMessages.WRONG_ITERATOR_RETURN_TYPE, getClassNode.execute(lineObj));
                    }


                    //TODO: Implement PyUnicode_Check Node? => how do we handle the possibility of bytes?
                    // PyPy: if isinstance(line, str) and '\0' in line or isinstance(line, bytes) and line.index(0) >=0:
                    //                    raise Error("line contains NULL byte")
                    if (line.contains("\u0000")) {
                        throw raise(PythonBuiltinClassType.CSVError, ErrorMessages.LINE_CONTAINS_NULL_BYTE);
                    }

                    self.lineNum++;
                    self.parseLine(line);

                } while (self.state != START_RECORD);

                ArrayList<Object> fields = self.fields;
                self.fields = null;

                return factory.createList(fields.toArray());

            } finally {
                IndirectCallContext.exit(frame, language, getContext(), state);
            }
        }
    }

    @Builtin(name = "dialect", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetDialectNode extends PythonUnaryBuiltinNode {
        @Specialization
        static CSVDialect doIt(CSVReader self) {
            return self.dialect;
        }
    }

    @Builtin(name = "line_num", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetLineNumNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int doIt(CSVReader self) {
            return self.lineNum;
        }
    }
}
