from pycparser import c_ast
from .autogenfile import AutoGenFile
from .parse import toC

class autogen_hpyslot_h(AutoGenFile):
    PATH = 'hpy/devel/include/hpy/autogen_hpyslot.h'

    def generate(self):
        lines = []
        w = lines.append
        w('typedef enum {')
        for slot in self.api.hpyslots:
            w(f'    {slot.name} = {slot.value},')
        w('} HPySlot_Slot;')
        w('')
        for slot in self.api.hpyslots:
            w(f'#define _HPySlot_SIG__{slot.name} {slot.hpyfunc}')
        return '\n'.join(lines)
