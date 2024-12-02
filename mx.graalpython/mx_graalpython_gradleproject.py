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

from __future__ import annotations

import pathlib
import textwrap
import os
import shutil

import mx
import mx_urlrewrites


class GradlePluginProject(mx.Distribution, mx.ClasspathDependency):  # pylint: disable=too-many-instance-attributes
    """
    Mx project and distribution for Gradle plugins projects. These have properties of both mx Java projects and
    mx (Jar) distributions.

    Gradle plugin project should contain java sources following the usual Gradle conventions.
    During the MX build, this project generates a build.gradle file including declaration of all
    the dependencies defined in suite.py and then invokes Gradle to produce the JAR file and
    sources JAR file.

    GradlePluginProject handles only building of the JARs via Gradle, but leaves the Maven deployment to MX,
    notably the deployment pom.xml generation. Therefore, all the metadata necessary for Maven publishing,
    especially dependencies, must be in suite.py. GradleProject will pull the necessary metadata from MX and
    add them to the generated build.gradle script.

    For the time being, this project does not embed the Maven deployment pom.xml into the META-INF directory.

    In order to be able to simply reuse the MX Maven deployment infrastructure, we need to generate one
    artifact. Normally, Gradle plugins consist of two Maven artifacts: the plugin marker, which is just
    a meta pom that depends on the plugin implementation Maven artifact. However, a structure where the
    plugin marker and the implementation are merged into one artifact is also supported by Gradle, but
    not by the Gradle plugin for plugin development. We therefore need to create our plugin "properties"
    file manually and disable its generation by the Gradle plugin. Otherwise, we can use the other features
    of the Gradle plugin: automatically injected Gradle API dependency and the verifyPlugin task (which is
    run as part of mx build of this project).
    """

    def __init__(
            self, suite: mx.Suite, name: str, deps, excludedLibs, platformDependent=None, theLicense=None, **args
    ):  # pylint: disable=too-many-arguments
        if platformDependent is not False:
            mx.abort(
                "GradlePluginProject must be in the 'distributions' section of a suite.py and platformDependent cannot be True"
            )
        super().__init__(suite, name, deps, excludedLibs, platformDependent, theLicense, **args)
        self.gradle_directory = os.path.join(suite.dir, args.get("subDir", ""), name)
        self.dir = self.gradle_directory # needed for mx checkstyle machinery

        for attr_name in ['javaCompliance', 'gradlePluginId', 'gradlePluginImplementation']:
            if not hasattr(self, attr_name):
                mx.abort(f"GradlePluginProject projects must specify {attr_name}")

        self.javaCompliance = mx.JavaCompliance(self.javaCompliance)

        # Note: unlike MX MavenProject, we require the user to set the Maven metadata in suite.py

        # These must be set for Jar distributions that can be deployed to maven:
        self._packaging = self._local_ext = self._remote_ext = "jar"
        self.sourcesname = f"{self.name}-sources.{self._local_ext}"
        self.sourcesPath = os.path.join(self.get_output_root(), self.sourcesname)  # pylint: disable=invalid-name
        self.path = self._default_path()

        # right now we don't let Gradle projects define annotation processors
        self.definedAnnotationProcessors = []  # pylint: disable=invalid-name

        if hasattr(self, 'checkstyle') and self.checkstyle == self.name:
            mx.abort("GradlePluginProject must use checkstyle config of another existing Java project")

    def source_dirs(self):
        """
        Because we are a kind of JavaProject, mx asks for our source_dirs in various locations.
        This is used for things like scanning defined packages or generating javadoc.
        """
        return [os.path.join(self.gradle_directory, "src/main/java")]

    def get_checkstyle_config(self):
        # Note: this needs support in MX: the only thing needed is that mx checkstyle scans not only
        # projects, but also distributions that happen to have 'get_checkstyle_config' attribute
        if not hasattr(self, 'checkstyle'):
            return (None, None, None)

        checkstyleProj = self if self.checkstyle == self.name else mx.project(self.checkstyle, context=self)
        config = os.path.join(checkstyleProj.dir, '.checkstyle_checks.xml')
        if not os.path.exists(config):
            mx.abort("Cannot find checkstyle configuration: " + config)

        checkstyleVersion = getattr(checkstyleProj, 'checkstyleVersion')
        if not checkstyleVersion:
            checkstyleVersion = checkstyleProj.suite.getMxCompatibility().checkstyleVersion()

        return config, checkstyleVersion, checkstyleProj

    def classpath_repr(self, resolve=True) -> str | None:
        """
        Returns this project's output jar if it has <packaging>jar</packaging>
        """
        jar = self._default_path()
        if jar.endswith(".jar"):
            if resolve and not os.path.exists(jar):
                mx.abort(f"unbuilt Gradle project {self} cannot be on a class path ({jar})")
            return jar
        return None

    def make_archive(self):
        os.makedirs(os.path.dirname(self._default_path()), exist_ok=True)
        shutil.copy(os.path.join(self.get_output_root(), self.default_filename()), self._default_path())
        return self._default_path()

    def exists(self):
        return os.path.exists(self._default_path())

    def prePush(self, f):
        return f

    def get_output_root(self):
        """
        Gets the root of the directory hierarchy under which generated artifacts for this
        dependency such as class files and annotation generated sources should be placed.
        """
        return os.path.join(self.get_output_base(), self.name)

    def isJARDistribution(self):
        return self.localExtension() == "jar"

    def isJavaProject(self):
        return True

    def remoteExtension(self) -> str:
        return self._remote_ext

    def localExtension(self) -> str:
        return self._local_ext

    def set_archiveparticipant(self, _):
        pass

    def needsUpdate(self, newestInput):
        if not os.path.exists(self._default_path()):
            return True, "Gradle package does not exist"
        if not os.path.exists(self.sourcesPath):
            return True, "Gradle sources do not exist"
        newest_source = newestInput.timestamp if newestInput else 0
        for root, _, files in os.walk(self.gradle_directory):
            if files:
                newest_source = max(newest_source, max(mx.getmtime(os.path.join(root, f)) for f in files))
        if mx.getmtime(self._default_path()) < newest_source:
            return True, "Gradle package out of date"
        if mx.getmtime(self.sourcesPath) < newest_source:
            return True, "Gradle sources out of date"
        return False, "Gradle package is up to date"

    def getBuildTask(self, args):
        self.deps = [mx.dependency(d) for d in self.deps]
        return _GradleBuildTask(self, args)

    def get_ide_project_dir(self):
        """
        We generate the build script which can be manually imported in IDE, but otherwise
        don't use mx's support for generating ide configs. By returning None, we tell
        mx eclipseinit to just ignore this project.
        """
        self.getBuildTask([])._create_build_script()


