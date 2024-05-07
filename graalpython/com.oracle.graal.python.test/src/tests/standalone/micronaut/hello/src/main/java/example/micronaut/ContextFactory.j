package example.micronaut;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.micronaut.GraalPyContextFactory;
import org.graalvm.python.embedding.vfs.VirtualFileSystem;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Factory
class ContextFactory {

    private Context context;

    @Bean(preDestroy = "close")
    @Singleton
    @Replaces(value = Context.class, factory = GraalPyContextFactory.class)
    public Context createContext() {
        VirtualFileSystem vfs = VirtualFileSystem.create();
        context = Context.newBuilder()
                .allowExperimentalOptions(false)
                .allowAllAccess(false)
                .allowHostAccess(HostAccess.ALL)
                .allowIO(IOAccess.newBuilder()
                        .allowHostSocketAccess(true)
                        .fileSystem(vfs)
                        .build())
                .allowCreateThread(true)
                .allowNativeAccess(true)
                .allowPolyglotAccess(PolyglotAccess.ALL)
                .option("python.PosixModuleBackend", "java")
                .option("python.DontWriteBytecodeFlag", "true")
                .option("python.VerboseFlag", System.getenv("PYTHONVERBOSE") != null ? "true" : "false")
                .option("log.python.level", System.getenv("PYTHONVERBOSE") != null ? "FINE" : "SEVERE")
                .option("python.WarnOptions", System.getenv("PYTHONWARNINGS") == null ? "" : System.getenv("PYTHONWARNINGS"))
                .option("python.AlwaysRunExcepthook", "true")
                .option("python.ForceImportSite", "true")
                .option("python.Executable", vfs.vfsVenvPath() + (VirtualFileSystem.isWindows() ? "\\Scripts\\python.exe" : "/bin/python"))
                .option("python.PythonHome", vfs.vfsHomePath())
                .option("engine.WarnInterpreterOnly", "false")
                .option("python.PythonPath", vfs.vfsProjPath())
                .build();
        context.initialize("python");
        System.out.println("=== CREATED REPLACE CONTEXT ===");
        return context;
    }

    @PreDestroy
    public void close() throws IOException {
        try {
            context.interrupt(Duration.of(5,  ChronoUnit.SECONDS));
            context.close(true);
        } catch (Exception e) {
            // ignore
        }
    }
}
