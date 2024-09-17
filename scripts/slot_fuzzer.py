# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

# Generates self-contained Python scripts that:
#
# 1) contain from 1 to 3 classes, each class is either pure Python or native heap type
# (the script itself takes care of compiling and loading the extension). Python class
# N inherits from class N-1.
#
# 2) invoke various Python operations on the last class in the hierarchy. The results are
# printed to the stdout. If there is an exception, only its type is printed to the stdout
#
# After each such Python script is generated it is executed on both CPython and GraalPy
# and the output is compared. If CPython segfaults, we ignore the test case.
#
# After the experiment, one can simply manually rerun the problematic test scripts.
import os.path
import sys
import dataclasses
import random
import itertools
import subprocess
import textwrap
from argparse import ArgumentParser
from collections import defaultdict

# language=python
SLOTS_TESTER = '''
import sys
import re
import traceback
import operator
def slots_tester(Klass, other_klasses):
    def test(fun, name):
        try:
            print(f'{name}:', end='')
            result = repr(fun())
            result = re.sub(r'object at 0x[0-9a-fA-F]+', '', result)
            print(result)
        except Exception as e:
            if '--verbose' in sys.argv or '-v' in sys.argv:
                traceback.print_exc()
            else:
                print(type(e))

    def test_dunder(obj, fun_name, *args):
        # avoid going through tp_getattr/o, which may be overridden to something funky
        args_str = ','.join([repr(x) for x in args])
        test(lambda: Klass.__dict__[fun_name](obj, *args), f"{fun_name} via class dict")
        test(lambda: getattr(obj, fun_name)(*args), f"{fun_name}")

    def write_attr(obj, attr, value):
        if attr == 'foo':
            obj.foo = value
        elif attr == 'bar':
            obj.bar = value

    obj = Klass()
    test(lambda: bool(obj), "bool(obj)")
    test(lambda: len(obj), "len(obj)")
    test_dunder(obj, '__bool__')
    test_dunder(obj, '__len__')
    test(lambda: obj.foo, "obj.foo")
    test(lambda: obj.bar, "obj.bar")

    test(lambda: write_attr(obj, 'foo', 42), "obj.foo = 42")
    test(lambda: obj.foo, "obj.foo")
    test(lambda: write_attr(obj, 'foo', 'hello'), "obj.foo = 'hello'")
    test(lambda: obj.foo, "obj.foo")

    test(lambda: write_attr(obj, 'bar', 42), "obj.bar = 42")
    test(lambda: obj.foo, "obj.foo")
    test(lambda: obj.bar, "obj.bar")

    test_dunder(obj, '__bool__')
    test_dunder(obj, '__len__')
    test_dunder(obj, '__getattr__', 'bar')
    test_dunder(obj, '__getattribute__', 'bar')
    test_dunder(obj, '__setattr__', 'foo', 11)
    test_dunder(obj, '__getattr__', 'foo')
    test_dunder(obj, '__delattr__', 'foo')
    test_dunder(obj, '__getattr__', 'foo')
    test(lambda: obj.foo, "obj.foo")

    class Dummy1:
        pass

    obj = Klass()
    owner = Dummy1()
    test_dunder(obj, '__get__', owner)
    test_dunder(obj, '__set__', owner, 42)
    test_dunder(obj, '__get__', owner)
    test_dunder(obj, '__delete__', owner)
    test_dunder(obj, '__get__', owner)

    class WithDescr:
        descr = Klass()
        def assign(self): self.descr = 42
        def del_descr(self): del self.descr

    obj = WithDescr()
    test(lambda: obj.descr, "obj.descr")
    test(lambda: obj.assign(), "obj.assign()")
    test(lambda: obj.descr, "obj.descr")
    test(lambda: obj.del_descr(), "obj.del_descr()")
    test(lambda: obj.descr, "obj.descr")

    obj = Klass()
    test(lambda: obj[42], "obj[42]")
    test(lambda: obj['hello'], "obj['hello']")
    test(lambda: obj[-1], "obj[-1]")
    test_dunder(obj, '__getitem__', 42)
    test_dunder(obj, '__getitem__', -1)
    test_dunder(obj, '__getitem__', 'hello')

    other_objs = [K() for K in other_klasses] + [42, 3.14, 'string', (1,2,3)]
    for obj2 in other_objs:
        obj1 = Klass()
        test(lambda: obj1 + obj2, f"{Klass} + {type(obj2)}")
        test(lambda: obj2 + obj1, f"{type(obj2)} + {Klass}")
        test(lambda: obj1 * obj2, f"{Klass} * {type(obj2)}")
        test(lambda: obj2 * obj1, f"{type(obj2)} * {Klass}")
        test(lambda: operator.concat(obj1, obj2), f"operator.concat({type(obj2)}, {Klass})")
        test(lambda: operator.mul(obj1, obj2), f"operator.mul({type(obj2)}, {Klass})")
        test_dunder(obj1, "__add__", obj2)
        test_dunder(obj1, "__radd__", obj2)
        test_dunder(obj1, "__mul__", obj2)

'''

