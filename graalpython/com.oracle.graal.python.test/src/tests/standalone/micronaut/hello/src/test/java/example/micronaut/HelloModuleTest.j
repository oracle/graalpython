package example.micronaut;

import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
class HelloModuleTest {

    @Inject
    EmbeddedApplication<?> application;
    @Inject HelloModule hello;

    @Test
    void testItWorks() {
        Assertions.assertTrue(application.isRunning());
    }

    @Test
    void testHello() {
        Assertions.assertEquals("hello java", hello.createHello().hello("java"));
    }

}
