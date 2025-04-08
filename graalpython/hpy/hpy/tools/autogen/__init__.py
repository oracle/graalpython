import pycparser
from packaging import version

if version.parse(pycparser.__version__) < version.parse('2.21'):
    raise ImportError('You need pycparser>=2.21 to run autogen')

from .parse import HPyAPI, AUTOGEN_H


def generate(generators, *gen_args):
    """
    Takes a sequence of autogen generators that will have access to the parse
    tree of 'public_api.c' and can then generate files or whatever.
    :param generators: A sequence (e.g. tuple) of classes or callables that
    will produce objects with a 'write' method. The 'gen_args' will be passed
    to the 'write' method on invocation.
    :param gen_args: Arguments for the autogen generator instances.
    """
    api = HPyAPI.parse(AUTOGEN_H)
    for cls in generators:
        cls(api).write(*gen_args)