# language=Python
EXT_COMPILER = '''
import sys
from pathlib import Path
import warnings
DIR = Path(__file__).parent.absolute()

with warnings.catch_warnings(action="ignore"):
    from distutils.core import setup, Extension

def compile_ext(name):
    source_file = name + '.c'
    verbosity = '--verbose' if '-v' in sys.argv else '--quiet'
    module = Extension(name, sources=[str(source_file)])
    args = [verbosity, 'build', 'install_lib', '-f', f'--install-dir={DIR}', 'clean']
    with warnings.catch_warnings(action="ignore"):
        setup(
            script_name='setup',
            script_args=args,
            name=name,
            version='1.0',
            description='',
            ext_modules=[module]
        )
'''


def write_all(filename, text):
    with open(filename, 'w+') as f:
        f.write(text)


@dataclasses.dataclass
class Slot:
    group: str | None
    name: str
    decl: str
    impls: list[str]

    def get_name(self, prefix) -> str:
        return prefix + '_' + self.name

    def impl(self, name_prefix, body):
        if body is None:
            return ''
        decl = self.decl.replace("$name$", self.get_name(name_prefix))
        if 'return' not in body:
            if 'Py_RETURN' in body:
                return f"{decl} {{ {body}; }}"
            else:
                return f"{decl} {{ return {body}; }}"
        else:
            b = '\n'.join(['  ' + line for line in body.split('\n')])
            return f"{decl} {{\n{b}\n}}"

    def tp_decl(self, name_prefix):
        return f"  {{ Py_{self.name}, {self.get_name(name_prefix)} }}"


NO_GROUP = 'top'

# language=C
C_SOURCE_HEADER = '''
#include <Python.h>

PyObject *global_stash1;
PyObject *global_stash2;
'''

