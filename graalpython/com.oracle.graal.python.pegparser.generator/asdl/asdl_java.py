# Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

"""Generate Java code from an ASDL description."""

from typing import Optional, Tuple

from asdl import java_file
from asdl import model


SST_PACKAGE = 'com.oracle.graal.python.pegparser.sst'
AST_PACKAGE = 'com.oracle.graal.python.builtins.modules.ast'


class Generator(model.Visitor):

    PACKAGE: str
    CLASS_NAME: str

    def __init__(self, out_dir_base: str):
        super().__init__()
        self._out_dir_base = out_dir_base

    def create_emitter(self, java_class_name: Optional[str] = None):
        return java_file.create(self._out_dir_base, self.PACKAGE, java_class_name or self.CLASS_NAME)


class SSTNodeGenerator(Generator):
    """A visitor that generates subclasses of SSTNode."""
    PACKAGE = SST_PACKAGE

    def visit_module(self, module: model.Module):
        for t in module.types:
            self.visit(t)

    def visit_concrete_class(self, node: model.ConcreteClass):
        with self.create_emitter(node.name.java) as emitter:
            emitter.println()
            emitter.println('import com.oracle.graal.python.pegparser.tokenizer.SourceRange;')
            self.generate_sst_node(emitter, node)

    def visit_abstract_class(self, node: model.AbstractClass):
        with self.create_emitter(node.name.java) as emitter:
            emitter.println()
            emitter.println('import com.oracle.graal.python.pegparser.tokenizer.SourceRange;')
            with emitter.define(f'public abstract class {node.name.java} extends SSTNode'):
                with emitter.define(f'{node.name.java}(SourceRange sourceRange)'):
                    emitter.println(f'super(sourceRange);')
                for t in node.inner_classes:
                    self.generate_sst_node(emitter, t)

    def visit_enum(self, node: model.Enum):
        with self.create_emitter(node.name.java) as emitter:
            with emitter.define(f'public enum {node.name.java}'):
                for n in node.members:
                    emitter.println(f'{n.java},')

    @staticmethod
    def generate_sst_node(emitter: java_file.Emitter, c: model.ConcreteClass):
        static = 'static ' if c.outer_name else ''
        super_class = c.outer_name.java if c.outer_name else 'SSTNode'
        with emitter.define(f'public {static}final class {c.name.java} extends {super_class}'):
            # fields
            for f in c.fields:
                comment = '   // nullable' if f.is_nullable else ''
                emitter.println(f'public final {f.type.java} {f.name.java};{comment}')
            # constructor
            ctor_args = ', '.join(f'{f.type.java} {f.name.java}' for f in c.fields)
            with emitter.define(f'public {c.name.java}({ctor_args}{", " if ctor_args else ""}SourceRange sourceRange)'):
                emitter.println(f'super(sourceRange);')
                for f in c.fields:
                    if not f.is_nullable and f.type.java not in ('int', 'boolean'):
                        emitter.println(f'assert {f.name.java} != null;')
                    emitter.println(f'this.{f.name.java} = {f.name.java};')
            # accept() method
            with emitter.define('public <T> T accept(SSTreeVisitor<T> visitor)', '@Override'):
                emitter.println('return visitor.visit(this);')


class SSTreeVisitorGenerator(Generator):
    """A visitor that generates the SSTreeVisitor interface."""

    PACKAGE = SST_PACKAGE
    CLASS_NAME = 'SSTreeVisitor'
    VISIT_SEQUENCE = """
                    default <U> U visitSequence(SSTNode[] sequence) {
                        if (sequence != null) {
                            for (SSTNode n : sequence) {
                                if (n != null) {
                                    n.accept(this);
                                }
                            }
                        }
                        return null;
                    }"""

    def visit_module(self, module: model.Module):
        with self.create_emitter() as emitter:
            with emitter.define(f'public interface {self.CLASS_NAME}<T>'):
                emitter.print_block(self.VISIT_SEQUENCE)
                for t in module.types:
                    self.visit(t, emitter)

    @staticmethod
    def visit_concrete_class(c: model.ConcreteClass, emitter: java_file.Emitter):
        emitter.println()
        emitter.println(f'T visit({c.name.java} node);')

    @staticmethod
    def visit_abstract_class(c: model.AbstractClass, emitter: java_file.Emitter):
        for t in c.inner_classes:
            emitter.println()
            emitter.println(f'T visit({c.name.java}.{t.name.java} node);')


