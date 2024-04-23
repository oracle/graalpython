# pylint: disable=anomalous-backslash-in-string
suite = {
    # --------------------------------------------------------------------------------------------------------------
    #
    #  METADATA
    #
    # --------------------------------------------------------------------------------------------------------------
    "mxversion": "7.6.1",
    "name": "graalpython",
    "versionConflictResolution": "latest",

    "version": "24.1.0",
    "graalpython:pythonVersion": "3.11.7",
    "release": False,
    "groupId": "org.graalvm.python",
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
                "version": "b9126869e93338dde41dd3aa102131aa347bb803",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "tools",
                "version": "b9126869e93338dde41dd3aa102131aa347bb803",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ],
            },
            {
                "name": "sulong",
                "version": "b9126869e93338dde41dd3aa102131aa347bb803",
                "subdir": True,
                "urls": [
                    {"url": "https://github.com/oracle/graal", "kind": "git"},
                ]
            },
            {
                "name": "regex",
                "version": "b9126869e93338dde41dd3aa102131aa347bb803",
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
            "licenses": ["GPLv2-CPE", "UPL", "BSD-new", "MIT", "PSF-License"]
        },
        "python-local-snapshots": {
            "url": "http://localhost",
            "licenses": ["GPLv2-CPE", "UPL", "BSD-new", "MIT", "PSF-License"]
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
        "XZ-5.2.6": {
            "urls": [
                "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/xz-5.2.6.tar.gz",
            ],
            "packedResource": True,
            "digest": "sha512:090958dd6c202c989746686094c86707ad4ae835026640080fc0a9d0fad699821b7d5cb3a67e6700661a0938818ba153662366f89ab8ec47e0bae4a3fe9b1961",
        },
        "BOUNCYCASTLE-PROVIDER": {
            "digest": "sha512:dbc5b525d805823b53dbaded11547155a14f795212ce6fe1e93d6da431081ea9480718ea2fc17dc7906f8489aadb68e781afd1e771d26f9f8a09b21552bb165c",
            "sourceDigest": "sha512:4ce8b88e26af98c3cb8a3691ace366e960e36a8225d14685447b4aa9838b92334bdb63f8ba4baf651d28c8e063e21d0cbca8f2fcf8eecd003362ae62b6c87dbd",
            "maven": {
              "groupId": "org.bouncycastle",
              "artifactId": "bcprov-jdk18on",
              "version": "1.76",
            },
            "moduleName": "org.bouncycastle.provider",
        },
        "BOUNCYCASTLE-PKIX": {
            "digest": "sha512:b924374168e25f21ab7f6dd4f6755e55a401cbbbaa0d6f17a0c9bf59e61dc42750b200c494c413f6f8c27fc16d9312f51fc15c979e4298916f5bd0c329cbbffa",
            "sourceDigest": "sha512:6945aedc041f9282ee3569aef46c6df8940643e5a66236c5e95fafdc4dead4b94d3d64f32750ce2f131b4fdd398aacd200968103fc3e4d22eb2dc171aedb48dd",
            "maven": {
                "groupId": "org.bouncycastle",
                "artifactId": "bcpkix-jdk18on",
                "version": "1.76",
            },
            "moduleName": "org.bouncycastle.pkix",
        },
        "BOUNCYCASTLE-UTIL": {
            "digest": "sha512:385d95b4c32053bb3734c342d5f3255bcc1cee7e35649965bb5fbf8733ec37009fd5f5e06817a45e7857a2e62e923563ce1231ee3a1de411f788dfa93d39ce41",
            "sourceDigest": "sha512:8d2068b8a90381dde75f25059dfdf3073a2657ea8f7d65872f972aaae6b780a4156b39d922e10302f4c4ddaf22d5057c02e9a0cb2a228f0a43730dfba46b1b22",
            "maven": {
                "groupId": "org.bouncycastle",
                "artifactId": "bcutil-jdk18on",
                "version": "1.76",
            },
            "moduleName": "org.bouncycastle.util",
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
        "JBANG" : {
            "urls" : [
                "https://github.com/jbangdev/jbang/releases/download/v0.114.0/jbang-0.114.0.zip"
            ],
            "digest": "sha256:660c7eb2eda888897f20aa5c5927ccfed924f3b86d5f2a2477a7b0235cdc94bb"
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
                "sdk:POLYGLOT",
                "sdk:LAUNCHER_COMMON",
                "sdk:JLINE3",
                "sdk:MAVEN_DOWNLOADER",
            ],
            "requires": [
                "java.management",
                "java.xml",
            ],
            "jacoco": "include",
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.graal.python",
        },

        "org.graalvm.python.embedding": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:POLYGLOT",
            ],
            "jacoco": "include",
            "javaCompliance": "17+",
            "checkstyle": "com.oracle.graal.python",
        },
        "org.graalvm.python.embedding.tools": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
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
                "jdk.compiler",
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
                "GRAALPYTHON_RESOURCES",
                "truffle:TRUFFLE_API",
                "tools:TRUFFLE_PROFILER",
                "regex:TREGEX",
                "sdk:POLYGLOT",
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
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_NFI",
                "tools:TRUFFLE_PROFILER",
                "sdk:POLYGLOT",
                "sulong:SULONG_API",
                "truffle:TRUFFLE_XZ",
                "truffle:TRUFFLE_ICU4J",
                "regex:TREGEX",
                "BOUNCYCASTLE-PROVIDER",
                "BOUNCYCASTLE-PKIX",
                "BOUNCYCASTLE-UTIL",
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
            "checkstyleVersion": "10.7.0",
            "annotationProcessors": [
                "GRAALPYTHON_PROCESSOR",
                "truffle:TRUFFLE_DSL_PROCESSOR"
            ],
            "forceJavac": True, # GRAALPYTHON_PROCESSOR is not compatible with ECJ
            "workingSets": "Truffle,Python",
            "spotbugsIgnoresGenerated": True,
        },

        # GRAALPYTHON TESTS
        "com.oracle.graal.python.test": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.graal.python.shell",
                "com.oracle.graal.python",
                "com.oracle.graal.python.test.integration",
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
                # This is used to discover some files needed for the tests that
                # normally live in GraalPython source tree
                "test.graalpython.home": "<suite:graalpython>/graalpython"
            },
        },

        # GRAALPYTHON_INTEGRATION_UNIT_TESTS
        "com.oracle.graal.python.test.integration": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "GRAALPYTHON_EMBEDDING",
                "mx:JUNIT",
                "sdk:GRAAL_SDK",
            ],
            "requires": [
                "java.management",
                "jdk.management",
                "jdk.unsupported",
            ],
            "jacoco": "exclude",
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "17+",
            "workingSets": "Truffle,Python",
            "testProject": True,
        },

        "com.oracle.graal.python.hpy.test": {
            "subDir": "graalpython",
            "sourceDirs": ["src"],
            "dependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON_NATIVE_LIBS",
            ],
            "jacoco": "exclude",
            "checkstyle": "com.oracle.graal.python",
            "javaCompliance": "17+",
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
                "sdk:POLYGLOT",
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
        },

        "python-venvlauncher": {
            "subDir": "graalpython",
            "native":  "executable",
            "deliverable": "venvlauncher",
            "os_arch": {
                "windows": {
                    "<others>": {
                        "defaultBuild": True,
                    },
                },
                "<others>": {
                    "<others>": {
                        "defaultBuild": False,
                    },
                },
            },
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

        "graalpy-versions": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "max_jobs": "1",
            "ninja_targets": ["all"],
            "cmakeConfig": {
                "GRAALPY_VER": "<py_ver:binary><graal_ver:binary><dev_tag:none>",
            },
            "results": [
                "graalpy_versions"
            ],
        },

        "com.oracle.graal.python.cext": {
            "subDir": "graalpython",
            "class": "CMakeNinjaProject",
            "toolchain": "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
            "max_jobs": "8",
            "vpath": True,
            "use_jdk_headers": True, # not actually, just making mx happy
            "ninja_targets": ["all"],
            "ninja_install_targets": ["install"],
            "os_arch": {
                "windows": {
                    "<others>": {
                        "cmakeConfig": {
                            "CAPI_INC_DIR": "<output_root:com.oracle.graal.python>/jni_gen",
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
                            "bin/modules/_sha3<graalpy_ext:native>",
                        ],
                    },
                },
                "<others>": {
                    "<others>": {
                        "cmakeConfig": {
                            "CAPI_INC_DIR": "<output_root:com.oracle.graal.python>/jni_gen",
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
                            "bin/modules/_sha3<graalpy_ext:native>",
                            "bin/modules/_testcapi<graalpy_ext:native>",
                            "bin/modules/_testbuffer<graalpy_ext:native>",
                            "bin/modules/_testmultiphase<graalpy_ext:native>",
                            "bin/modules/_ctypes_test<graalpy_ext:native>",
                            "bin/modules/pyexpat<graalpy_ext:native>",
                            "bin/modules/termios<graalpy_ext:native>",
                        ],
                    },
                },
            },
            "buildDependencies": [
                "sulong:SULONG_HOME",
                "sulong:SULONG_BOOTSTRAP_TOOLCHAIN",
                "sulong:SULONG_LEGACY",
                "com.oracle.graal.python",
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
                            "-DHPY_ABI_HYBRID", "-DHPY_EMBEDDED_MODULES", "-DNDEBUG", "-DMS_WINDOWS",
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
                            "-DHPY_ABI_HYBRID", "-DHPY_EMBEDDED_MODULES", "-DNDEBUG",
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
                        "ldlibs": ["-lutil"],
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
                "\\/__pycache__\\/",
                "\\/test\\/",
                "\\/tests\\/",
                "\\/idle_test\\/",
                "\\\\__pycache__\\\\",
                "\\\\test\\\\",
                "\\\\tests\\\\",
                "\\\\idle_test\\\\",
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
            "layout": {
                "./": "dependency:graalpy-versions/graalpy_versions",
            },
        },
        "GRAALPYTHON_VERSIONS_MAIN": {
            "type": "dir",
            "layout": {
                "./": "dependency:graalpy-versions/graalpy_versions",
            },
        },

        "GRAALPYTHON_EMBEDDING" : {
            "moduleInfo": {
                "name": "org.graalvm.python.embedding",
                "exports": [
                    "org.graalvm.python.embedding.utils",
                    "org.graalvm.python.embedding.vfs",
                ]
            },
            "useModulePath": True,
            "dependencies": [
                "org.graalvm.python.embedding"
            ],
            "distDependencies": [
                "sdk:POLYGLOT",
            ],
            "description": "GraalPy Embedding",
            "maven": {
                "groupId": "org.graalvm.python",
                "artifactId": "python-embedding",
                "tag": ["default", "public"],
            },

        },
        "GRAALPYTHON_EMBEDDING_TOOLS" : {
            "moduleInfo": {
                "name": "org.graalvm.python.embedding.tools",
                "exports": [
                    "org.graalvm.python.embedding.tools.vfs",
                    "org.graalvm.python.embedding.tools.exec",
                    "org.graalvm.python.embedding.tools.jbang",
                ]
            },
            "useModulePath": True,
            "dependencies": [
                "org.graalvm.python.embedding.tools",
            ],
            "distDependencies": [
                "sdk:POLYGLOT",
            ],
            "description": "GraalPy Embedding Tools",
            "maven": {
                "groupId": "org.graalvm.python",
                "artifactId": "python-embedding-tools",
                "tag": ["default", "public"],
            },

        },

        "GRAALPYTHON-LAUNCHER": {
            "moduleInfo": {
                "name": "org.graalvm.py.launcher",
                "exports": [
                    "com.oracle.graal.python.shell to org.graalvm.py.enterprise.launcher",
                    "com.oracle.graal.python.shell to org.graalvm.launcher",
                ],
            },
            "useModulePath": True,
            "dependencies": [
                "com.oracle.graal.python.shell",
            ],
            "distDependencies": [
                "sdk:POLYGLOT",
                "sdk:LAUNCHER_COMMON",
                "sdk:JLINE3",
                "sdk:MAVEN_DOWNLOADER",
            ],
            "description": "GraalPython launcher",
            "maven": {
                "groupId": "org.graalvm.python",
                "artifactId": "python-launcher",
                "tag": ["default", "public"],
            },
        },

        "GRAALPYTHON_NATIVE_LIBS": {
            "native": True,
            "platformDependent": True,
            "type": "dir",
            "os_arch": {
                "windows": {
                    "<others>": {
                        "layout": {
                            "<os>/<arch>/": [
                                "dependency:com.oracle.graal.python.jni/*",
                                "dependency:com.oracle.graal.python.cext/bin/*",
                                "dependency:com.oracle.graal.python.hpy.llvm/bin/*",
                            ]
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "<os>/<arch>/": [
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
            "platforms": [
                "linux-amd64",
                "linux-aarch64",
                "darwin-amd64",
                "darwin-aarch64",
                "windows-amd64",
            ],
            "description": "Contains the JNI native lib, the C API and support libs.",
            "maven": False,
        },

        "GRAALPYTHON_RESOURCES": {
            "platformDependent": False,
            "moduleInfo": {
                "name": "org.graalvm.py.resources",
            },
            "useModulePath": True,
            "dependencies": [
                "com.oracle.graal.python.resources",
                "GRAALPYTHON_VERSIONS_RES",
                "GRAALPYTHON_NI_RESOURCES",
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
            "compress": True,
            "description": "GraalPython runtime resources",
            "maven": {
                "artifactId": "python-resources",
                "groupId": "org.graalvm.python",
                "tag": ["default", "public"],
            },
            "license": [
                "UPL",
                "MIT",
                "PSF-License",
            ],
        },

        "GRAALPYTHON": {
            "moduleInfo": {
                "name": "org.graalvm.py",
                "exports": [
                    "com.oracle.graal.python.* to org.graalvm.py.enterprise",
                ],
                "uses": [
                    "com.oracle.graal.python.builtins.PythonBuiltins",
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
                "sdk:POLYGLOT",
                "truffle:TRUFFLE_NFI",
                "truffle:TRUFFLE_NFI_LIBFFI", # runtime dependency for convenience
                "sulong:SULONG_API",
                "truffle:TRUFFLE_ICU4J",
                "truffle:TRUFFLE_XZ",
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
                "BOUNCYCASTLE-UTIL",
            ],
            "javaProperties": {
                "python.jni.library": "<lib:pythonjni>"
            },
            "description": "GraalPython language",
            "maven": {
                "artifactId": "python-language",
                "groupId": "org.graalvm.python",
                "tag": ["default", "public"],
            },
            "noMavenJavadoc": True,
            "license": [
                "UPL",
                "MIT",
                "PSF-License",
            ],
        },

        "PYTHON_COMMUNITY": {
            "type": "pom",
            "runtimeDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON_RESOURCES",
                "truffle:TRUFFLE_RUNTIME",
            ],
            "description": "GraalPython engine.",
            "maven": {
                "groupId": "org.graalvm.python",
                "artifactId": "python-community",
                "tag": ["default", "public"],
            },
            "license": [
                "UPL",
                "MIT",
                "PSF-License",
            ],
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
            "description": "GraalPy unit tests that can access its internals. These tests require open access to GraalPy and Truffle modules.",
            "dependencies": [
                "com.oracle.graal.python.test",
                "com.oracle.graal.python.pegparser.test",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON-LAUNCHER",
                "sulong:SULONG_NATIVE", # See MultiContextTest#testSharingWithStruct
                "truffle:TRUFFLE_TCK",
                "GRAALPYTHON_INTEGRATION_UNIT_TESTS",
            ],
            "testDistribution": True,
            "maven": False,
            "unittestConfig": "python-internal",
        },

        "GRAALPYTHON_INTEGRATION_UNIT_TESTS": {
            "description": "Python integration tests. These tests access GraalPy only via the GraalVM SDK APIs",
            "dependencies": [
                "com.oracle.graal.python.test.integration",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "GRAALPYTHON",
                "GRAALPYTHON_RESOURCES",
                "GRAALPYTHON_EMBEDDING",
                "sulong:SULONG_NATIVE", # See MultiContextTest#testSharingWithStruct
                "sdk:GRAAL_SDK",
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
                "sdk:POLYGLOT",
            ],
            "testDistribution": True,
            "maven": False,
        },

        "GRAALPYTHON_TCK": {
            "dependencies": [
                "com.oracle.graal.python.tck",
            ],
            "exclude": ["mx:JUNIT"],
            "distDependencies": [
                "sdk:POLYGLOT_TCK",
                "GRAALPYTHON",
                # We run the TCK with Python home served from resources
                "GRAALPYTHON_RESOURCES",
            ],
            "description" : "Truffle TCK provider for Python language.",
            "license": "UPL",
            "maven": {
                "groupId": "org.graalvm.python",
                "artifactId": "python-truffle-tck",
                "tag": ["default", "public"],
            },
            "noMavenJavadoc": True,
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

        "GRAALPYTHON_NI_RESOURCES": {
            "native": False,
            "platformDependent": False,
            "hashEntry":  "META-INF/resources/ni.sha256",
            "fileListEntry": "META-INF/resources/ni.files",
            "type": "dir",
            "description": "GraalVM Python native image resources",
            "layout": {
                "./META-INF/resources/": [
                    "file:mx.graalpython/native-image.properties",
                ],
            },
            "maven": False,
        },

        # The Python and HPy headers. These go to "/include" on windows and
        # "/include/python<py_ver:major_minor>" on unix
        "GRAALPYTHON_INCLUDE_RESOURCES": {
            "native": False,
            "platformDependent": False,
            "hashEntry":  "META-INF/resources/include.sha256",
            "fileListEntry": "META-INF/resources/include.files",
            "type": "dir",
            "description": "GraalVM Python header resources",
            "layout": {
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
            "os_arch": {
                "windows": {
                    "<others>": {
                        "layout": {
                            "./META-INF/resources/<os>/<arch>/libs/": [
                                "dependency:GRAALPYTHON_NATIVE_LIBS/<os>/<arch>/python-native.lib",
                            ],
                            "./META-INF/resources/<os>/<arch>/lib-graalpython/": [
                                {
                                    "source_type": "dependency",
                                    "dependency": "GRAALPYTHON_NATIVE_LIBS",
                                    "path": "<os>/<arch>/*",
                                    "exclude": ["python-native.lib"],
                                },
                            ],
                            "./META-INF/resources/<os>/<arch>/Lib/venv/scripts/nt/graalpy.exe": "dependency:python-venvlauncher",
                            "./META-INF/resources/<os>/<arch>/Lib/venv/scripts/nt/python.exe": "dependency:python-venvlauncher",
                        },
                    },
                },
                "<others>": {
                    "<others>": {
                        "layout": {
                            "./META-INF/resources/<os>/<arch>/lib/graalpy<graal_ver:major_minor>/": [
                                "dependency:GRAALPYTHON_NATIVE_LIBS/<os>/<arch>/*",
                            ],
                        },
                    },
                },
            },
            "platforms": [
                "linux-amd64",
                "linux-aarch64",
                "darwin-amd64",
                "darwin-aarch64",
                "windows-amd64",
            ],
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
                            "./include/": [
                                "dependency:GRAALPYTHON_INCLUDE_RESOURCES/META-INF/resources/include/*",
                            ],
                            "./": [
                                "dependency:GRAALPYTHON_NI_RESOURCES/META-INF/resources/*",
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
                            "./include/python<py_ver:major_minor>/": [
                                "dependency:GRAALPYTHON_INCLUDE_RESOURCES/META-INF/resources/include/*",
                            ],
                            "./": [
                                "dependency:GRAALPYTHON_NI_RESOURCES/META-INF/resources/*",
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

        "graalpy-archetype-polyglot-app": {
            "class": "MavenProject",
            "subDir": "graalpython",
            "noMavenJavadoc": True,
            "maven": {
                "tag": ["default", "public"],
            },
        },

        "graalpy-maven-plugin": {
            "class": "MavenProject",
            "subDir": "graalpython",
            "noMavenJavadoc": True,
            "dependencies": [
                "GRAALPYTHON-LAUNCHER",
                "GRAALPYTHON_EMBEDDING_TOOLS",
            ],
            "maven": {
                "tag": ["default", "public"],
            },
        },
    },
}