SLOTS = [
    Slot('tp_as_number', 'nb_bool', 'int $name$(PyObject* self)', ['1', '0', None]),
    Slot('tp_as_number', 'nb_add', 'PyObject* $name$(PyObject* self, PyObject *other)', ['Py_NewRef(self)', 'PyLong_FromLong(0)', None]),
    Slot('tp_as_number', 'nb_multiply', 'PyObject* $name$(PyObject* self, PyObject *other)', ['Py_NewRef(self)', 'PyLong_FromLong(1)', None]),
    Slot('tp_as_sequence', 'sq_length', 'Py_ssize_t $name$(PyObject* self)', ['0', '1', '42', None]),
    Slot('tp_as_sequence', 'sq_item', 'PyObject* $name$(PyObject* self, Py_ssize_t index)', ['Py_NewRef(self)', 'PyLong_FromSsize_t(index + 1)', None]),
    Slot('tp_as_sequence', 'sq_concat', 'PyObject* $name$(PyObject* self, PyObject *other)', ['Py_NewRef(self)', 'PyLong_FromLong(10)', None]),
    Slot('tp_as_sequence', 'sq_repeat', 'PyObject* $name$(PyObject* self, Py_ssize_t count)', ['Py_NewRef(self)', 'PyLong_FromLong(count)', None]),
    Slot('tp_as_mapping', 'mp_length', 'Py_ssize_t $name$(PyObject* self)', ['0', '1', '42', None]),
    Slot('tp_as_mapping', 'mp_subscript', 'PyObject* $name$(PyObject* self, PyObject* key)', ['Py_RETURN_FALSE', 'Py_NewRef(key)', None]),
    Slot(NO_GROUP, 'tp_getattr', 'PyObject* $name$(PyObject* self, char *name)', ['Py_RETURN_NONE', 'Py_RETURN_FALSE', 'Py_NewRef(self)', None,
        '''
            if (global_stash1 == NULL) Py_RETURN_NONE;
            Py_IncRef(global_stash1);
            return global_stash1;
        ''']),
    Slot(NO_GROUP, 'tp_getattro', 'PyObject* $name$(PyObject* self, PyObject *name)', ['Py_RETURN_NONE', 'Py_RETURN_TRUE', 'Py_NewRef(self)', 'Py_NewRef(name)', None,
       '''
            if (global_stash1 == NULL) Py_RETURN_NONE;
            Py_IncRef(global_stash1);
            return global_stash1;
       ''']),
    Slot(NO_GROUP, 'tp_setattro', 'int $name$(PyObject* self, PyObject *name, PyObject *value)', ['0', None,
        '''
            Py_IncRef(value);
            Py_XDECREF(global_stash1);
            global_stash1 = value;
            return 0;
        ''']),
    # Disabled due to incompatibilities with Carlo Verre hack in some very specific corner cases
    # Slot(NO_GROUP, 'tp_setattr', 'int $name$(PyObject* self, char *name, PyObject *value)', ['0', None,
    #     '''
    #         Py_IncRef(value);
    #         Py_XDECREF(global_stash1);
    #         global_stash1 = value;
    #         return 0;
    #     ''']),
    Slot(NO_GROUP, 'tp_descr_get', 'PyObject* $name$(PyObject* self, PyObject* key, PyObject* type)', ['Py_RETURN_NONE', 'Py_NewRef(key)', None,
        '''
            if (global_stash2 == NULL) Py_RETURN_NONE;
            Py_IncRef(global_stash2);
            return global_stash2;
        ''']),
    Slot(NO_GROUP, 'tp_descr_set', 'int $name$(PyObject* self, PyObject* key, PyObject* value)', ['0', None,
        '''
            Py_IncRef(value);
            Py_XDECREF(global_stash2);
            global_stash2 = value;
            return 0;
        '''])
]

PY_GLOBALS = '''
global_dict1 = dict()
global_descr_val = None
'''

MAGIC = {
    '__bool__(self)': ['True', 'False', None],
    '__add__(self, other)': ['self', '"__add__result"', "NotImplemented", "str(other)", None],
    '__mul__(self, other)': ['self', '"__mul__result"', "NotImplemented", "repr(other)", None],
    '__radd__(self, other)': ['self', '"__radd__result"', "NotImplemented", "'radd' + str(other)", None],
    '__len__(self)': ['0', '1', '42', None],
    '__getattribute__(self,name)': ['name', '42', 'global_dict1[name]', None],
    '__getattr__(self,name)': ['name+"abc"', 'False', 'global_dict1[name]', None],
    '__get__(self,obj,objtype=None)': ['obj', 'True', 'global_descr_val', None],
    '__set__(self,obj,value)': [None,
                                '''
                                global global_descr_val
                                global_descr_val = value
                                return None
                                '''],
    '__setattr__(self,name,value)': [None,
                                    '''
                                    global global_dict1 # not using self._dict, because attr lookup can be something funky...
                                    global_dict1[name] = value
                                    return None
                                    '''],
    '__delattr__(self,name,value)': [None,
                                     '''
                                     global global_dict1
                                     del global_dict1[name]
                                     return None
                                     '''],
    '__getitem__(self, index)': [None, 'True', 'repr(index)']
}


def all_magic_impls(key):
    for body in MAGIC[key]:
        yield magic_impl(key, body)


def magic_impl(magic_key, magic_body):
    if magic_body is None:
        return ''
    if 'return' not in magic_body:
        return f" def {magic_key}: return {magic_body}"
    else:
        b = '\n'.join(['    ' + line for line in magic_body.split('\n')])
        return f" def {magic_key}:\n{b}"


# One of the combinations is going to be all empty implementations
magic_combinations = [x for x in itertools.product(*[all_magic_impls(key) for key in MAGIC.keys()])]


