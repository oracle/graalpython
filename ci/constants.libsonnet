{
    local common = import "graal/ci/common.jsonnet",

    MX_VERSION:: "HEAD",

    TIME_LIMIT:: {
        "10m": "00:10:00",
        "15m": "00:15:00",
        "30m": "00:30:00",
        "90m": "1:30:00",
        "1h": "1:00:00",
        "2h": "2:00:00",
        "3h": "3:00:00",
        "4h": "4:00:00",
        "5h": "5:00:00",
        "6h": "6:00:00",
        "7h": "7:00:00",
        "8h": "8:00:00",
        "9h": "9:00:00",
        "10h": "10:00:00",
        "11h": "11:00:00",
        "16h": "16:00:00",
        "20h": "20:00:00",
    },

    TARGET:: {
        onDemand: common.frequencies.on_demand.targets,
        postMerge: common.frequencies.post_merge.targets,
        daily: common.frequencies.daily.targets,
        weekly: common.frequencies.weekly.targets,
        monthly: common.frequencies.monthly.targets,
        gate: common.frequencies.gate.targets,
    },

    NOTIFY_GROUPS:: ["tim.felgentreff@oracle.com"],

    local ENV = {
        graalpy_svm_ce: ["--env", "native-ce"],
        graalpy_svm_ee: ["--env", "native-ee"],
        graalpy_jvm_ce: ["--env", "jvm-ce-libgraal"],
        graalpy_jvm_ee: ["--env", "jvm-ee-libgraal"],
        libgraal_ce: ["--env", "../../graal/vm/mx.vm/libgraal"],
        libgraal_ee: ["--env", "../../graal-enterprise/vm-enterprise/mx.vm-enterprise/libgraal-enterprise"],
    },

    local DY = {
      ce: "/vm",
      ee: "/vm-enterprise,/graalpython-enterprise",
    },

    local PYVM = {
      graalpython: "graalpython",
      cpython: "cpython",
      pypy: "pypy",
    },
    PYVM:: PYVM,

    local PYVM_CONFIG = {
      default: "default",
      default_manual: "default-manual",
      default_multi: "default-multi",
      interpreter: "interpreter",
      interpreter_manual: "interpreter-manual",
      native_interpreter: "native-interpreter",
      native_interpreter_manual: "native-interpreter-manual",
      interpreter_multi: "interpreter-multi",
      native_interpreter_multi: "native-interpreter-multi",
      default_multi_tier: "default-multi-tier",
      native: "native",
      native_manual: "native-manual",
      native_multi: "native-multi",
      launcher: "launcher",
      panama: "panama",
    },

    local JAVA_EMBEDDING_VM_CONFIG = {
      java_embedding_multi_shared: "java-driver-multi-shared",
      java_embedding_interpreter_multi_shared: "java-driver-interpreter-multi-shared",
    },

    // the host VMs
    local JVM_VM = {
      graaljdk_ce: {
        dy: ["--dynamicimports", DY.ce],
        env: ENV.graalpy_jvm_ce,
        edition: 'ce',
      },
      graaljdk_ee: {
        dy: ["--dynamicimports", DY.ee],
        env: ENV.graalpy_jvm_ee,
        edition: 'ee',
      },
      graal_native_image_ce: {
        dy: ["--dynamicimports", DY.ce],
        env: ENV.graalpy_svm_ce,
        edition: 'ce',
      },
      graal_native_image_ee: {
        dy: ["--dynamicimports", DY.ee],
        env: ENV.graalpy_svm_ee,
        edition: 'ee',
      },
      server_libgraal_ce: {
        jvm: 'server',
        jvm_config: 'graal-core-libgraal',
        dy: ["--dynamicimports", DY.ce],
        env: ENV.libgraal_ce,
        edition: 'ce',
      },
      server_libgraal_ee: {
        jvm: 'server',
        jvm_config: 'graal-enterprise-libgraal',
        dy: ["--dynamicimports", DY.ee],
        env: ENV.libgraal_ee,
        edition: 'ee',
      },
      // this is needed to simplify the VM mixins (see below)
      none: {
        dy: [],
        env: [],
        edition: 'ce',
      },
    },

    // the python vms
    local PYTHON_VM = {
      graalpython: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.default,
      },
      graalpython_manual: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.default_manual,
      },
      graalpython_interpreter: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.interpreter,
      },
      graalpython_interpreter_manual: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.interpreter_manual,
      },
      graalpython_native_interpreter: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.native_interpreter,
      },
      graalpython_native_interpreter_manual: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.native_interpreter_manual,
      },
      graalpython_multi: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.default_multi,
      },
      graalpython_interpreter_multi: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.interpreter_multi,
      },
      graalpython_native_interpreter_multi: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.native_interpreter_multi,
      },
      graalpython_multi_tier: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.default_multi_tier,
      },
      graalpython_native: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.native,
      },
      graalpython_native_manual: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.native_manual,
      },
      graalpython_native_multi: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.native_multi,
      },
      graalpython_panama: {
        python_vm: PYVM.graalpython,
        python_vm_config: PYVM_CONFIG.panama,
      },
      java_embedding_multi_shared: {
        python_vm: PYVM.graalpython,
        python_vm_config: JAVA_EMBEDDING_VM_CONFIG.java_embedding_multi_shared,
      },
      java_embedding_interpreter_multi_shared: {
        python_vm: PYVM.graalpython,
        python_vm_config: JAVA_EMBEDDING_VM_CONFIG.java_embedding_interpreter_multi_shared,
      },
      cpython: {
        python_vm: PYVM.cpython,
        python_vm_config: PYVM_CONFIG.default,
      },
      cpython_launcher: {
        python_vm: PYVM.cpython,
        python_vm_config: PYVM_CONFIG.launcher,
      },
      pypy: {
        python_vm: PYVM.pypy,
        python_vm_config: PYVM_CONFIG.default,
      },
      pypy_launcher: {
        python_vm: PYVM.pypy,
        python_vm_config: PYVM_CONFIG.launcher,
      },
    },

    local VM = {
        // graalpy jvm standalones
        graalpython_core: PYTHON_VM.graalpython + JVM_VM.graaljdk_ce,
        graalpython_core_manual: PYTHON_VM.graalpython_manual + JVM_VM.graaljdk_ce,
        graalpython_core_interpreter: PYTHON_VM.graalpython_interpreter + JVM_VM.graaljdk_ce,
        graalpython_core_interpreter_manual: PYTHON_VM.graalpython_interpreter_manual + JVM_VM.graaljdk_ce,
        graalpython_core_multi: PYTHON_VM.graalpython_multi + JVM_VM.graaljdk_ce,
        graalpython_core_interpreter_multi: PYTHON_VM.graalpython_interpreter_multi + JVM_VM.graaljdk_ce,
        graalpython_core_multi_tier: PYTHON_VM.graalpython_multi_tier + JVM_VM.graaljdk_ce,
        graalpython_enterprise: PYTHON_VM.graalpython + JVM_VM.graaljdk_ee,
        graalpython_enterprise_manual: PYTHON_VM.graalpython_manual + JVM_VM.graaljdk_ee,
        graalpython_enterprise_multi: PYTHON_VM.graalpython_multi + JVM_VM.graaljdk_ee,
        graalpython_enterprise_multi_tier: PYTHON_VM.graalpython_multi_tier + JVM_VM.graaljdk_ee,
        graalpython_enterprise_interpreter: PYTHON_VM.graalpython_interpreter + JVM_VM.graaljdk_ee,
        graalpython_enterprise_interpreter_manual: PYTHON_VM.graalpython_interpreter_manual + JVM_VM.graaljdk_ee,
        graalpython_core_native: PYTHON_VM.graalpython_native + JVM_VM.graaljdk_ce,
        graalpython_core_native_manual: PYTHON_VM.graalpython_native_manual + JVM_VM.graaljdk_ce,
        graalpython_core_native_interpreter: PYTHON_VM.graalpython_native_interpreter + JVM_VM.graaljdk_ce,
        graalpython_core_native_interpreter_manual: PYTHON_VM.graalpython_native_interpreter_manual + JVM_VM.graaljdk_ce,
        graalpython_core_native_multi: PYTHON_VM.graalpython_native_multi + JVM_VM.graaljdk_ce,
        graalpython_core_native_interpreter_multi: PYTHON_VM.graalpython_native_interpreter_multi + JVM_VM.graaljdk_ce,
        graalpython_enterprise_native: PYTHON_VM.graalpython_native + JVM_VM.graaljdk_ee,
        graalpython_enterprise_native_manual: PYTHON_VM.graalpython_native_manual + JVM_VM.graaljdk_ee,
        graalpython_enterprise_native_multi: PYTHON_VM.graalpython_native_multi + JVM_VM.graaljdk_ee,
        graalpython_core_panama: PYTHON_VM.graalpython_panama + JVM_VM.graaljdk_ce,
        graalpython_enterprise_panama: PYTHON_VM.graalpython_panama + JVM_VM.graaljdk_ee,

        // graalpy native standalones
        graalvm_ce_default: PYTHON_VM.graalpython + JVM_VM.graal_native_image_ce,
        graalvm_ce_default_interpreter: PYTHON_VM.graalpython_interpreter + JVM_VM.graal_native_image_ce,
        graalvm_ee_default: PYTHON_VM.graalpython + JVM_VM.graal_native_image_ee,
        graalvm_ee_default_manual: PYTHON_VM.graalpython_manual + JVM_VM.graal_native_image_ee,
        graalvm_ee_default_interpreter: PYTHON_VM.graalpython_interpreter + JVM_VM.graal_native_image_ee,
        graalvm_ee_default_interpreter_manual: PYTHON_VM.graalpython_interpreter_manual + JVM_VM.graal_native_image_ee,
        graalvm_ce_default_multi_tier: PYTHON_VM.graalpython_multi_tier + JVM_VM.graal_native_image_ce,
        graalvm_ee_default_multi_tier: PYTHON_VM.graalpython_multi_tier + JVM_VM.graal_native_image_ee,

        // only 3 compiler threads
        graalpython_core_3threads: PYTHON_VM.graalpython + JVM_VM.graaljdk_ce + {python_vm_config: super.python_vm_config + "-3-compiler-threads"},
        graalpython_enterprise_3threads: PYTHON_VM.graalpython + JVM_VM.graaljdk_ee + {python_vm_config: super.python_vm_config + "-3-compiler-threads"},
        graalvm_ce_default_3threads: PYTHON_VM.graalpython + JVM_VM.graal_native_image_ce + {python_vm_config: super.python_vm_config + "-3-compiler-threads"},
        graalvm_ee_default_3threads: PYTHON_VM.graalpython + JVM_VM.graal_native_image_ee + {python_vm_config: super.python_vm_config + "-3-compiler-threads"},
        graalpython_core_multi_tier_3threads: PYTHON_VM.graalpython_multi_tier + JVM_VM.graaljdk_ce + {python_vm_config: super.python_vm_config + "-3-compiler-threads"},
        graalpython_enterprise_multi_tier_3threads: PYTHON_VM.graalpython_multi_tier + JVM_VM.graaljdk_ee + {python_vm_config: super.python_vm_config + "-3-compiler-threads"},
        graalvm_ce_default_multi_tier_3threads: PYTHON_VM.graalpython_multi_tier + JVM_VM.graal_native_image_ce + {python_vm_config: super.python_vm_config + "-3-compiler-threads"},
        graalvm_ee_default_multi_tier_3threads: PYTHON_VM.graalpython_multi_tier + JVM_VM.graal_native_image_ee + {python_vm_config: super.python_vm_config + "-3-compiler-threads"},

        // Java embedding
        java_embedding_core_multi_shared: PYTHON_VM.java_embedding_multi_shared + JVM_VM.server_libgraal_ce,
        java_embedding_core_interpreter_multi_shared: PYTHON_VM.java_embedding_interpreter_multi_shared + JVM_VM.server_libgraal_ce,
        java_jmh_core: JVM_VM.server_libgraal_ce,
        java_jmh_enterprise: JVM_VM.server_libgraal_ee,

        // basline vms
        cpython: PYTHON_VM.cpython + JVM_VM.none,
        pypy: PYTHON_VM.pypy + JVM_VM.none,
        cpython_launcher: PYTHON_VM.cpython_launcher + JVM_VM.none,
        pypy_launcher: PYTHON_VM.pypy_launcher + JVM_VM.none,
    },
    VM:: VM,
}

// Local Variables:
// jsonnet-indent-level: 4
// smie-indent-basic: 4
// End:
