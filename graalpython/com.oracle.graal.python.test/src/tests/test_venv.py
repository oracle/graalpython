import tempfile
import sys
import shutil
import os
import subprocess


class VenvTest():
    def setUp(self):
        self.env_dir = os.path.realpath(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.env_dir)

    def test_create_and_use_basic_venv(self):
        create = subprocess.check_output([sys.executable, "-m", "venv", self.env_dir, "--without-pip"])
        assert create.decode() == "", create
        run = subprocess.getoutput(". %s/bin/activate; python -m site" % self.env_dir)
        assert "ENABLE_USER_SITE: False" in run, run
        assert self.env_dir in run, run