def managed_class_impl(name, bases, magic_impls):
    bases_tuple = '' if not bases else '(' + ','.join(bases) + ')'
    code = f"class {name}{bases_tuple}:\n"
    impls = [x for x in magic_impls if x]
    if impls:
        code += '\n'.join(impls)
    else:
        code += '  pass'
    return code


def all_slot_impls(slot:Slot):
    for body in slot.impls:
        yield (slot, body)


def native_heap_type_impl(name, mod_name, slots):
    slot_defs = '\n'.join([s.impl(name, body) for (s, body) in slots if body])
    slot_decls = ',\n'.join([s.tp_decl(name) for (s, body) in slots if body] + ["{0}"])
    return textwrap.dedent(f'''
        {slot_defs}
        PyType_Slot {name}_slots[] = {{
            {slot_decls}
        }};
        PyType_Spec {name}_spec = {{ "{mod_name}.{name}", sizeof(PyHeapTypeObject), 0, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, {name}_slots }};
        static PyObject* create_{name}(PyObject* module, PyObject* args) {{
            PyObject* bases;
            if (!PyArg_ParseTuple(args, "O", &bases))
                return NULL;
            PyObject* result = PyType_FromModuleAndSpec(module, &{name}_spec, bases);
            Py_XINCREF(result);
            return result;
        }}
    ''')


SLOT_GROUP_TYPES = {
    'tp_as_number': 'PyNumberMethods',
    'tp_as_sequence': 'PySequenceMethods',
    'tp_as_mapping': 'PyMappingMethods',
}


def native_static_type_impl(name, mod_name, slots):
    slot_defs = '\n'.join([s.impl(name, body) for (s, body) in slots if body])
    groups = defaultdict(lambda: list())
    for (s, body) in slots:
        if body:
            groups[s.group].append(s)

    slots_decl = ''
    slots_groups_decl = ''
    for group in groups.keys():
        group_decls = ''
        if group != NO_GROUP:
            slots_decl += f'.{group} = &{name}_{group},\n'
            group_decls = f'{SLOT_GROUP_TYPES[group]} {name}_{group} = {{\n'

        for s in groups[group]:
            group_decls += f'.{s.name} = &{s.get_name(name)},\n'

        if group != NO_GROUP:
            group_decls += '};\n'
            slots_groups_decl += group_decls
        else:
            slots_decl += group_decls

    slots_decl = textwrap.indent(slots_decl, '        ')
    slots_groups_decl = textwrap.indent(slots_groups_decl, '        ')

    return textwrap.dedent(f'''
        {slot_defs}

        {slots_groups_decl}

        static PyTypeObject CustomType_{name} = {{
            .ob_base = PyVarObject_HEAD_INIT(NULL, 0)
            .tp_name = "{mod_name}.{name}",
            .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
            .tp_new = PyType_GenericNew,
            {slots_decl}
        }};

        static PyObject* create_{name}(PyObject* module, PyObject* args) {{
            if (PyType_Ready(&CustomType_{name}) < 0)
                return NULL;
            Py_INCREF(&CustomType_{name});
            return (PyObject*) &CustomType_{name};
        }}
    ''')


slot_combinations = [x for x in itertools.product(*[all_slot_impls(slot) for slot in SLOTS])]

parser = ArgumentParser()
parser.add_argument('--graalpy', dest='graalpy', required=True)
parser.add_argument('--cpython', dest='cpython', required=True)
parser.add_argument('--iterations', type=int, default=200)
parser.add_argument('--output-dir', dest='output_dir', required=True,
                    help='where to store generated test cases and logs from test runs')
parser.add_argument('--seed', type=int, default=0)
args, _ = parser.parse_known_args()

graalpy = args.graalpy
cpython = args.cpython
output_dir = args.output_dir

if os.path.exists(output_dir):
    if not os.path.isdir(output_dir):
        print(f"Error: output directory is existing file")
        sys.exit(1)
    elif os.listdir(output_dir):
        print(f"Error: output directory is not empty")
        sys.exit(1)
else:
    os.mkdir(output_dir)


log_file = open(os.path.join(output_dir, 'log'), 'w+')


def log(*args, **kwargs):
    print(*args, **kwargs)
    print(*args, **kwargs, file=log_file)


