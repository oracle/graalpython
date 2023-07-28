# pylint: disable=anomalous-backslash-in-string
suite = {
    # --------------------------------------------------------------------------------------------------------------
    #
    #  METADATA
    #
    # --------------------------------------------------------------------------------------------------------------
    "mxversion": "6.34.0",
    "name": "graalpython",
    "versionConflictResolution": "latest",

    "version": "23.1.0",
    "graalpython:pythonVersion": "3.10.8",
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
                "version": "eb44a8bb6d3866f22bfb48069bf3f2aa1d04da87",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "tools",
                "version": "eb44a8bb6d3866f22bfb48069bf3f2aa1d04da87",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ],
            },
            {
                "name": "sulong",
                "version": "eb44a8bb6d3866f22bfb48069bf3f2aa1d04da87",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "regex",
                "version": "eb44a8bb6d3866f22bfb48069bf3f2aa1d04da87",
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
            "digest": "sha512:a4362db234d4e83683e90f5baf90c82107450cc4404acab96e3fab14b8a3d4588a19722171d32f27d18463682a6994cad9af0b1065c954e3a77ea7bdcf586bac",
            "maven": {
                "groupId": "org.tukaani",
                "artifactId": "xz",
                "version": "1.9",
            },
            "moduleName": "org.tukaani.xz",
        },
        "XZ-5.2.6": {
            "urls": [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/xz-5.2.6.tar.gz",
            ],
            "packedResource": True,
            "digest": "sha512:090958dd6c202c989746686094c86707ad4ae835026640080fc0a9d0fad699821b7d5cb3a67e6700661a0938818ba153662366f89ab8ec47e0bae4a3fe9b1961",
        },
        "BOUNCYCASTLE-PROVIDER": {
            "digest": "sha512:b2d267e5d198a4c51318a53fa3cac5a769521f217ea69dd40c3d2372ea3a00d1245585f8916cad77c9a11d561aed04840c73c6095d785b598a385561f99af8ac",
            "sourceDigest": "sha512:3ff983178dbf03aa6265b28c1bf79a813e53a1403188ffd9688347f3bcf0269b8587b071913fc0cfec8d2e81126fbafa7977143d4d920f8cbdbd2ead3a2c92bd",
            "maven": {
              "groupId": "org.bouncycastle",
              "artifactId": "bcprov-jdk15on",
              "version": "1.68",
            },
            "moduleName": "org.bouncycastle.provider",
        },
        "BOUNCYCASTLE-PKIX": {
            "digest": "sha512:1d18b43537370e0c7eb29332c9f9a4f0162f9bf68a79d6df3fb76080a8b96e1e378537e5a7aff481f2e1390cf45558b18679a1557ffd3c3b670b7ba1d625e8dd",
            "sourceDigest": "sha512:f48cbec79f4999a009a77a955587259d3bd6a27563d572fea1dcf101e12474e3de3395d9c0b10702233b190f36b8282c9d8115fa7815cfa67eb76f81cd8b5c15",
            "maven": {
                "groupId": "org.bouncycastle",
                "artifactId": "bcpkix-jdk15on",
                "version": "1.68",
            },
            "moduleName": "org.bouncycastle.pkix",
        },
        "BZIP2": {
            "urls": [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/graalpython/bzip2-1.0.8.tar.gz",
            ],
            "packedResource": True,
            "digest": "sha512:083f5e675d73f3233c7930ebe20425a533feedeaaa9d8cc86831312a6581cefbe6ed0d08d2fa89be81082f2a5abdabca8b3c080bf97218a1bd59dc118a30b9f3",
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

        "com.oracle.graal.python.hpy.llvm": {
            "type": "python",
            "path": "graalpython/com.oracle.graal.python.hpy.llvm",
            "source": [
                "include",
                "src",
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

        "python-liblzma": {
            "type": "python",
            "path": "graalpython/python-liblzma",
            "source": [
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
                "truffle:TRUFFLE_ICU4J",
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
            "class": "CMakeNinjaProject",
            "ninja_targets": [
                "all",
            ],
            "results": [
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
                "sdk:JLINE3",
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
                "truffle:TRUFFLE_API",
                "tools:TRUFFLE_PROFILER",
                "regex:TREGEX",
                "sdk:GRAAL_SDK",
                "sulong:SULONG_API",
                "sulong:SULONG_NATIVE",  # this is actually just a runtime dependency
            ],
        },

        "com.oracle.graal.python.resources": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "jacoco": "include",
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.graal.python",
            "annotationProcessors": [
                "truffle:TRUFFLE_DSL_PROCESSOR"
            ],
            "workingSets": "Truffle,Python",
            "spotbugsIgnoresGenerated": True,
        },

        # GRAALPYTHON
        "com.oracle.graal.python": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "jniHeaders": True,
            "dependencies": [
                "com.oracle.graal.python.annotations",
                "com.oracle.graal.python.pegparser",
                "com.oracle.graal.python.resources",
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
                "tools:TRUFFLE_PROFILER",
                "sdk:GRAAL_SDK",
                "sulong:SULONG_API",
                "XZ-1.8",
                "truffle:TRUFFLE_ICU4J",
                "regex:TREGEX",
                "BOUNCYCASTLE-PROVIDER",
                "BOUNCYCASTLE-PKIX",
            ],
            "requires": [
                "java.logging",
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
            "javaProperties": {
                "test.graalpython.home": "<suite:graalpython>/graalpython"
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
            "annotationProcessors": ["mx:JMH_1_21"],
            "workingSets": "Truffle,Python",
            "spotbugsIgnoresGenerated": True,
            "testProject": True,
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

        "python-libbz2": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "max_jobs": "4",
            "vpath": True,
            "ninja_targets": ["<lib:bz2support>"],
            "ninja_install_targets": ["install"],
            "results": [
                "bin/<lib:bz2support>",
            ],
            "cmakeConfig": {
                "BZIP2_ROOT": "<path:BZIP2>",
                "BZIP2_VERSION_MAJOR": "1",
                "BZIP2_VERSION_MINOR": "0",
                "BZIP2_VERSION_PATCH": "8",
            },
            "os_arch": {
                "windows": {
                    "<others>": {
                        "defaultBuild": False,
                    },
                },
                "<others>": {
                    "<others>": {
                        "defaultBuild": True,
                    },
                },
            },
            "buildDependencies": [
                "sulong:SULONG_HOME",
                "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
                "BZIP2",
            ],
            "buildEnv": {
                "ARCH": "<arch>",
                "OS": "<os>",
            },
        },

        "python-liblzma": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "max_jobs": "8",
            "vpath": True,
            "ninja_targets": ["<lib:lzmasupport>"],
            "ninja_install_targets": ["install"],
            "results": [
                "bin/<lib:lzmasupport>",
            ],
            "cmakeConfig": {
                "XZ_SRC": "<path:XZ-5.2.6>",
                "XZ_VERSION_MAJOR": "5",
                "XZ_VERSION_MINOR": "2",
                "XZ_VERSION_PATCH": "6",
            },
            "os_arch": {
                "windows": {
                    "<others>": {
                        "defaultBuild": False,
                    },
                },
                "<others>": {
                    "<others>": {
                        "defaultBuild": True,
                    },
                },
            },
            "buildDependencies": [
                "XZ-5.2.6",
            ],
        },

        "com.oracle.graal.python.cext": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "toolchain": "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
            "max_jobs": "8",
            "vpath": True,
            "ninja_targets": ["all"],
            "ninja_install_targets": ["install"],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "cmakeConfig": {
                            "GRAALVM_LLVM_LIB_DIR": "<path:SULONG_NATIVE_HOME>/native/lib",
                            "TRUFFLE_H_INC": "<path:SULONG_LEGACY>/include",
                            "TRUFFLE_NFI_H_INC": "<path:com.oracle.truffle.nfi.native>/include",
                            "CMAKE_C_COMPILER": "<toolchainGetToolPath:native,CC>",
                            "LLVM_MODE": "native",
                            "GRAALPY_EXT": "<graalpy_ext:native>",
                        },
                        "results": [
                            "bin/<lib:python-native>",
                            "bin/python-native.lib",
                            "bin/modules/_sqlite3<graalpy_ext:native>",
                            "bin/modules/_cpython_sre<graalpy_ext:native>",
                            "bin/modules/_cpython_unicodedata<graalpy_ext:native>",
                            "bin/modules/_cpython_struct<graalpy_ext:native>",
                        ],
                    },
                },
                "<others>": {
                    "<others>": {
                        "cmakeConfig": {
                            "TRUFFLE_H_INC": "<path:SULONG_LEGACY>/include",
                            "TRUFFLE_NFI_H_INC": "<path:com.oracle.truffle.nfi.native>/include",
                            "CMAKE_C_COMPILER": "<toolchainGetToolPath:native,CC>",
                            "LLVM_MODE": "native",
                            "GRAALPY_EXT": "<graalpy_ext:native>",
                        },
                        "results": [
                            "bin/<lib:python-native>",
                            "bin/modules/_sqlite3<graalpy_ext:native>",
                            "bin/modules/_cpython_sre<graalpy_ext:native>",
                            "bin/modules/_cpython_unicodedata<graalpy_ext:native>",
                            "bin/modules/_cpython_struct<graalpy_ext:native>",
                            "bin/modules/_testcapi<graalpy_ext:native>",
                            "bin/modules/_testmultiphase<graalpy_ext:native>",
                            "bin/modules/_ctypes_test<graalpy_ext:native>",
                            "bin/modules/pyexpat<graalpy_ext:native>",
                        ],
                    },
                },
            },
            "buildDependencies": [
                "sulong:SULONG_HOME",
                "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
                "sulong:SULONG_LEGACY",
            ],
        },

        "com.oracle.graal.python.hpy.llvm": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "toolchain": "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
            "max_jobs": "8",
            "vpath": True,
            "ninja_targets": [
                "<lib:hpy-native>",
            ],
            "ninja_install_targets": ["install"],
            "results": [
                "bin/<lib:hpy-native>",
            ],
            "buildDependencies": [
                "sulong:SULONG_HOME",
                "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
                "sulong:SULONG_LEGACY",
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "cmakeConfig": {
                            "GRAALVM_LLVM_LIB_DIR": "<path:SULONG_NATIVE_HOME>/native/lib",
                            "LLVM_MODE": "native",
                            "GRAALVM_HPY_INCLUDE_DIR": "<path:com.oracle.graal.python.hpy.llvm>/include",
                            "GRAALVM_PYTHON_INCLUDE_DIR": "<path:com.oracle.graal.python.cext>/include",
                            "TRUFFLE_H_INC": "<path:SULONG_LEGACY>/include",
                            "CMAKE_C_COMPILER": "<toolchainGetToolPath:native,CC>",
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "cmakeConfig": {
                            "LLVM_MODE": "native",
                            "GRAALVM_HPY_INCLUDE_DIR": "<path:com.oracle.graal.python.hpy.llvm>/include",
                            "GRAALVM_PYTHON_INCLUDE_DIR": "<path:com.oracle.graal.python.cext>/include",
                            "TRUFFLE_H_INC": "<path:SULONG_LEGACY>/include",
                            "CMAKE_C_COMPILER": "<toolchainGetToolPath:native,CC>",
                        },
                    },
                },
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
            "ldlibs": [
                "-lm"
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "cflags": [
                            "-DHPY_UNIVERSAL_ABI", "-DNDEBUG", "-DMS_WINDOWS",
                            # cflags equivalent to -g -O3 -Wall (/WX would be -Werror)
                            "-D_CRT_SECURE_NO_WARNINGS", "/Z7", "/O2", "/W3",
                            "-I\"<path:com.oracle.graal.python.cext>/include\"",
                            "-I\"<path:com.oracle.graal.python.cext>/include/internal\"",
                            "-I\"<path:com.oracle.graal.python.cext>/src\"",
                            "-I\"<path:com.oracle.graal.python.hpy.llvm>/include\"",
                            "-I\"<path:com.oracle.graal.python.hpy.llvm>/src\"",
                            "-I\"<path:com.oracle.truffle.nfi.native>/include\""
                        ],
                    },
                },
                "<others>": {
                    "<others>": {
                        "cflags": [
                            "-DHPY_UNIVERSAL_ABI", "-DNDEBUG",
                            "-O3", "-Werror", "-fPIC",
                            "-I\"<path:com.oracle.graal.python.cext>/include\"",
                            "-I\"<path:com.oracle.graal.python.cext>/include/internal\"",
                            "-I\"<path:com.oracle.graal.python.cext>/src\"",
                            "-I\"<path:com.oracle.graal.python.hpy.llvm>/include\"",
                            "-I\"<path:com.oracle.graal.python.hpy.llvm>/src\"",
                            "-I\"<path:com.oracle.truffle.nfi.native>/include\""
                        ],
                    },
                },
            },
        },

        "python-libzsupport": {
            "subDir": "graalpython",
            "native": "shared_lib",
            "deliverable": "zsupport",
            "buildDependencies": [],
            "cflags": [
                "-DNDEBUG", "-g", "-O3", "-Werror",
            ],
            "ldlibs": ["-lz"],
            "os_arch": {
                "windows": {
                    "<others>": {
                        # "/Z7", "/O2", "/WX", # cflags to replace -g -O3 -Werror
                        "defaultBuild": False,
                    },
                },
                "<others>": {
                    "<others>": {
                        "defaultBuild": True,
                    },
                },
            },
        },

        "python-libposix": {
            "subDir": "graalpython",
            "native": "shared_lib",
            "deliverable": "posix",
            "buildDependencies": [],
            "cflags": [
                "-DNDEBUG", "-g", "-O3", "-Wall", "-Werror",
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        # "/Z7", "/O2", "/WX", # cflags to replace -g -O3 -Werror
                        "defaultBuild": False,
                    },
                },
                "darwin": {
                    "<others>": {
                        "defaultBuild": True,
                    },
                },
                "<others>": {
                    "<others>": {
                        "ldlibs": ["-lutil", "-lcrypt"],
                        "defaultBuild": True,
                    },
                },
            },
        },

        "python-lib": {
            "class": "ArchiveProject",
            "outputDir": "graalpython/lib-python/3",
            "type": "dir",
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
        # We need the versions twice (even though that's silly), because
        # otherwise they will not get included in both the resources jar and
        # the language jar.
        "GRAALPYTHON_VERSIONS_RES": {
            "type": "dir",
            "layout": {"./graalpy_versions": ["string:<py_ver:binary><graal_ver:binary>"]},
        },
        "GRAALPYTHON_VERSIONS_MAIN": {
            "type": "dir",
            "layout": {"./graalpy_versions": ["string:<py_ver:binary><graal_ver:binary>"]},
        },

        "GRAALPYTHON-LAUNCHER": {
            "moduleInfo": {
                "name": "org.graalvm.py.launcher",
                "exports": [
                    "com.oracle.graal.python.shell to org.graalvm.py.enterprise.launcher",
                    "com.oracle.graal.python.shell to org.graalvm.launcher",
                ],
            },
            # "useModulePath": True,
            "dependencies": [
                "com.oracle.graal.python.shell",
            ],
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "exclude": ["sdk:JLINE3"],
            "description": "GraalPython launcher",
        },

        "GRAALPYTHON_NATIVE_LIBS": {
            "native": True,
            "platformDependent": True,
            "type": "dir",
            "os_arch": {
                "windows": {
                    "<others>": {
                        "buildDependencies": [
                            "com.oracle.graal.python.cext",
                            "com.oracle.graal.python.jni",
                            "com.oracle.graal.python.hpy.llvm"
                        ],
                        "layout": {
                            "./": [
                                "dependency:com.oracle.graal.python.jni/*",
                                "dependency:com.oracle.graal.python.cext/bin/*",
                                "dependency:com.oracle.graal.python.hpy.llvm/bin/*",
                            ]
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "buildDependencies": [
                            "com.oracle.graal.python.cext",
                            "com.oracle.graal.python.jni",
                            "python-libzsupport",
                            "python-libposix",
                            "python-libbz2",
                            "python-liblzma",
                            "com.oracle.graal.python.hpy.llvm"
                        ],
                        "layout": {
                            "./": [
                                "dependency:com.oracle.graal.python.jni/*",
                                "dependency:com.oracle.graal.python.cext/bin/*",
                                "dependency:python-libzsupport/*",
                                "dependency:python-libposix/*",
                                "dependency:com.oracle.graal.python.hpy.llvm/bin/*",
                                "dependency:python-libbz2/bin/*",
                                "dependency:python-liblzma/bin/*",
                            ]
                        },
                    },
                },
            },
            "description": "Contains the JNI native lib, the C API and support libs.",
            "maven": False,
        },

        "GRAALPYTHON_RESOURCES": {
            "platformDependent": False,
            "moduleInfo": {
                "name": "org.graalvm.py.resources",
                "exports": [
                    "com.oracle.graal.python.resources to org.graalvm.py",
                ],
            },
            "useModulePath": True,
            "dependencies": [
                "com.oracle.graal.python.resources",
                "GRAALPYTHON_VERSIONS_RES",
                "GRAALPYTHON_LIBPYTHON_RESOURCES",
                "GRAALPYTHON_LIBGRAALPY_RESOURCES",
                "GRAALPYTHON_INCLUDE_RESOURCES",
                "GRAALPYTHON_NATIVE_RESOURCES",
            ],
            "distDependencies": [
                "truffle:TRUFFLE_API",
            ],
            "requires": [
                "java.base",
            ],
            "maven": {
                "artifactId": "python-resources",
                "groupId": "org.graalvm.python",
            },
        },

        "GRAALPYTHON": {
            "moduleInfo": {
                "name": "org.graalvm.py",
                "exports": [
                    "com.oracle.graal.python.builtins to org.graalvm.py.enterprise",
                    "com.oracle.graal.python.builtins.objects to org.graalvm.py.enterprise",
                    "com.oracle.graal.python.builtins.objects.* to org.graalvm.py.enterprise",
                    "com.oracle.graal.python.lib to org.graalvm.py.enterprise",
                    "com.oracle.graal.python.nodes to org.graalvm.py.enterprise",
                    "com.oracle.graal.python.nodes.function to org.graalvm.py.enterprise",
                    "com.oracle.graal.python.nodes.function.builtins to org.graalvm.py.enterprise",
                    "com.oracle.graal.python.runtime.exception to org.graalvm.py.enterprise",
                    "com.oracle.graal.python.util to org.graalvm.py.enterprise",
                ],
                "uses": [
                    "com.oracle.graal.python.builtins.PythonBuiltins",
                ],
                "requires": [
                    "static org.graalvm.py.resources",
                ],
            },
            "useModulePath": True,
            "dependencies": [
                "GRAALPYTHON_VERSIONS_MAIN",
                "com.oracle.graal.python",
                "com.oracle.graal.python.frozen",
            ],
            "distDependencies": [
                "truffle:TRUFFLE_API",
                "tools:TRUFFLE_PROFILER",
                "regex:TREGEX",
                "sdk:GRAAL_SDK",
                "sulong:SULONG_API",
                "sulong:SULONG_NATIVE",  # this is actually just a runtime dependency
                "truffle:TRUFFLE_ICU4J",
                "GRAALPYTHON_RESOURCES", # overridden below to make this an optional dependency
            ],
            "requires": [
                "java.base",
                "java.logging",
                "java.management",
                "jdk.management",
                "jdk.unsupported",
                "jdk.security.auth",
            ],
            "exclude": [
                "BOUNCYCASTLE-PROVIDER",
                "BOUNCYCASTLE-PKIX",
                "XZ-1.8",
            ],
            "javaProperties": {
                "python.jni.library": "<lib:pythonjni>"
            },
            "description": "GraalPython engine",
            "maven": {
                "artifactId": "python-language",
                "groupId": "org.graalvm.python",
            },
        },

        "GRAALPYTHON_PROCESSOR": {
            "dependencies": [
                "com.oracle.graal.python.processor",
            ],
            "description": "GraalPython Java annotations processor",
            "overlaps": ["GRAALPYTHON"], # sharing the annotations
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
            "maven": False,
        },

        "GRAALPYTHON_BENCH": {
            "description": "java python interop benchmarks",
            "dependencies": ["com.oracle.graal.python.benchmarks"],
            "exclude": ["mx:JMH_1_21"],
            "distDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON-LAUNCHER",
                "sdk:GRAAL_SDK",
            ],
            "testDistribution": True,
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
            "maven": False,
        },

        # Now come the different resource projects. These all end up bundled
        # together in a Jar, but they each have different extraction targets
        # depending on the platform.

        # The Python standard library. On Windows the Java code will extract
        # this to "/Lib", on Unix to "/lib/python<py_ver:major_minor>/"
        "GRAALPYTHON_LIBPYTHON_RESOURCES": {
            "native": False,
            "platformDependent": False,
            "hashEntry":  "META-INF/resources/libpython.sha256",
            "fileListEntry": "META-INF/resources/libpython.files",
            "type": "dir",
            "description": "GraalVM Python lib-python resources",
            "layout": {
                "./META-INF/resources/libpython/": [
                    "dependency:graalpython:python-lib/*",
                    "dependency:graalpython:python-test-support-lib/*",
                ],
            },
            "maven": False,
        },

        # The GraalPy core library. On Windows the Java code will extract this
        # to "/lib-graalpython", on Unix to "/lib/graalpy<graal_ver:major_minor>/"
        "GRAALPYTHON_LIBGRAALPY_RESOURCES": {
            "native": False,
            "platformDependent": False,
            "hashEntry":  "META-INF/resources/libgraalpy.sha256",
            "fileListEntry": "META-INF/resources/libgraalpy.files",
            "type": "dir",
            "description": "GraalVM Python lib-graalpython resources",
            "buildDependencies": [
                "graalpy_virtualenv",
            ],
            "layout": {
                "./META-INF/resources/libgraalpy/": [
                    "file:graalpython/lib-graalpython/*",
                ],
                "./META-INF/resources/libgraalpy/modules/graalpy_virtualenv": [
                    "file:graalpy_virtualenv/graalpy_virtualenv",
                ],
            },
            "maven": False,
        },

        # The Python and HPy headers. These go to "/include" on all platforms.
        "GRAALPYTHON_INCLUDE_RESOURCES": {
            "native": False,
            "platformDependent": False,
            "hashEntry":  "META-INF/resources/include.sha256",
            "fileListEntry": "META-INF/resources/include.files",
            "type": "dir",
            "description": "GraalVM Python header resources",
            "buildDependencies": [
                "GRAALPYTHON_NATIVE_LIBS",
                "com.oracle.graal.python.cext",
                "com.oracle.graal.python.hpy.llvm",
            ],
            "layout": {
                "./META-INF/resources/": [
                    "file:mx.graalpython/native-image.properties",
                ],
                "./META-INF/resources/include/": [
                    "file:graalpython/com.oracle.graal.python.cext/include/*",
                    "file:graalpython/com.oracle.graal.python.hpy.llvm/include/*",
                ],
            },
            "maven": False,
        },

        # The native libraries we ship. These are platform specific, and even
        # the names of libraries are platform specific. So we already put them
        # in the right folder structure here
        "GRAALPYTHON_NATIVE_RESOURCES": {
            "native": True,
            "platformDependent": True,
            "hashEntry":  "META-INF/resources/<os>/<arch>/native.sha256",
            "fileListEntry": "META-INF/resources/<os>/<arch>/native.files",
            "type": "dir",
            "description": "GraalVM Python platform dependent resources",
            "buildDependencies": [
                "GRAALPYTHON_NATIVE_LIBS",
                "com.oracle.graal.python.cext",
                "com.oracle.graal.python.hpy.llvm"
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "layout": {
                            "./META-INF/resources/<os>/<arch>/libs/": [
                                "dependency:GRAALPYTHON_NATIVE_LIBS/python-native.lib",
                            ],
                            "./META-INF/resources/<os>/<arch>/lib-graalpython/": [
                                "file:graalpython/lib-graalpython/*",
                                {
                                    "source_type": "dependency",
                                    "dependency": "GRAALPYTHON_NATIVE_LIBS",
                                    "path": "*",
                                    "exclude": ["python-native.lib"],
                                },
                            ],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "./META-INF/resources/<os>/<arch>/lib/graalpy<graal_ver:major_minor>/": [
                                "dependency:GRAALPYTHON_NATIVE_LIBS/*",
                            ],
                        },
                    },
                },
            },
            "maven": False,
        },

        # This puts the resources above into their platform specific folders at
        # build time. This is for the usage by mx and the SDK when building
        # standalones. The layouts should be exactly equivalent to what
        # PythonResource.java produces.
        "GRAALPYTHON_GRAALVM_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "fileListPurpose": 'native-image-resources',
            "description": "GraalVM Python support distribution for the GraalVM",
            "platforms": [
                "linux-amd64",
                "linux-aarch64",
                "darwin-amd64",
                "darwin-aarch64",
                "windows-amd64",
            ],
            "buildDependencies": [
                "GRAALPYTHON_LIBPYTHON_RESOURCES",
                "GRAALPYTHON_LIBGRAALPY_RESOURCES",
                "GRAALPYTHON_INCLUDE_RESOURCES",
                "GRAALPYTHON_NATIVE_RESOURCES",
            ],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "layout": {
                            "./Lib/": [
                                "dependency:GRAALPYTHON_LIBPYTHON_RESOURCES/META-INF/resources/libpython/*",
                            ],
                            "./lib-graalpython/": [
                                "dependency:GRAALPYTHON_LIBGRAALPY_RESOURCES/META-INF/resources/libgraalpy/*",
                            ],
                            "./": [
                                "dependency:GRAALPYTHON_INCLUDE_RESOURCES/META-INF/resources/*",
                                "dependency:GRAALPYTHON_NATIVE_RESOURCES/META-INF/resources/<os>/<arch>/*",
                            ],
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "./lib/python<py_ver:major_minor>/": [
                                "dependency:GRAALPYTHON_LIBPYTHON_RESOURCES/META-INF/resources/libpython/*",
                            ],
                            "./lib/graalpy<graal_ver:major_minor>/": [
                                "dependency:GRAALPYTHON_LIBGRAALPY_RESOURCES/META-INF/resources/libgraalpy/*",
                            ],
                            "./": [
                                "dependency:GRAALPYTHON_INCLUDE_RESOURCES/META-INF/resources/*",
                                "dependency:GRAALPYTHON_NATIVE_RESOURCES/META-INF/resources/<os>/<arch>/*",
                            ],
                        },
                    },
                },
            },
            "maven": False,
        },

        "GRAALPY_VIRTUALENV": {
            "native": True, # so it produces a tar, not a jar file
            "platformDependent": False,
            "maven": False,
            "description": "graalpy-virtualenv plugin sources usable to be installed into other interpreters",
            "layout": {
                "graalpy_virtualenv": "file:graalpy_virtualenv",
            },
            "maven": False,
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
                "LICENSE_GRAALPY.txt": "file:LICENSE.txt",
                "THIRD_PARTY_LICENSE_GRAALPY.txt": "file:THIRD_PARTY_LICENSE.txt",
            },
            "maven": False,
        },
    },
}
