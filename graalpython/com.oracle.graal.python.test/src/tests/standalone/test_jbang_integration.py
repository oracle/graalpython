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

import json
import os
import shutil
import sys
import subprocess
import tempfile
import unittest

is_enabled = 'ENABLE_JBANG_INTEGRATION_UNITTESTS' in os.environ and os.environ['ENABLE_JBANG_INTEGRATION_UNITTESTS'] == "true"
MAVEN_REPO_LOCAL_URL = os.environ.get('org.graalvm.maven.downloader.repository')
GRAAL_VERSION = os.environ.get('org.graalvm.maven.downloader.version')
CATALOG_ALIAS = "tested_catalog"

# whole folder will be deleted after the tests finished
WORK_DIR = os.path.join(tempfile.gettempdir(),tempfile.mkdtemp())
JBANG_CMD = os.environ.get('JBANG_CMD')
ENV = os.environ.copy()
USE_SHELL = 'win' in sys.platform 

def run_cmd(cmd, env=ENV, cwd=None):
    print(f"\nExecuting: {cmd=}")
    process = subprocess.Popen(cmd, env=env, cwd=cwd, shell=USE_SHELL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, text=True, errors='backslashreplace')
    out = []
    print("============== output =============")
    for line in iter(process.stdout.readline, ""):
        print(line, end="")
        out.append(line)
    print("========== end of output ==========")
    return "".join(out), process.wait()
    