seed = args.seed if args.seed != 0 else random.randrange(sys.maxsize)
log(f"Using seed: {seed}")
log(f"Output directory: {output_dir}")
log(f"Iterations: {args.iterations}")
log()
log()
rand = random.Random(seed)


def choose_random(l):
    index = rand.randint(0, len(l)-1)
    return l[index]


for test_case_idx in range(args.iterations):
    classes_count = max(3, rand.randint(1, 5))  # Make it more likely that it's 3...
    classes = []
    test_module_name = f"test{test_case_idx}"
    c_source = C_SOURCE_HEADER
    py_source = SLOTS_TESTER + PY_GLOBALS
    native_classes = []
    for i in range(classes_count):
        base = choose_random(classes) if classes else 'object'
        if rand.randint(0, 1) == 1:
            class_name = 'Native' + str(i)
            native_classes.append(class_name)
            if i == 0 and rand.randint(0, 2) < 2:
                c_source += native_static_type_impl(class_name, test_module_name, choose_random(slot_combinations))
            else:
                c_source += native_heap_type_impl(class_name, test_module_name, choose_random(slot_combinations))
            c_source += '\n'
            py_source += f"{class_name} = {test_module_name}.create_{class_name}(({base}, ))\n"
        else:
            class_name = 'Managed' + str(i)
            py_source += managed_class_impl(class_name, (base,), choose_random(magic_combinations))
            py_source += '\n'

        classes.append(class_name)

    if native_classes:
        compile_ext = EXT_COMPILER
        compile_ext += f"compile_ext('{test_module_name}')\n\n"
        py_source = compile_ext + f"import {test_module_name}\n\n" + py_source

        c_source += "static struct PyMethodDef test_module_methods[] = {\n"
        for clazz in native_classes:
            c_source += f'  {{"create_{clazz}", (PyCFunction)create_{clazz}, METH_VARARGS, ""}},\n'
        c_source += "  {NULL, NULL, 0, NULL}\n"
        c_source += "};"

        c_source += textwrap.dedent(f'''
            static PyModuleDef test_module = {{
                PyModuleDef_HEAD_INIT,
                "{test_module_name}",
                "",
                -1,
                test_module_methods,
                NULL, NULL, NULL, NULL
            }};

            PyMODINIT_FUNC
            PyInit_{test_module_name}(void) {{
                return PyModule_Create(&test_module);
            }}
        ''')

        c_filename = f"{test_module_name}.c"
        write_all(os.path.join(output_dir, c_filename), c_source)

    py_source += '\n\n# ===========\n'
    py_source += '# Tests:\n\n'
    py_source += 'all_classes = ' + ','.join(classes) + '\n\n'
    for klass in classes:
        py_source += f'print("\\n\\n\\nTesting {klass}\\n")\n'
        py_source += f'slots_tester({klass}, all_classes)\n'

    py_filename = f"test{test_case_idx}.py"
    write_all(os.path.join(output_dir, py_filename), py_source)

    log(f"Test case {test_case_idx:5}: ", end='')
    try:
        cpython_out = subprocess.check_output([cpython, py_filename], stderr=subprocess.STDOUT, cwd=output_dir).decode()
        write_all(os.path.join(output_dir, f"cpython{test_case_idx}.out"), cpython_out)
    except subprocess.CalledProcessError as e:
        output = e.output.decode()
        log("CPython error;     ⚠️")
        write_all(os.path.join(output_dir, f"cpython{test_case_idx}.out"), output)
        continue

    log("CPython succeeded; ", end='')
    try:
        graalpy_out = subprocess.check_output([graalpy, '--vm.ea', '--vm.esa', py_filename], stderr=subprocess.STDOUT, cwd=output_dir).decode()
        write_all(os.path.join(output_dir, f"graalpy{test_case_idx}.out"), graalpy_out)
    except subprocess.CalledProcessError as e:
        output = e.output.decode()
        log("❌ fatal error in GraalPy")
        write_all(os.path.join(output_dir, f"graalpy{test_case_idx}.out"), output)
        continue

    if cpython_out != graalpy_out:
        log(f"❌ output does not match!")
    else:
        log("✅ GraalPy succeeded")
