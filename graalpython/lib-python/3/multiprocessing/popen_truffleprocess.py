import io
import os
import signal

from .context import reduction, set_spawning_popen
from . import spawn
from . import util
from . import process

from _multiprocessing_graalpy import _waittid, _terminate_spawned_thread, _spawn_context, _pipe, _read, _write, _close

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
    method = 'graalpy'

    DupFd = _DupFd

    def __init__(self, process_obj):
        util._flush_std_streams()
        self._fds = []
        self.returncode = None
        self.finalizer = None
        self._launch(process_obj)

    def duplicate_for_child(self, fd):
        self._fds.append(fd)
        return fd

    def poll(self, flag=os.WNOHANG):
        if self.returncode is None:
            try:
                # this is different than in popen_fork -> os.waitpid(self.pid, flag)
                # we have no real proces pid and a process
                # which could have been evenutally signaled
                tid, sigcode, exitcode = _waittid(self._tid, flag)
            except OSError as e:
                return None
            if tid == self._tid:
                if sigcode > 0:
                    self.returncode = -sigcode
                else:
                    self.returncode = exitcode
        return self.returncode

    def wait(self, timeout=None):
        if self.returncode is None:
            # begin change
            # if timeout is not None:
            #   from multiprocessing.connection import wait
            #   if not wait([self.sentinel], timeout):                    
            #       return None

            # this method was copied from popen_fork, and is called (only?) from process.join()
            # - TODO docs says that process.join(timeout=None) should block, 
            # but if so, than it entirely relies on this popen.wait() 
            # and calling wait() only if timeout != None would not block =>
            # => call wait() always, even if timeout == None
            # - see also _test_multiprocessing.py/test_sentinel:
            # after p.join() (which should return once the process is done), 
            # wait_for_handle() is still called with a timeout - why so if the process is already done?
            # the test (and other) fail(s) with the original impl commented above, 
            # raising the timeout value in wait_for_handle helps, graalpython gets more time to finish the process
            from multiprocessing.connection import wait
            if not wait([self.sentinel], timeout):
                return None
            # end change
            # This shouldn't block if wait() returned successfully.
            return self.poll(os.WNOHANG if timeout == 0.0 else 0)
        return self.returncode

    def _send_signal(self, sig):
        if self.returncode is None:
            _terminate_spawned_thread(self._tid, sig)

    def terminate(self):
        self._send_signal(signal.SIGTERM)

    def kill(self):
        self._send_signal(signal.SIGKILL)

    def _launch(self, process_obj):
        prep_data = spawn.get_preparation_data(process_obj._name)
        fp = io.BytesIO()

        parent_r = child_w = child_r = parent_w = None

        try:    
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

            self._fds.extend([child_r, child_w])
            self._tid = _spawn_context(child_r, child_w, self._fds)
            self.pid = self._tid
        finally:
            fds_to_close = []
            for fd in (parent_r, parent_w):
                if fd is not None:
                    fds_to_close.append(fd)
            self.finalizer = util.Finalize(self, util.close_fds, fds_to_close)

            for fd in (child_r, child_w):
                if fd is not None:
                    _close(fd)

    def close(self):
        if self.finalizer is not None:
            self.finalizer()


# Entry point to the child context thread
def spawn_truffleprocess(fd, parent_sentinel):
    process.current_process()._inheriting = True
    try:
        bytesIO = io.BytesIO(_read(fd, 1024))
        preparation_data = reduction.pickle.load(bytesIO)
        spawn.prepare(preparation_data)
        self = reduction.pickle.load(bytesIO)
    finally:
        del process.current_process()._inheriting
    return self._bootstrap(parent_sentinel)
