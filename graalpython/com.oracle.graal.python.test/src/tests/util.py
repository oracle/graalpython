import sys


def storage_to_native(s):
    if sys.implementation.name == 'graalpy':
        assert hasattr(__graalpython__, 'storage_to_native'), "Needs to be run with --python.EnableDebuggingBuiltins"
        __graalpython__.storage_to_native(s)
