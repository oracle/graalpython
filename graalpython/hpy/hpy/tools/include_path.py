"""Prints the include path for the current Python interpreter."""

from sysconfig import get_paths as gp
print(gp()['include'])