class AstStateGenerator(Generator):
    """A visitor that generates the AstState class."""

    PACKAGE = AST_PACKAGE
    CLASS_NAME = 'AstState'

    def visit_module(self, module: model.Module):
        with self.create_emitter() as emitter:
            emitter.println()
            emitter.println('import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;')
            emitter.println('import static com.oracle.graal.python.util.PythonUtils.tsLiteral;')
            emitter.println()
            emitter.println('import com.oracle.graal.python.builtins.objects.object.PythonObject;')
            emitter.println('import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;')
            emitter.println('import com.oracle.graal.python.builtins.objects.type.PythonClass;')
            emitter.println('import com.oracle.truffle.api.strings.TruffleString;')
            with emitter.define(f'final class {self.CLASS_NAME}'):
                self.emit_fields(module, emitter)
                self.emit_constructor(module, emitter)
                self.emit_helpers(emitter)

    @staticmethod
    def emit_fields(module: model.Module, emitter: java_file.Emitter):
        # TruffleString literals
        emitter.println()
        for f in sorted(module.collect_field_names()):
            emitter.println(f'static final TruffleString {f.ts_literal} = tsLiteral("{f.python}");')
        for f in sorted(module.collect_class_names()):
            emitter.println(f'static final TruffleString {f.ts_literal} = tsLiteral("{f.python}");')
        # cls fields
        emitter.println()
        emitter.println('final PythonBuiltinClass clsAst;')
        for t in module.collect_types():
            emitter.println(f'final PythonClass {t.name.cls_field};')
        # singleton fields
        emitter.println()
        for n in module.collect_enum_constants():
            emitter.println(f'final PythonClass {n.cls_field};')
            emitter.println(f'final PythonObject {n.singleton_field};')

    def emit_constructor(self, module: model.Module, emitter: java_file.Emitter):
        with emitter.define(f'{self.CLASS_NAME}(AstTypeFactory factory, PythonBuiltinClass clsAst)'):
            emitter.println('this.clsAst = clsAst;')
            for t in module.types:
                self.visit(t, emitter)

    @staticmethod
    def emit_helpers(emitter: java_file.Emitter):
        with emitter.define('private static TruffleString ts(String s)'):
            emitter.println('return toTruffleStringUncached(s);')
        with emitter.define('private static TruffleString[] tsa(TruffleString... names)'):
            emitter.println('return names;')

    def visit_abstract_class(self, c: model.AbstractClass, emitter: java_file.Emitter):
        emitter.println()
        emitter.println(f'// {c.name.java}')
        self.emit_make_type(emitter, c.name, None, (), c.attributes, c.doc)
        for t in c.inner_classes:
            self.visit(t, emitter)

    def visit_concrete_class(self, c: model.ConcreteClass, emitter: java_file.Emitter):
        emitter.println()
        emitter.println(f'// {c.full_name}')
        self.emit_make_type(emitter, c.name, c.outer_name, c.fields, c.attributes, c.doc)

    def visit_enum(self, c: model.Enum, emitter: java_file.Emitter):
        emitter.println()
        emitter.println(f'// {c.name.java}')
        self.emit_make_type(emitter, c.name, None, (), (), c.doc)
        for m in c.members:
            emitter.println()
            emitter.println(f'// {c.name.java}.{m.java}')
            self.emit_make_type(emitter, m, c.name, (), (), m.python)
            emitter.println(f'{m.singleton_field} = factory.createSingleton({m.cls_field});')

    @staticmethod
    def emit_make_type(emitter: java_file.Emitter, name: model.Name, base_class: Optional[model.Name],
                       fields: Tuple[model.Field, ...], attributes: Tuple[model.Field, ...], doc: str):
        base = base_class.cls_field if base_class else 'clsAst'
        with emitter.start_call(f'{name.cls_field} = factory.makeType({name.ts_literal}, {base},'):
            f = ', '.join(f.name.ts_literal for f in fields)
            emitter.println(f'tsa({f}),')
            if attributes:
                a = ', '.join(a.name.ts_literal for a in attributes)
                emitter.println(f'tsa({a}),')
            else:
                emitter.println('null,')
            o = ', '.join(a.name.ts_literal for a in fields + attributes if a.is_optional)
            emitter.println(f'tsa({o}),')
            if '\n' in doc:
                emitter.print_block('ts("' + '\\n" +\n"'.join(doc.split('\n')) + '")')
            else:
                emitter.println(f'ts("{doc}")')


