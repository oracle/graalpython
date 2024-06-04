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
import org.graalvm.python.embedding.utils.GraalPyResources;

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
        context = GraalPyResources.createContext();
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