class TestJBangIntegration(unittest.TestCase):

    def setUpClass(self):
        if not is_enabled:
            return
        self.ensureProxy()
        self.ensureLocalMavenRepo()
        self.catalog_file = self.getCatalogFile()
        self.registerCatalog(self.catalog_file)
     
    def tearDownClass(self):
        if not is_enabled:
            return
        shutil.rmtree(WORK_DIR)
        
    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()
      
    def tearDown(self):
        shutil.rmtree(self.tmpdir)
        
    def ensureProxy(self):
        java_tools = os.environ.get('JAVA_TOOL_OPTIONS')
        if java_tools is None:
            java_tools = ""
        
        http_proxy = os.environ.get('http_proxy')
        https_proxy = os.environ.get('http_proxy')
        if https_proxy and 'https_proxy' not in java_tools and len(https_proxy.split(":")) == 2:
            server, port = https_proxy.split(":")
            java_tools = f"{java_tools} -Dhttps.proxyHost={server} -Dhttps.proxyPort={port}"
        if http_proxy and 'http_proxy' not in java_tools and len(http_proxy.split(":")) == 2:
            server, port = http_proxy.split(":")
            java_tools = f"{java_tools} -Dhttp.proxyHost={server} -Dhttp.proxyPort={port}"
    
        if len(java_tools) > 0:
            os.environ['JAVA_TOOL_OPTIONS'] = java_tools

    def ensureLocalMavenRepo(self):
        if MAVEN_REPO_LOCAL_URL is None:
            self.fail("'org.graalvm.maven.downloader.repository' is not defined")
    
    def getCatalogFile(self): 
        catalog_dir = os.path.dirname(os.path.abspath(__file__))
        for _ in range(5):
            catalog_dir = os.path.dirname(catalog_dir)
        return os.path.join(catalog_dir, 'jbang-catalog.json')
        
    def getCatalogData(self, catalog_file):
        try:
            with open(catalog_file, 'r') as json_file:
                json_data = json.load(json_file)
            
        except FileNotFoundError:
            self.fail(f"Catalog {catalog_file} was not found.")
        except json.JSONDecodeError:
            self.fail(f"Error during readinj JSON catalog {catalog_file}.")
        except Exception as e:
            self.fail(f"Error during reading catalog: {e}")
        return json_data

    def addLocalMavenRepo(self, file):
    
        with open(file, 'r') as script_file:
            content = script_file.readlines()

        deps_index = next((i for i, line in enumerate(content) if line.startswith("//DEPS")), None)

        if deps_index is not None:
            # TODO can we relay on that in MAVEN_REPO the first one is the local repo?
            local_repo = [
                f'//REPOS mc=https://repo1.maven.org/maven2/',
                f'//REPOS local={MAVEN_REPO_LOCAL_URL}'
            ]
            content.insert(deps_index, '\n'.join(local_repo) + '\n')

            with open(file, 'w') as script_file:
                script_file.writelines(content)
        else:
            self.fail(f"Not found any dependecies in: {file}")
    
    def registerCatalog(self, catalog_file):
        # we need to be sure that the current dir is not dir, where is the catalog defined
        os.chdir(WORK_DIR)
        # find if the catalog is not registered
        command = [JBANG_CMD, "catalog", "list"]
        out, result = run_cmd(command)
        if result == 0:
            if CATALOG_ALIAS not in out:
                # registering our catalog
                command = [JBANG_CMD, "catalog", "add", "--name", CATALOG_ALIAS, catalog_file]
                out, result = run_cmd(command)
                if result != 0:                
                    self.fail(f"Problem during registering catalog: {out}")
        else:
            self.fail(f"Problem during registering catalog: {out}")
            
    @unittest.skipUnless(is_enabled, "ENABLE_JBANG_INTEGRATION_UNITTESTS is not true")
    def test_catalog(self):       
        json_data = self.getCatalogData(self.catalog_file)
        for alias in json_data.get("aliases", {}).values():
            script_ref = alias.get("script-ref")
            script_path = os.path.normpath(os.path.join(os.path.dirname(self.catalog_file), script_ref))
            self.assertTrue(os.path.isfile(script_path), f"The path defined in catalog is not found: {script_path}")

        for template in json_data.get("templates", {}).values():
            for file_ref in template.get("file-refs", {}).values():
                file_path = os.path.normpath(os.path.join(os.path.dirname(self.catalog_file), file_ref))
                self.assertTrue(os.path.isfile(file_path), f"The path definied in catalog is not found: {file_path}")
    
    @unittest.skipUnless(is_enabled, "ENABLE_JBANG_INTEGRATION_UNITTESTS is not true")
    def test_graal_version(self):
        json_data = self.getCatalogData(self.catalog_file)
        for alias in json_data.get("aliases", {}).values():
            script_ref = alias.get("script-ref")
            script_path = os.path.normpath(os.path.join(os.path.dirname(self.catalog_file), script_ref))
            with open(script_path, 'r') as script_file:
                content = script_file.read()
            self.assertIn (f"//DEPS org.graalvm.python:python-language:{GRAAL_VERSION}", content, f"//DEPS org.graalvm.python:python-language:{GRAAL_VERSION} was not foudn in {script_path}")
            self.assertIn (f"//DEPS org.graalvm.python:python-resources:{GRAAL_VERSION}", content, f"//DEPS org.graalvm.python:python-resources:{GRAAL_VERSION} was not foudn in {script_path}")
            self.assertIn (f"//DEPS org.graalvm.python:python-launcher:{GRAAL_VERSION}", content, f"//DEPS org.graalvm.python:python-launcher:{GRAAL_VERSION} was not foudn in {script_path}")
            self.assertIn (f"//DEPS org.graalvm.python:python-embedding:{GRAAL_VERSION}", content, f"//DEPS org.graalvm.python:python-embedding:{GRAAL_VERSION} was not foudn in {script_path}")
            
        for template in json_data.get("templates", {}).values():
            for file_ref in template.get("file-refs", {}).values():
                file_path = os.path.normpath(os.path.join(os.path.dirname(self.catalog_file), file_ref))
                with open(script_path, 'r') as script_file:
                    content = script_file.read()
                self.assertIn (f"//DEPS org.graalvm.python:python-language:{GRAAL_VERSION}", content, f"//DEPS org.graalvm.python:python-language:{GRAAL_VERSION} was not foudn in {script_path}")
                self.assertIn (f"//DEPS org.graalvm.python:python-resources:{GRAAL_VERSION}", content, f"//DEPS org.graalvm.python:python-resources:{GRAAL_VERSION} was not foudn in {script_path}")
                self.assertIn (f"//DEPS org.graalvm.python:python-launcher:{GRAAL_VERSION}", content, f"//DEPS org.graalvm.python:python-launcher:{GRAAL_VERSION} was not foudn in {script_path}")
                self.assertIn (f"//DEPS org.graalvm.python:python-embedding:{GRAAL_VERSION}", content, f"//DEPS org.graalvm.python:python-embedding:{GRAAL_VERSION} was not foudn in {script_path}")
                
    @unittest.skipUnless(is_enabled, "ENABLE_JBANG_INTEGRATION_UNITTESTS is not true")
    def test_graalpy_template(self):
        template_name = "graalpy"
        test_file = "graalpy_test.java"
        work_dir = self.tmpdir
        os.chdir(work_dir)
        
        command = [JBANG_CMD, "--verbose", "init", f"--template={template_name}@{CATALOG_ALIAS}" , test_file]
        out, result = run_cmd(command)
        self.assertTrue(result == 0, f"Creating template {template_name} failed")
        
        # add local maven repo to the deps
        test_file_path = os.path.join(work_dir, test_file)
        self.addLocalMavenRepo(test_file_path)
        
        tested_code = "print (f\'This is test text and result is {123456789 * 1000}\')"
        expected_text = "This is test text and result is 123456789000"
        command = [JBANG_CMD, "--verbose",  test_file_path, tested_code]
        out, result = run_cmd(command)
        
        self.assertTrue(result == 0, f"Execution failed with code {result}\n    command: {command}\n    stdout: {out}\n")
        self.assertTrue(expected_text in out, f"Expected text:\n{expected_text}\nbut in stdout was:\n{out}")
        
    @unittest.skipUnless(is_enabled, "ENABLE_JBANG_INTEGRATION_UNITTESTS is not true")
    @unittest.skipUnless('win' not in sys.platform, "Currently the jbang native image on Win gate fails.")
    def test_graalpy_template_native(self):
        template_name = "graalpy"
        test_file = "graalpy_test.java"
        work_dir = self.tmpdir
        os.chdir(work_dir)
        
        command = [JBANG_CMD, "--verbose", "init", f"--template={template_name}@{CATALOG_ALIAS}" , test_file]
        out, result = run_cmd(command)
        self.assertTrue(result == 0, f"Creating template {template_name} failed")
        
        test_file_path = os.path.join(work_dir, test_file)
        self.addLocalMavenRepo(test_file_path)
        tested_code = "print (f\'This is test text and result is {147258369 * 1000}\')"
        expected_text = "This is test text and result is 147258369000"
        command = [JBANG_CMD, "--verbose", "--native", test_file_path, tested_code]
        out, result = run_cmd(command)
        
        self.assertTrue(result == 0, f"Execution failed with code {result}\n    command: {command}\n    stdout: {out}")
        self.assertTrue(expected_text in out, f"Expected text:\n{expected_text}\nbut in stdout was:\n{out}")
            
    
    @unittest.skipUnless(is_enabled, "ENABLE_JBANG_INTEGRATION_UNITTESTS is not true")
    def test_graalpy_local_repo_template(self):
        template_name = "graalpy_local_repo"
        test_file = "graalpy_test_local_repo.java"
        work_dir = self.tmpdir
        os.chdir(work_dir)
        
        command = [JBANG_CMD, "--verbose", "init", f"--template={template_name}@{CATALOG_ALIAS}", f"-Dpath_to_local_repo={MAVEN_REPO_LOCAL_URL}", test_file]
        out, result = run_cmd(command)
        self.assertTrue(result == 0, f"Creating template {template_name} failed")
        
        test_file_path = os.path.join(work_dir, test_file)
        tested_code = "print (f\'This is test text and result is {987654321 * 1000}\')"
        expected_text = "This is test text and result is 987654321000"
        command = [JBANG_CMD, "--verbose", test_file_path, tested_code]
        out, result = run_cmd(command)
        
        self.assertTrue(result == 0, f"Execution failed with code {result}\n    command: {command}\n    stdout: {out}")
        self.assertTrue(expected_text in out, f"Expected text:\n{expected_text}\nbut in stdout was:\n{out}")
        