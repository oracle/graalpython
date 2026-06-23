# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

"""Regenerate the Maven IDE facade from mx.graalpython/suite.py."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

import mx

ROOT = Path(__file__).resolve().parents[1]
SUITE_PATH = Path("mx.graalpython/suite.py")
GENERATOR_PATH = Path("mx.graalpython/mx_pominit.py")

ROOT_GROUP_ID = "org.graalvm.python.ide"
ROOT_ARTIFACT_ID = "graalpy-source-workspace"
ROOT_VERSION = "1.0-SNAPSHOT"
LOCAL_GROUP_ID = "${project.groupId}"
LOCAL_VERSION = "${project.version}"
GRAALVM_VERSION = "${graalvm.version}"
DEFAULT_GRAALVM_VERSION = "25.1.3"
CURRENT_GRAALVM_VERSION = "25.2.4"

XML_UPL_HEADER = """<!--
Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

The Universal Permissive License (UPL), Version 1.0

Subject to the condition set forth below, permission is hereby granted to any
person obtaining a copy of this software, associated documentation and/or
data (collectively the "Software"), free of charge and under any and all
copyright rights in the Software, and any and all patent rights owned or
freely licensable by each licensor hereunder covering either (i) the
unmodified Software as contributed to or provided by such licensor, or (ii)
the Larger Works (as defined below), to deal in both

(a) the Software, and

(b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
one is included with the Software each a "Larger Work" to which the Software
is contributed by such licensors),

without restriction, including without limitation the rights to copy, create
derivative works of, display, perform, and distribute the Software and make,
use, sell, offer for sale, import, export, have made, and have sold the
Software and the Larger Work(s), and to sublicense the foregoing rights on
either these or other terms.

This license is subject to the following condition:

The above copyright notice and either this complete permission notice or at a
minimum a reference to the UPL must be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
-->"""

FACADE_PROJECTS = [
    "com.oracle.graal.python.annotations",
    "com.oracle.graal.python.processor",
    "com.oracle.graal.python.pegparser",
    "com.oracle.graal.python.resources",
    "com.oracle.graal.python",
    "com.oracle.graal.python.shell",
    "com.oracle.graal.python.bouncycastle",
]

# mx dependency names remain authoritative. This table is the only place that
# translates mx dependency names to Maven coordinates for the IDE facade.
MX_TO_MAVEN = {
    "com.oracle.graal.python.annotations": (LOCAL_GROUP_ID, "com.oracle.graal.python.annotations"),
    "com.oracle.graal.python.processor": (LOCAL_GROUP_ID, "com.oracle.graal.python.processor"),
    "com.oracle.graal.python.pegparser": (LOCAL_GROUP_ID, "com.oracle.graal.python.pegparser"),
    "com.oracle.graal.python.resources": (LOCAL_GROUP_ID, "com.oracle.graal.python.resources"),
    "com.oracle.graal.python": (LOCAL_GROUP_ID, "com.oracle.graal.python"),
    "com.oracle.graal.python.shell": (LOCAL_GROUP_ID, "com.oracle.graal.python.shell"),
    "com.oracle.graal.python.bouncycastle": (LOCAL_GROUP_ID, "com.oracle.graal.python.bouncycastle"),
    "truffle:TRUFFLE_API": ("org.graalvm.truffle", "truffle-api", GRAALVM_VERSION),
    "truffle:TRUFFLE_DSL_PROCESSOR": ("org.graalvm.truffle", "truffle-dsl-processor", GRAALVM_VERSION),
    "truffle:TRUFFLE_ICU4J": ("org.graalvm.shadowed", "icu4j", GRAALVM_VERSION),
    "truffle:TRUFFLE_XZ": ("org.graalvm.shadowed", "xz", GRAALVM_VERSION),
    "tools:TRUFFLE_PROFILER": ("org.graalvm.tools", "profiler-tool", GRAALVM_VERSION),
    "sdk:POLYGLOT": ("org.graalvm.polyglot", "polyglot", GRAALVM_VERSION),
    "sdk:LAUNCHER_COMMON": ("org.graalvm.sdk", "launcher-common", GRAALVM_VERSION),
    "sdk:JLINE3": ("org.graalvm.shadowed", "jline", GRAALVM_VERSION),
    "sdk:MAVEN_DOWNLOADER": ("org.graalvm.sdk", "maven-downloader", GRAALVM_VERSION),
    "sdk:NATIVEIMAGE": ("org.graalvm.sdk", "nativeimage", GRAALVM_VERSION),
    "regex:TREGEX": ("org.graalvm.regex", "regex", GRAALVM_VERSION),
}

DEPENDENCY_ALIASES = {
    "GRAALPYTHON": ["com.oracle.graal.python", "sdk:NATIVEIMAGE"],
}

ANNOTATION_PROCESSOR_ALIASES = {
    "GRAALPYTHON_PROCESSOR": ["com.oracle.graal.python.processor"],
}

GRAALVM_VERSION_RE = re.compile(r"\d+\.\d+\.\d+")


@dataclass(frozen=True)
class MavenDependency:
    group_id: str
    artifact_id: str
    version: str | None = None
    scope: str | None = None
    optional: str | None = None

    def with_scope(self, scope: str | None = None, optional: str | None = None) -> "MavenDependency":
        return MavenDependency(self.group_id, self.artifact_id, self.version, scope, optional)


def load_suite(mx_suite) -> dict:
    return mx_suite.suiteDict


def load_projects(mx_suite) -> dict:
    return {project.name: project for project in mx_suite.projects}


def parse_graalvm_version(version: str) -> tuple[int, int, int]:
    if not GRAALVM_VERSION_RE.fullmatch(version):
        mx.abort(f"Cannot parse Maven IDE facade GraalVM version {version!r}")
    return tuple(int(part) for part in version.split("."))


def suite_graalvm_version(mx_suite) -> str:
    version = mx_suite.release_version()
    if not mx_suite.is_release() and version.endswith("-dev"):
        version = version[:-len("-dev")]
    parse_graalvm_version(version)
    return version


def graalvm_versions(mx_suite) -> tuple[str, str]:
    suite_version = suite_graalvm_version(mx_suite)
    if parse_graalvm_version(suite_version) > parse_graalvm_version(CURRENT_GRAALVM_VERSION):
        return CURRENT_GRAALVM_VERSION, suite_version
    return DEFAULT_GRAALVM_VERSION, CURRENT_GRAALVM_VERSION


def update_generator_versions(content: str, default_version: str, current_version: str) -> str:
    def replace_constant(text: str, name: str, value: str) -> str:
        updated, count = re.subn(
            rf'^{name} = "[^"]+"$',
            f'{name} = "{value}"',
            text,
            count=1,
            flags=re.MULTILINE,
        )
        if count != 1:
            raise SystemExit(f"Cannot update {name} in {GENERATOR_PATH}")
        return updated

    content = replace_constant(content, "DEFAULT_GRAALVM_VERSION", default_version)
    return replace_constant(content, "CURRENT_GRAALVM_VERSION", current_version)


def project_path(project_name: str, project) -> str:
    subdir = project.subDir
    if not subdir:
        raise SystemExit(f"{project_name}: expected suite.py subDir for Maven IDE facade generation")
    return f"{subdir}/{project_name}"


def common_java_version(projects: dict) -> str:
    versions = {projects[name].javaCompliance.value for name in FACADE_PROJECTS}
    if len(versions) != 1:
        raise SystemExit(f"Maven IDE facade expects uniform javaCompliance, got {sorted(versions)!r}")
    return str(versions.pop())


def common_source_dir(projects: dict) -> str:
    source_dirs = {tuple(projects[name].srcDirs) for name in FACADE_PROJECTS}
    if len(source_dirs) != 1:
        raise SystemExit(f"Maven IDE facade expects uniform sourceDirs, got {sorted(source_dirs)!r}")
    source_dir = source_dirs.pop()
    if len(source_dir) != 1:
        raise SystemExit(f"Maven IDE facade expects one source directory, got {source_dir!r}")
    return source_dir[0]


def expand_processor(processor: str) -> list[str]:
    return ANNOTATION_PROCESSOR_ALIASES.get(processor, [processor])


def expand_dependency(mx_name: str) -> list[str]:
    return DEPENDENCY_ALIASES.get(mx_name, [mx_name])


def maven_coord(mx_name: str, suite: dict) -> MavenDependency:
    if mx_name in MX_TO_MAVEN:
        return MavenDependency(*MX_TO_MAVEN[mx_name])
    library = suite.get("libraries", {}).get(mx_name)
    if library and "maven" in library:
        maven = library["maven"]
        return MavenDependency(maven["groupId"], maven["artifactId"], version=maven.get("version"))
    raise SystemExit(f"No Maven coordinate mapping for mx dependency {mx_name!r}")


def dependency_name(dep) -> str:
    if isinstance(dep, str):
        return dep
    if dep.suite.name == "graalpython":
        return dep.name
    return f"{dep.suite.name}:{dep.name}"


def project_dependencies(project, suite: dict) -> list[MavenDependency]:
    result = []
    for dep in project.deps:
        mx_name = dependency_name(dep)
        for expanded in expand_dependency(mx_name):
            result.append(maven_coord(expanded, suite))
    for processor in getattr(project, "declaredAnnotationProcessors", []):
        processor_name = dependency_name(processor)
        for mx_name in expand_processor(processor_name):
            result.append(maven_coord(mx_name, suite).with_scope(scope="provided", optional="true"))
    return result


def all_managed_dependencies(projects: dict, suite: dict) -> list[MavenDependency]:
    seen = set()
    result = []
    for name in FACADE_PROJECTS:
        local = MavenDependency(LOCAL_GROUP_ID, name)
        if (local.group_id, local.artifact_id) not in seen:
            seen.add((local.group_id, local.artifact_id))
            result.append(local)
        for dep in project_dependencies(projects[name], suite):
            key = (dep.group_id, dep.artifact_id)
            if key not in seen:
                seen.add(key)
                result.append(MavenDependency(dep.group_id, dep.artifact_id, dep.version))
    return result


def dependency_xml(dep: MavenDependency, indent: str = "    ", include_version: bool = False) -> str:
    lines = [
        f"{indent}<dependency>",
        f"{indent}  <groupId>{dep.group_id}</groupId>",
        f"{indent}  <artifactId>{dep.artifact_id}</artifactId>",
    ]
    if include_version:
        version = dep.version or (LOCAL_VERSION if dep.group_id == LOCAL_GROUP_ID else GRAALVM_VERSION)
        lines.append(f"{indent}  <version>{version}</version>")
    if dep.scope:
        lines.append(f"{indent}  <scope>{dep.scope}</scope>")
    if dep.optional:
        lines.append(f"{indent}  <optional>{dep.optional}</optional>")
    lines.append(f"{indent}</dependency>")
    return "\n".join(lines)


def root_pom(suite: dict, projects: dict, graalvm_version: str) -> str:
    java_version = common_java_version(projects)
    source_dir = common_source_dir(projects)
    modules = [project_path(name, projects[name]) for name in FACADE_PROJECTS]
    managed = all_managed_dependencies(projects, suite)
    module_xml = "\n".join(f"    <module>{module}</module>" for module in modules)
    dependency_xmls = "\n".join(dependency_xml(dep, indent="      ", include_version=True) for dep in managed)
    return f'''<?xml version="1.0" encoding="UTF-8"?>
{XML_UPL_HEADER}
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>{ROOT_GROUP_ID}</groupId>
  <artifactId>{ROOT_ARTIFACT_ID}</artifactId>
  <version>{ROOT_VERSION}</version>
  <packaging>pom</packaging>

  <modules>
{module_xml}
  </modules>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>{java_version}</maven.compiler.source>
    <maven.compiler.target>{java_version}</maven.compiler.target>
    <maven.deploy.skip>true</maven.deploy.skip>
    <maven.compiler-plugin.version>3.14.1</maven.compiler-plugin.version>
    <maven.enforcer-plugin.version>3.6.1</maven.enforcer-plugin.version>
    <graalvm.version>{graalvm_version}</graalvm.version>
  </properties>

  <dependencyManagement>
    <dependencies>
{dependency_xmls}
    </dependencies>
  </dependencyManagement>

  <build>
    <sourceDirectory>{source_dir}</sourceDirectory>
    <resources>
      <resource>
        <directory>{source_dir}</directory>
        <excludes>
          <exclude>**/*.java</exclude>
        </excludes>
      </resource>
    </resources>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <version>${{maven.compiler-plugin.version}}</version>
          <configuration>
            <source>${{maven.compiler.source}}</source>
            <target>${{maven.compiler.target}}</target>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-enforcer-plugin</artifactId>
          <version>${{maven.enforcer-plugin.version}}</version>
          <executions>
            <execution>
              <id>enforce-maven-version</id>
              <goals>
                <goal>enforce</goal>
              </goals>
              <configuration>
                <rules>
                  <requireMavenVersion>
                    <version>[3.9.9,)</version>
                  </requireMavenVersion>
                </rules>
              </configuration>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
'''


def leaf_pom(project_name: str, project, suite: dict) -> str:
    deps = project_dependencies(project, suite)
    deps_xml = ""
    if deps:
        dep_entries = "\n".join(dependency_xml(dep, indent="    ") for dep in deps)
        deps_xml = f'''

  <dependencies>
{dep_entries}
  </dependencies>'''

    build_xml = ""
    if getattr(project, "jniHeaders", None):
        build_xml = '''

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <proc>full</proc>
          <generatedSourcesDirectory>${project.build.directory}/generated-sources/annotations</generatedSourcesDirectory>
          <compilerArgs>
            <arg>-h</arg>
            <arg>${project.build.directory}/jni_gen</arg>
          </compilerArgs>
        </configuration>
      </plugin>
    </plugins>
  </build>'''

    return f'''<?xml version="1.0" encoding="UTF-8"?>
{XML_UPL_HEADER}
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>{ROOT_GROUP_ID}</groupId>
    <artifactId>{ROOT_ARTIFACT_ID}</artifactId>
    <version>{ROOT_VERSION}</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>

  <artifactId>{project_name}</artifactId>
  <packaging>jar</packaging>{deps_xml}{build_xml}
</project>
'''


def write_if_changed(path: Path, content: str) -> bool:
    old = path.read_text(encoding="utf-8") if path.exists() else None
    if old == content:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return True


def normalize_repo_path(path: str) -> str:
    candidate = Path(path)
    if candidate.is_absolute():
        try:
            candidate = candidate.relative_to(ROOT)
        except ValueError:
            return candidate.as_posix()
    return candidate.as_posix().lstrip("./")


def should_regenerate(args: list[str]) -> bool:
    if not args:
        return True
    changed = {normalize_repo_path(arg) for arg in args}
    return SUITE_PATH.as_posix() in changed or GENERATOR_PATH.as_posix() in changed


def main(args: list[str] | None = None, mx_suite=None) -> int:
    if not should_regenerate(sys.argv[1:] if args is None else args):
        return 0

    mx_suite = mx.suite("graalpython") if mx_suite is None else mx_suite
    suite = load_suite(mx_suite)
    projects = load_projects(mx_suite)
    default_graalvm_version, current_graalvm_version = graalvm_versions(mx_suite)
    outputs = {
        ROOT / GENERATOR_PATH: update_generator_versions(
            (ROOT / GENERATOR_PATH).read_text(encoding="utf-8"),
            default_graalvm_version,
            current_graalvm_version,
        ),
        ROOT / "pom.xml": root_pom(suite, projects, default_graalvm_version),
    }
    for project_name in FACADE_PROJECTS:
        project = projects.get(project_name)
        if project is None:
            raise SystemExit(f"{project_name}: not found in suite.py projects")
        outputs[ROOT / project_path(project_name, project) / "pom.xml"] = leaf_pom(project_name, project, suite)

    changed = [path for path, content in outputs.items() if write_if_changed(path, content)]
    if changed:
        print("warning: regenerated Maven IDE facade POMs from mx.graalpython/suite.py", file=sys.stderr)
        for path in changed:
            print(f"warning: updated {path.relative_to(ROOT)}", file=sys.stderr)
    return 0


def pominit(args: list[str]) -> int:
    return main(args, mx.suite("graalpython"))


if __name__ == "__main__":
    sys.exit(main())
