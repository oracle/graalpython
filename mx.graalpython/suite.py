# pylint: disable=anomalous-backslash-in-string
suite = {
    # --------------------------------------------------------------------------------------------------------------
    #
    #  METADATA
    #
    # --------------------------------------------------------------------------------------------------------------
    "mxversion": "5.311.0",
    "name": "graalpython",
    "versionConflictResolution": "latest",

    "version": "23.0.2",
    "release": False,
    "groupId": "org.graalvm.graalpython",
    "url": "http://www.graalvm.org/",

    "developer": {
        "name": "Truffle and Graal developers",
        "email": "graalvm-users@oss.oracle.com",
        "organization": "Graal",
        "organizationUrl": "http://www.graalvm.org/",
    },

    "scm": {
        "url": "https://github.com/graalvm/graalpython",
        "read": "https://github.com/graalvm/graalpython.git",
        "write": "git@github.com:graalvm/graalpython.git",
    },

    # --------------------------------------------------------------------------------------------------------------
    #
    #  DEPENDENCIES
    #
    # --------------------------------------------------------------------------------------------------------------
    "imports": {
        "suites": [
            {
                "name": "truffle",
                "versionFrom": "sulong",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "sdk",
                "version": "89d126af22fc68a233c79c90ff0dbb5b0c5ec2b7",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "tools",
                "version": "89d126af22fc68a233c79c90ff0dbb5b0c5ec2b7",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ],
            },
            {
                "name": "sulong",
                "version": "89d126af22fc68a233c79c90ff0dbb5b0c5ec2b7",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "regex",
                "version": "89d126af22fc68a233c79c90ff0dbb5b0c5ec2b7",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
        ],
    },

    # --------------------------------------------------------------------------------------------------------------
    #
    #  REPOS
    #
    # --------------------------------------------------------------------------------------------------------------
    "repositories": {
        "python-public-snapshots": {
            "url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
            "licenses": ["GPLv2-CPE", "UPL", "BSD-new", "MIT"]
        },
        "python-local-snapshots": {
            "url": "http://localhost",
            "licenses": ["GPLv2-CPE", "UPL", "BSD-new", "MIT"]
        },
    },

    "defaultLicense": "UPL",

    # --------------------------------------------------------------------------------------------------------------
    #
    #  LIBRARIES
    #
    # --------------------------------------------------------------------------------------------------------------
    "libraries": {
        "SETUPTOOLS": {
            "urls": [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/setuptools-40.6.3.zip",
            ],
            "sha1": "7a5960b8062ddbf0c0e79f806e23785d55fec3c8",
        },
        "XZ-1.8": {
            "sha1": "c4f7d054303948eb6a4066194253886c8af07128",
            "maven": {
                "groupId": "org.tukaani",
                "artifactId": "xz",
                "version": "1.8",
            },
        },
        "XZ-5.2.6": {
            "urls": [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/xz-5.2.6.tar.gz",
            ],
            "packedResource": True,
            "sha1": "1b1acd2e62203a7abceee6f573f1a96cdf5fbc8f",
        },
        "BOUNCYCASTLE-PROVIDER": {
            "sha1": "46a080368d38b428d237a59458f9bc915222894d",
            "sourceSha1": "c092c4f5af620ea5fd40a48d844c556826bebb63",
            "maven": {
              "groupId": "org.bouncycastle",
              "artifactId": "bcprov-jdk15on",
              "version": "1.68",
            },
        },
        "BOUNCYCASTLE-PKIX": {
            "sha1": "81da950604ff0b2652348cbd2b48fde46ced9867",
            "sourceSha1": "a5407438fed5d271f129d85e3f92cc027fa246a9",
            "maven": {
                "groupId": "org.bouncycastle",
                "artifactId": "bcpkix-jdk15on",
                "version": "1.68",
            },
        },
        "BZIP2": {
            "urls": [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/graalpython/bzip2-1.0.8.tar.gz",
            ],
            "packedResource": True,
            "sha1": "bf7badf7e248e0ecf465d33c2f5aeec774209227",
        },
        "NETBEANS-LIB-PROFILER": {
            "moduleName": "org.netbeans.modules.org-netbeans-lib-profiler",
            "digest": "sha512:1de81a0340c0266b41ba774600346ac977910663016a0afa22859cf1eb9d9e507de4f66e3f51d5bd9575b1d1f083765ecb9b30c4d43adb201f68b83257e8a17d",
            "sourceDigest": "sha512:92c50b8832e3a9afc93f9eaacdfc79cdf2487a74a9f5cf93c54bed50e904ef70ac6504018558d7183f074132c37fe57b21bc1e662a71c74c4201b75cdc5f8947",
            "maven": {
              "groupId": "org.netbeans.modules",
              "artifactId": "org-netbeans-lib-profiler",
              "version": "RELEASE120-1",
            },
        },
    },

    # --------------------------------------------------------------------------------------------------------------
    #
    #  PROJECTS
    #
    # --------------------------------------------------------------------------------------------------------------
    "externalProjects": {
        "lib.python": {
            "type": "python",
            "path": 'graalpython/lib-python',
            "source": [
                "3"
            ],
        },

        "lib.graalpython": {
            "type": "python",
            "path": 'graalpython/lib-graalpython',
            "source": [],
        },

        "util.scripts": {
            "type": "python",
            "path": 'scripts',
            "source": [],
        },

        "docs.user": {
            "type": "python",
            "path": 'docs/user',
            "source": [],
        },

        "docs.contributor": {
            "type": "python",
            "path": 'docs/contributor',
            "source": [],
        },

        "com.oracle.graal.python.cext": {
            "type": "python",
            "path": "graalpython/com.oracle.graal.python.cext",
            "source": [
                "include",
                "src",
                "modules"
            ],
        },

        "com.oracle.graal.python.jni": {
            "type": "python",
            "path": "graalpython/com.oracle.graal.python.jni",
            "source": [
                "include",
                "src",
            ],
        },

        "com.oracle.graal.python.frozen": {
            "type": "python",
            "path": "graalpython/com.oracle.graal.python.frozen",
        },

        "com.oracle.graal.python.pegparser.generator": {
            "type": "python",
            "path": "graalpython/com.oracle.graal.python.pegparser.generator",
            "source": [],
        },

        "graalpy-virtualenv": {
            "type": "python",
            "path": "graalpy_virtualenv",
            "source": [],
        }
    },

    "projects": {
        "com.oracle.graal.python.pegparser": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "javaCompliance": "17+",
            "dependencies": [
                "truffle:ICU4J",
            ],
            "buildDependencies": [
                "com.oracle.graal.python.pegparser.generator",
            ],
        },

        "com.oracle.graal.python.pegparser.test": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "testProject": True,
            "javaCompliance": "17+",
            "dependencies": [
                "com.oracle.graal.python.pegparser",
                "mx:JUNIT",
            ],
        },

        "com.oracle.graal.python.pegparser.generator": {
            "subDir": "graalpython",
            "class" : "CMakeNinjaProject",
            "ninja_targets" : [
                "all",
            ],
            "results" : [
                "Parser.java",
                "Python.asdl.stamp",
            ]
        },

        "com.oracle.graal.python.shell": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "requires": [
                "java.management",
            ],
            "jacoco": "include",
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.graal.python",
        },

        "com.oracle.graal.python.annotations": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "jacoco": "include",
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.graal.python",
        },

        # GRAALPYTHON-PROCESSOR
        "com.oracle.graal.python.processor": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.graal.python.annotations"
            ],
            "requires": [
                "java.compiler",
            ],
            "jacoco": "exclude",
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.graal.python",
        },

        # FROZEN MODULES
        "com.oracle.graal.python.frozen": {
            "subDir": "graalpython",
            "vpath": True,
            "type": "GraalpythonProject",
            "args": [
                "<path:com.oracle.graal.python.frozen>/freeze_modules.py",
                "--python-lib",
                "<suite:graalpython>/graalpython/lib-python/3",
                "--binary-dir",
                "<output_root:com.oracle.graal.python.frozen>/com/oracle/graal/python/builtins/objects/module/",
                "--sources-dir",
                "<path:com.oracle.graal.python>/src/com/oracle/graal/python/builtins/objects/module/",
            ],
            "platformDependent": False,
            "buildDependencies": [
                # a bit ugly, we need the same dist dependencies as the full GRAALPYTHON dist + python-lib
                "com.oracle.graal.python",
                "GRAALPYTHON-LAUNCHER",
                "GRAALPYTHON_JNI",
                "truffle:TRUFFLE_API",
                "tools:TRUFFLE_COVERAGE",
                "tools:TRUFFLE_PROFILER",
                "regex:TREGEX",
                "sdk:GRAAL_SDK",
                "sulong:SULONG_API",
                "sulong:SULONG_NATIVE",  # this is actually just a runtime dependency
                "GRAALPYTHON_PYTHON_LIB",
            ],
        },

        # GRAALPYTHON
        "com.oracle.graal.python": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "jniHeaders": True,
            "dependencies": [
                "com.oracle.graal.python.annotations",
                "com.oracle.graal.python.pegparser",
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
                "tools:TRUFFLE_COVERAGE",
                "tools:TRUFFLE_PROFILER",
                "sdk:GRAAL_SDK",
                "sulong:SULONG_API",
                "XZ-1.8",
                "truffle:ICU4J",
                "truffle:ICU4J-CHARSET",
                "regex:TREGEX",
                "BOUNCYCASTLE-PROVIDER",
                "BOUNCYCASTLE-PKIX",
            ],
            "requires": [
                "java.management",
                "jdk.management",
                "jdk.unsupported",
                "jdk.security.auth",
            ],
            "jacoco": "include",
            "javaCompliance": "17+",
            "checkstyleVersion": "8.36.1",
            "annotationProcessors": [
                "GRAALPYTHON_PROCESSOR",
                "truffle:TRUFFLE_DSL_PROCESSOR"
            ],
            "workingSets": "Truffle,Python",
            "spotbugsIgnoresGenerated": True,
        },

        # GRAALPYTHON TEST
        "com.oracle.graal.python.test": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.graal.python.shell",
                "com.oracle.graal.python",
                "truffle:TRUFFLE_TCK",
                "mx:JUNIT",
                "NETBEANS-LIB-PROFILER",
            ],
            "requires": [
                "java.management",
                "jdk.management",
                "jdk.unsupported",
            ],
            "jacoco": "exclude",
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "17+",
            "annotationProcessors": [
                "GRAALPYTHON_PROCESSOR",
                "truffle:TRUFFLE_DSL_PROCESSOR"
            ],
            "workingSets": "Truffle,Python",
            "testProject": True,
            "javaProperties" : {
                "test.graalpython.home" : "<suite:graalpython>/graalpython"
             },
        },

        # GRAALPYTHON BENCH
        "com.oracle.graal.python.benchmarks": {
            "subDir": "graalpython",
            "sourceDirs": ["java"],
            "dependencies": [
                "com.oracle.graal.python",
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
                "mx:JMH_1_21"
            ],
            "requires": [
                "java.logging",
            ],
            "jacoco": "exclude",
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "17+",
            "annotationProcessors" : ["mx:JMH_1_21"],
            "workingSets": "Truffle,Python",
            "spotbugsIgnoresGenerated" : True,
            "testProject" : True,
        },

        "com.oracle.graal.python.tck": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:POLYGLOT_TCK",
                "mx:JUNIT"
            ],
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "17+",
            "workingSets": "Truffle,Python",
            "testProject": True,
        },

        "com.oracle.graal.python.cext": {
            "subDir": "graalpython",
            "vpath": True,
            "type": "GraalpythonCAPIProject",
            "platformDependent": False,
            "os_arch": {
                "windows": {
                    "<others>": {
                        "args": [
                            "<src_dir:com.oracle.graal.python.cext>/setup.py",
                            "<output_root:com.oracle.graal.python.cext>",
                        ],
                    },
                },
                "<others>": {
                    "<others>": {
                        "args": [
                            "--python.CAPI=/dev/null",
                            "<src_dir:com.oracle.graal.python.cext>/setup.py",
                            "<output_root:com.oracle.graal.python.cext>",
                        ],
                    },
                },
            },
            "buildDependencies": [
                "GRAALPYTHON",
                "sulong:SULONG_HOME",
                "sulong:SULONG_LEGACY",
                "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
                "XZ-5.2.6",
                "BZIP2",
            ],
            "buildEnv": {
                "TRUFFLE_H_INC": "<path:SULONG_LEGACY>/include",
                "ARCH": "<arch>",
                "OS": "<os>",
                "XZ-5.2.6": "<path:XZ-5.2.6>",
                "BZIP2": "<path:BZIP2>",
            },
        },

        "com.oracle.graal.python.jni": {
            "dir": "graalpython/com.oracle.graal.python.jni",
            "native": "shared_lib",
            "deliverable": "pythonjni",
            "buildDependencies": [
                "com.oracle.graal.python", # for the generated JNI header file
            ],
            "use_jdk_headers": True, # the generated JNI header includes jni.h
            "cflags": [
                "-DHPY_UNIVERSAL_ABI", "-DNDEBUG",
                "-g", "-O3", "-Werror",
                "-I\"<path:com.oracle.graal.python.cext>/include\"",
                "-I\"<path:com.oracle.graal.python.cext>/include/internal\"",
                "-I\"<path:com.oracle.graal.python.cext>/src\"",
                "-I\"<path:com.oracle.graal.python.cext>/hpy\""
            ],
            "ldlibs": [
                "-lm"
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        # "/Z7", "/O2", "/WX", # cflags to replace -g -O3 -Werror
                        "defaultBuild": False,
                    },
                },
                "<others>": {
                    "<others>": {
                        "defaultBuild" : True,
                    },
                },
            },
        },

        "python-lib": {
            "class": "ArchiveProject",
            "outputDir": "graalpython/lib-python/3",
            "prefix": "",
            "ignorePatterns": [
                ".pyc",
                "\/__pycache__\/",
                "\/test\/",
                "\/tests\/",
                "\/idle_test\/",
            ],
            "license": ["PSF-License"],
        },

        "python-test-support-lib": {
            "class": "ArchiveProject",
            "outputDir": "graalpython/lib-python/3/test/support",
            "prefix": "test/support",
            "ignorePatterns": [],
            "license": ["PSF-License"],
        },
    },

    "licenses": {
        "PSF-License": {
            "name": "Python Software Foundation License",
            "url": "https://docs.python.org/3/license.html",
        },
        "UPL": {
            "name": "Universal Permissive License, Version 1.0",
            "url": "http://opensource.org/licenses/UPL",
        },
    },

    # --------------------------------------------------------------------------------------------------------------
    #
    #  DISTRIBUTIONS
    #
    # --------------------------------------------------------------------------------------------------------------
    "distributions": {
        "GRAALPYTHON-LAUNCHER": {
            "dependencies": [
                "com.oracle.graal.python.shell",
            ],
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "description": "GraalPython launcher",
        },

        "GRAALPYTHON_JNI" : {
            "native": True,
            "platformDependent": True,
            "platforms": [
                "linux-amd64",
                "linux-aarch64",
                "darwin-amd64",
                "windows-amd64",
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "layout": {
                            "./": "file:graalpython/com.oracle.graal.python.jni/JNI-WINDOWS-README.md",
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "dependencies": [
                            "com.oracle.graal.python.jni",
                        ],
                        "layout": {
                            "./": {
                                "source_type": "dependency",
                                "dependency": "com.oracle.graal.python.jni",
                                "path": "*",
                                "exclude": ["JNI-WINDOWS-README.md"],
                            },
                        },
                    },
                },
            },
            "description": "Contains the native library needed by HPy JNI backend.",
            "maven": True,
        },

        "GRAALPYTHON_PEGPARSER": {
            "dependencies": [
                "com.oracle.graal.python.pegparser",
            ],
            "exclude": [
                "truffle:ICU4J",
            ],
            "description": "GraalPython PEG parser",
            "internal": True,
        },

        "GRAALPYTHON": {
            "dependencies": [
                "com.oracle.graal.python",
                "com.oracle.graal.python.frozen",
            ],
            "distDependencies": [
                "GRAALPYTHON-LAUNCHER",
                "GRAALPYTHON_JNI",
                "truffle:TRUFFLE_API",
                "tools:TRUFFLE_COVERAGE",
                "tools:TRUFFLE_PROFILER",
                "regex:TREGEX",
                "sdk:GRAAL_SDK",
                "sulong:SULONG_API",
                "sulong:SULONG_NATIVE",  # this is actually just a runtime dependency
            ],
            "exclude": [
                "BOUNCYCASTLE-PROVIDER",
                "BOUNCYCASTLE-PKIX",
                "XZ-1.8",
                "truffle:ICU4J",
                "truffle:ICU4J-CHARSET",
            ],
            "javaProperties": {
                "python.jni.library": "<lib:pythonjni>"
            },
            "description": "GraalPython engine",
        },

        "GRAALPYTHON_PROCESSOR": {
            "dependencies": [
                "com.oracle.graal.python.processor",
            ],
            "description": "GraalPython Java annotations processor",
            "overlaps": ["GRAALPYTHON"], # sharing the annotations
        },

        "GRAALPYTHON_PYTHON_LIB": {
            "native": True, # makes this a tar archive
            "relpath": True, # relpath for tar archives is False but probably should be True
            "dependencies": ["python-lib", "python-test-support-lib"],
            "description": "Python 3 lib files",
            "maven": False,
        },

        "GRAALPYTHON_UNIT_TESTS": {
            "description": "unit tests",
            "dependencies": [
                "com.oracle.graal.python.test",
                "com.oracle.graal.python.pegparser.test",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON-LAUNCHER",
                "truffle:TRUFFLE_TCK",
            ],
            "testDistribution": True,
        },

        "GRAALPYTHON_BENCH" : {
            "description": "java python interop benchmarks",
            "dependencies" : ["com.oracle.graal.python.benchmarks"],
            "exclude": ["mx:JMH_1_21"],
            "distDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON-LAUNCHER",
                "sdk:GRAAL_SDK",
            ],
            "testDistribution" : True,
            "maven": False,
        },

        "GRAALPYTHON_TCK": {
            "description": "unit tests",
            "dependencies": [
                "com.oracle.graal.python.tck",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "sdk:POLYGLOT_TCK",
            ],
            "testDistribution": True,
        },

        "GRAALPYTHON_GRAALVM_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "fileListPurpose": 'native-image-resources',
            "description": "GraalVM Python support distribution for the GraalVM",
            "distDependencies": [
                "GRAALPYTHON_JNI",
            ],
            "dependencies": [
                "com.oracle.graal.python.cext",
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "layout": {
                            "./": [
                                "file:mx.graalpython/native-image.properties",
                            ],
                            "./Lib/": [
                                "extracted-dependency:graalpython:GRAALPYTHON_PYTHON_LIB",
                            ],
                            "./lib-graalpython/": [
                                "file:graalpython/lib-graalpython/*",
                                "extracted-dependency:GRAALPYTHON_JNI/*",
                                {
                                    "source_type": "dependency",
                                    "dependency": "graalpython:com.oracle.graal.python.cext",
                                    "path": "*",
                                },
                            ],
                            "./lib-graalpython/modules/graalpy_virtualenv": [
                                "file:graalpy_virtualenv/graalpy_virtualenv",
                            ],
                            "./Include/": [
                                "file:graalpython/com.oracle.graal.python.cext/include/*",
                            ],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "./": [
                                "file:mx.graalpython/native-image.properties",
                            ],
                            "./lib/python<py_ver:major_minor_nodot>/": [
                                "extracted-dependency:graalpython:GRAALPYTHON_PYTHON_LIB",
                            ],
                            "./lib/graalpy<graal_ver:major_minor_nodot>/": [
                                "file:graalpython/lib-graalpython/*",
                                "extracted-dependency:GRAALPYTHON_JNI/*",
                                {
                                    "source_type": "dependency",
                                    "dependency": "graalpython:com.oracle.graal.python.cext",
                                    "path": "*",
                                    "exclude": ["CEXT-WINDOWS-README.md"],
                                },
                            ],
                            "./lib/graalpy<graal_ver:major_minor_nodot>/modules/graalpy_virtualenv": [
                                "file:graalpy_virtualenv/graalpy_virtualenv",
                            ],
                            "./include/python<py_ver:major_minor_nodot>/": [
                                "file:graalpython/com.oracle.graal.python.cext/include/*",
                            ],
                        },
                    },
                },
            },
            "maven": False,
        },

        "GRAALPY_VIRTUALENV": {
            "native": True,
            "maven": False,
            "description": "graalpy-virtualenv plugin sources usable to be installed into other interpreters",
            "layout": {
                "graalpy_virtualenv": "file:graalpy_virtualenv",
            },
        },

        "GRAALPYTHON_GRAALVM_DOCS": {
            "native": True,
            "description": "GraalVM Python documentation files for the GraalVM",
            "layout": {
                "README_GRAALPY.md": "file:README.md",
                "./": "file:docs",
            },
            "maven": False,
        },

        "GRAALPYTHON_GRAALVM_LICENSES": {
            "native": True,
            "platformDependent": True,
            "fileListPurpose": 'native-image-resources',
            "description": "GraalVM Python support distribution for the GraalVM license files",
            "layout": {
                "LICENSE_GRAALPY.txt": "file:LICENSE",
                "THIRD_PARTY_LICENSE_GRAALPY.txt": "file:THIRD_PARTY_LICENSE.txt",
            },
            "maven": False,
        },
    },
}