class Sst2ObjGenerator(Generator):
    """A visitor that generates the Sst2ObjVisitor class."""

    PACKAGE = AST_PACKAGE
    CLASS_NAME = 'Sst2ObjVisitor'

    def visit_module(self, module: model.Module):
        with self.create_emitter() as emitter:
            self.emit_imports(module, emitter)
            with emitter.define(f'final class {self.CLASS_NAME} extends Sst2ObjVisitorBase'):
                self.emit_fields(emitter)
                self.emit_constructor(emitter)
                for t in module.types:
                    self.visit(t, emitter)

    @staticmethod
    def emit_imports(module, emitter):
        emitter.println()
        emitter.println('import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;')
        emitter.println()
        emitter.println('import com.oracle.graal.python.builtins.objects.object.PythonObject;')
        top_level_class_names = [t.name.java for t in module.types]
        for n in top_level_class_names:
            emitter.println(f'import {SST_PACKAGE}.{n};')

    @staticmethod
    def emit_fields(emitter: java_file.Emitter):
        emitter.println()
        emitter.println(f'private final {AstStateGenerator.CLASS_NAME} state;')

    def emit_constructor(self, emitter: java_file.Emitter):
        with emitter.define(f'{self.CLASS_NAME}({AstStateGenerator.CLASS_NAME} state)'):
            emitter.println('this.state = state;')

    def visit_abstract_class(self, c: model.AbstractClass, emitter: java_file.Emitter):
        for t in c.inner_classes:
            self.visit(t, emitter)

    def visit_concrete_class(self, c: model.ConcreteClass, emitter: java_file.Emitter):
        with emitter.define(f'public Object visit({c.full_name} node)', '@Override'):
            emitter.println(f'PythonObject o = factory.createPythonObject(state.{c.name.cls_field});')
            for f in c.fields:
                self.visit(f, emitter)
            if c.outer_has_attributes or c.attributes:
                emitter.println(f'fillSourceRangeAttributes(o, node.getSourceRange());')
            emitter.println(f'return o;')

    @staticmethod
    def visit_enum(c: model.Enum, emitter: java_file.Emitter):
        annotations = []
        if c.name.java == 'CmpOpTy':
            annotations.append('@Override')
        with emitter.define(f'public Object visitNonNull({c.name.java} v)', *annotations):
            with emitter.start_block(f'switch (v)'):
                for m in c.members:
                    with emitter.start(f'case {m.java}:'):
                        emitter.println(f'return state.{m.singleton_field};')
                with emitter.start('default:'):
                    emitter.println(f'throw shouldNotReachHere();')

    @staticmethod
    def visit_field(f: model.Field, emitter: java_file.Emitter):
        if f.is_sequence:
            emitter.println(f'o.setAttribute({f.name.ts_literal_qn}, seq2List(node.{f.name.java}));')
        elif f.is_nullable:
            if f.type.python == 'string':
                emitter.println(f'o.setAttribute({f.name.ts_literal_qn}, visitNullableStringOrByteArray(node.{f.name.java}));')
            else:
                emitter.println(f'o.setAttribute({f.name.ts_literal_qn}, visitNullable(node.{f.name.java}));')
        else:
            if f.type.python == 'string':
                emitter.println(f'o.setAttribute({f.name.ts_literal_qn}, visitNonNullStringOrByteArray(node.{f.name.java}));')
            else:
                emitter.println(f'o.setAttribute({f.name.ts_literal_qn}, visitNonNull(node.{f.name.java}));')


