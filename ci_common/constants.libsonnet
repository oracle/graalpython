{
    TIME_LIMIT:: {
        "30m": "00:30:00",
        "1h": "1:00:00",
        "2h": "2:00:00",
        "3h": "3:00:00",
        "4h": "4:00:00",
        "8h": "8:00:00",
        "10h": "10:00:00",
        "16h": "16:00:00",
        "20h": "20:00:00",
    },

    TARGET:: {
        onDemand: ["bench"],
        postMerge: ["post-merge"],
        weekly: ["weekly"],
        gate: ["gate"],
    },

    JVM:: {
        server: "server",
    },

    JVM_CONFIG:: {
        core: "graal-core",
        enterprise: "graal-enterprise",
        native: "native",
        hostspot: "default",
    },
}