def _run_gradlew(args, **kwargs):
    kwargs.setdefault('env', os.environ.copy())
    env = kwargs.pop('env')
    if 'GRADLE_JAVA_HOME' not in env:
        def abortCallback(msg):
            mx.abort("Could not find a JDK of version between 17 and 21 to build a Gradle project.\n"
                     "Export GRADLE_JAVA_HOME pointing to a suitable JDK "
                     "or use the generic MX mechanism explained below:\n" + msg)
        jdk = mx.get_jdk('17..21', abortCallback=abortCallback)
        env['GRADLE_JAVA_HOME'] = jdk.home
        env['JAVA_HOME'] = jdk.home
    else:
        env['JAVA_HOME'] = env['GRADLE_JAVA_HOME']
    mx.logv("Building Gradle project using java: " + env['GRADLE_JAVA_HOME'])
    command = './gradlew'
    if mx.is_windows():
        command = 'gradle.bat'
    mx.run([command, *args], env=env, **kwargs)


# Gradle uses forward slashes in paths even on Windows
def _as_gradle_path(p:str) -> str:
    return pathlib.Path(p).as_posix()


class _GradleBuildTask(mx.ProjectBuildTask):
    PLUGIN_PORTAL_URL = 'https://plugins.gradle.org/m2/'

    def __init__(self, subject: GradlePluginProject, args):
        mx.ProjectBuildTask.__init__(self, args, 1, subject)
        self.build_script_path = os.path.join(self.subject.get_output_root(), "build.gradle")

    def needsBuild(self, newestInput):
        if self.args.force:
            return (True, "forced build")
        return self.subject.needsUpdate(newestInput)

    def clean(self, forBuild=False):
        shutil.rmtree(self.subject.get_output_root(), ignore_errors=True)
        if os.path.exists(self.subject.path):
            os.unlink(self.subject.path)

    def __str__(self):
        return self.subject.name

    def build(self, version: str | None = None):
        os.makedirs(self.subject.get_output_root(), exist_ok=True)
        os.makedirs(os.path.dirname(self.subject.path), exist_ok=True)
        self._create_build_script(version=version)
        _run_gradlew(['jar', 'validatePlugins', 'mxJars'], cwd=self.subject.get_output_root())
        self.subject.make_archive()

    def _create_build_script(self, version: str | None = None):
        """
        Generates a build.gradle script. Most notably we configure:

        The sources location, because the script will be saved into a subdirectory
        of mxbuild directory.

        Dependencies: For MX libraries from Maven, we use their coordinates, other
        types of libraries are not allowed as a dependency for GradleProject. For
        MX distributions we add dependency on the JAR file directly. Since we only
        want to build the project (and not run it), we only need to add direct
        dependencies. Referencing API from indirect dependency is not a good practice
        and in such case the indirect dependency should be turned into explicit direct
        dependency.

        Tasks to copy the resulting jars from the build directory where Gradle puts
        them by default into the project directory where MX expects them (mxJars task).
        """
        # Gradle wrapper
        shutil.copytree(os.path.join(self.subject.gradle_directory, "wrapper"), self.subject.get_output_root(), dirs_exist_ok=True)
        properties_path = os.path.join("gradle", "wrapper", "gradle-wrapper.properties")
        wrapper_properties_file = os.path.join(self.subject.get_output_root(), properties_path)
        with open(wrapper_properties_file, 'r') as f:
            wrapper_properties = [l.strip() for l in f.readlines()]
        found = False
        for i, line in enumerate(wrapper_properties):
            if line.strip().startswith('distributionUrl='):
                url = line[len('distributionUrl='):].replace('\\:', ':')
                new_url = mx_urlrewrites.rewriteurl(url)
                mx.logv("Rewritten Gradle distribution URL to: " + new_url)
                wrapper_properties[i] = 'distributionUrl=' + new_url.replace(':', '\\:')
                found = True
                break
        if not found:
            mx.abort("Could not find 'distributionUrl' in " + os.path.join(self.subject.gradle_directory, "wrapper", properties_path))
        mx.logvv(f'Patched {properties_path} to:\n' + '\n'.join(wrapper_properties))
        with open(wrapper_properties_file, 'w') as f:
            f.write('\n'.join(wrapper_properties))

        # build.gradle
        deps_decls = []
        for d in self.subject.deps:
            if d.isLayoutDirDistribution() or d.suite.internal:
                continue
            if d.isLibrary():
                m = getattr(d, 'maven')
                if not m:
                    mx.abort("Gradle project can only depend on Maven libraries")
                deps_decls.append(f"implementation '{m['groupId']}:{m['artifactId']}:{m['version']}'")
            elif d.isDistribution():
                path = os.path.join(".", os.path.relpath(d.path, self.subject.get_output_root()))
                deps_decls.append(f"implementation files('{_as_gradle_path(path)}')")
        deps_decl = '\n' + os.linesep.join([(16 * ' ') + d for d in deps_decls])

        sources_dir = os.path.join(self.subject.gradle_directory, 'src', 'main', 'java')
        sources_dir = os.path.join(".", os.path.relpath(sources_dir, self.subject.get_output_root()))
        sources_dir = _as_gradle_path(sources_dir)
        version = self.subject.suite.release_version()
        if version.endswith('-dev'):
            version = version[:-len('-dev')]
        script = textwrap.dedent(f'''
            plugins {{
                id 'java'
                id 'java-gradle-plugin'
            }}

            version = '{version}'

            gradlePlugin {{
                plugins {{
                    graalPy {{
                        id = '{self.subject.gradlePluginId}'
                        implementationClass = '{self.subject.gradlePluginImplementation}'
                    }}
                }}
            }}

            // Disable to automatic generation of org.graalvm.python.properties, we
            // use manually created properties file
            tasks.withType(GeneratePluginDescriptors).configureEach {{
                enabled = false
            }}

            java {{
                sourceCompatibility = JavaVersion.VERSION_{self.subject.javaCompliance.value}
                targetCompatibility = JavaVersion.VERSION_{self.subject.javaCompliance.value}
                withSourcesJar()
            }}

            dependencies {{
                {deps_decl}
            }}

            sourceSets {{
                main {{
                    java {{ srcDir '{sources_dir}' }}
                }}
            }}

            tasks.register("mxJars", Copy) {{
                into(".") // the project root directory
                doNotTrackState("Copying to current working directory causes issues with tracking on Windows")
                outputs.upToDateWhen {{ false }} // just run this always
                from(tasks.named("jar")) {{
                    rename {{ "{self.subject.name}.jar" }}
                }}
                from(tasks.named("sourcesJar")) {{
                    rename {{ "{self.subject.name}-sources.jar" }}
                }}
            }}

            validatePlugins.mustRunAfter mxJars
            ''').strip()

        if 'MX_GRADLE_GENERATE_CHECKSTYLE' in os.environ:
            # Note: the checkstyle config in build.gradle is not automatically imported into IntelliJ :-/
            checkstyle_config, checkstyle_ver, _ = self.subject.get_checkstyle_config()
            if checkstyle_config:
                script += '\n' + textwrap.dedent(f'''
                    apply plugin 'checkstyle'
                    repositories {{
                        mavenCentral()
                    }}
                    checkstyle {{
                        toolVersion = '{checkstyle_ver}'
                        configFile = '{checkstyle_config}' as File
                    }}
                    ''').strip()

        os.makedirs(self.subject.get_output_root(), exist_ok=True)
        with open(self.build_script_path, 'w+') as f:
            f.write(script)
        mx.logv(f"Gradle build script written to {self.build_script_path}")
        mx.logvv(script)

        # org.graalvm.python.properties: plugin properties file
        properties = textwrap.dedent(f'''
            implementation-class={self.subject.gradlePluginImplementation}
            version={version}
            id={self.subject.gradlePluginId}
            ''').strip()
        resources_dir = os.path.join(self.subject.get_output_root(), 'src', 'main', 'resources', 'META-INF', 'gradle-plugins')
        os.makedirs(resources_dir, exist_ok=True)
        properties_file_path = os.path.join(resources_dir, 'org.graalvm.python.properties')
        with open(properties_file_path, 'w+') as f:
            f.write(properties)
        mx.logv(f"Gradle plugin properties file written to {properties_file_path}")
        mx.logvv(properties)

        # settings.gradle
        settings = f'rootProject.name = "{self.subject.gradleProjectName}"'
        plugin_repo = mx_urlrewrites.rewriteurl(_GradleBuildTask.PLUGIN_PORTAL_URL)
        if plugin_repo != _GradleBuildTask.PLUGIN_PORTAL_URL:
            # Note: we are not including mavenCentral for ordinary dependencies,
            # so this should be the only repo that Gradle searches, so far we only
            # depend on one external plugin: java-gradle-plugin
            settings = textwrap.dedent(f'''
                pluginManagement {{
                    repositories {{
                        maven {{
                            url = uri("{plugin_repo}")
                        }}
                    }}
                }}
            ''') + settings
        settings_path = os.path.join(self.subject.get_output_root(), "settings.gradle")
        with open(settings_path, 'w+') as f:
            f.write(settings)
        mx.logv(f"Gradle settings written to {settings_path}")
        mx.logvv(settings)