class Obj2Sst2Generator(Generator):
    """A visitor that generates the Obj2Sst class."""

    PACKAGE = AST_PACKAGE
    CLASS_NAME = 'Obj2Sst'

    def visit_module(self, module: model.Module):
        with self.create_emitter() as emitter:
            self.emit_imports(module, emitter)
            with emitter.define(f'final class {self.CLASS_NAME} extends Obj2SstBase'):
                self.emit_constructor(emitter)
                for t in module.types:
                    self.visit(t, emitter)

    @staticmethod
    def emit_imports(module, emitter):
        emitter.println()
        emitter.println('import com.oracle.graal.python.builtins.objects.PNone;')
        emitter.println('import com.oracle.graal.python.pegparser.sst.ConstantValue;')
        top_level_class_names = [t.name.java for t in module.types]
        for n in top_level_class_names:
            emitter.println(f'import {SST_PACKAGE}.{n};')
        emitter.println('import com.oracle.graal.python.pegparser.tokenizer.SourceRange;')

    def emit_constructor(self, emitter: java_file.Emitter):
        with emitter.define(f'{self.CLASS_NAME}({AstStateGenerator.CLASS_NAME} state)'):
            emitter.println('super(state);')

    @staticmethod
    def visit_enum(c: model.Enum, emitter: java_file.Emitter):
        with emitter.define(f'{c.name.java} obj2{c.name.java}(Object obj)'):
            for m in c.members:
                with emitter.start_block(f'if (isInstanceOf(obj, state.{m.cls_field}))'):
                    emitter.println(f'return {c.name.java}.{m.java};')
            emitter.println(f'throw unexpectedNodeType({c.name.ts_literal_qn}, obj);')

    def visit_abstract_class(self, c: model.AbstractClass, emitter: java_file.Emitter):
        with emitter.define(f'{c.name.java} obj2{c.name.java}(Object obj)'):
            with emitter.start_block('if (obj == PNone.NONE)'):
                emitter.println('return null;')
            if c.attributes:
                self.emit_attributes(c.attributes, c.name, emitter)
                arg = ', sourceRange'
            else:
                arg = ''
            for t in c.inner_classes:
                with emitter.start_block(f'if (isInstanceOf(obj, state.{t.name.cls_field}))'):
                    emitter.println(f'return obj2{t.name.java}(obj{arg});')
            emitter.println(f'throw unexpectedNodeType({c.name.ts_literal_qn}, obj);')
        for t in c.inner_classes:
            self.visit_concrete_class(t, emitter)

    def visit_concrete_class(self, c: model.ConcreteClass, emitter: java_file.Emitter):
        arg = ', SourceRange sourceRange' if c.outer_has_attributes else ''
        with emitter.define(f'{c.full_name} obj2{c.name.java}(Object obj{arg})'):
            for f in c.fields:
                self.visit_field(f, c.name, emitter)
            fields = ', '.join(f.name.java for f in c.fields)
            if c.attributes:
                self.emit_attributes(c.attributes, c.name, emitter)
            elif not c.outer_has_attributes:
                emitter.println('SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;')
            if fields:
                fields += ', sourceRange'
            else:
                fields = 'sourceRange'
            emitter.println(f'return new {c.full_name}({fields});')

    def emit_attributes(self, attributes: Tuple[model.Field, ...], class_name: model.Name, emitter: java_file.Emitter):
        if attributes:
            for a in attributes:
                self.visit_field(a, class_name, emitter)
            names = ', '.join(a.name.java for a in attributes)
            emitter.println(f'SourceRange sourceRange = new SourceRange({names});')
        else:
            emitter.println(f'SourceRange sourceRange = SourceRange.ARTIFICIAL_RANGE;')

    @staticmethod
    def visit_field(f: model.Field, class_name: model.Name, emitter: java_file.Emitter):
        arguments = ['obj', f.name.ts_literal_qn, class_name.ts_literal_qn]
        if f.is_sequence:
            arguments.append(f'this::{f.convertor}')
            arguments.append(f'{f.type.java}::new')
            suffix = 'Sequence'
        elif f.type.java in ('int', 'boolean'):
            arguments.append('false' if f.is_optional else 'true')
            suffix = f.type.java.capitalize()
        else:
            arguments.append(f'this::{f.convertor}')
            arguments.append('false' if f.is_optional else 'true')
            suffix = ''
        emitter.println(f'{f.type.java} {f.name.java} = lookupAndConvert{suffix}({", ".join(arguments)});')


def generate(input_filename, sst_path, ast_path):
    module = model.load(input_filename)
    SSTNodeGenerator(sst_path).visit(module)
    SSTreeVisitorGenerator(sst_path).visit(module)
    AstStateGenerator(ast_path).visit(module)
    Sst2ObjGenerator(ast_path).visit(module)
    Obj2Sst2Generator(ast_path).visit(module)
