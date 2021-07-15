import io
import os


from .context import reduction, set_spawning_popen
from . import spawn
from . import util

from _multiprocessing import _waittid, _terminate_spawned_thread, _spawn_context, _pipe, _write

__all__ = ['Popen']

#
# Wrapper for an fd used while launching a process
#

class _DupFd(object):
    def __init__(self, fd):
        self.fd = fd
    def detach(self):
        return self.fd
    
#
# Start child truffle context
#
# inspired by popen_spawn_posix and popen_spawn_fork
#

class Popen(object):
    method = 'spawn'
    
    DupFd = _DupFd
    
    def __init__(self, process_obj):
        util._flush_std_streams()
        self.returncode = None
        self.finalizer = None
        self._launch(process_obj)

    def duplicate_for_child(self, fd):
        return fd

    def poll(self, flag=os.WNOHANG):
        if self.returncode is None:
            try:
                tid, sts = _waittid(self._tid, flag)
            except OSError as e:
                return None
            if tid == self._tid:
                if os.WIFSIGNALED(sts):
                    self.returncode = -os.WTERMSIG(sts)
                else:
                    assert os.WIFEXITED(sts), "Status is {:n}".format(sts)
                    self.returncode = os.WEXITSTATUS(sts)
        return self.returncode

    def wait(self, timeout=None):
        if self.returncode is None:
            if timeout is not None:
                from multiprocessing.connection import wait
                if not wait([self.sentinel], timeout):                    
                    return None
            # This shouldn't block if wait() returned successfully.
            return self.poll(os.WNOHANG if timeout == 0.0 else 0)
        return self.returncode

    def terminate(self):
        if self._tid is not None:
            _terminate_spawned_thread(self._tid)

    def kill(self):
        if self._tid is not None:
            _terminate_spawned_thread(self._tid)
        
    def _launch(self, process_obj):
        prep_data = spawn.get_preparation_data(process_obj._name)            
        fp = io.BytesIO()
        
        parent_r = child_w = child_r = parent_w = None
        
        parent_r, child_w = _pipe()
        child_r, parent_w = _pipe()
        
        set_spawning_popen(self)
        try:
            reduction.dump(prep_data, fp)
            reduction.dump(process_obj, fp)
        finally:
            set_spawning_popen(None)
        
        self.sentinel = parent_r
        _write(parent_w, fp.getbuffer().tobytes())
            
        self._tid = _spawn_context(child_r, child_w)
        self.pid = self._tid
        
    def close(self):
        if self.finalizer is not None:
            self.finalizer()        