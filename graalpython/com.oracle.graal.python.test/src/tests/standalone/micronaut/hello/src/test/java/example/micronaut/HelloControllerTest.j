package example.micronaut;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class HelloControllerTest {

    @Inject
    EmbeddedServer server;

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testHelloResponse() {
        String response = client.toBlocking().retrieve(HttpRequest.GET("/hello"));
        assertEquals("hello java", response);
    }

    @Test
    void testHelloValueResponse() {
        String response = client.toBlocking().retrieve(HttpRequest.GET("/hellovalue"));
        assertEquals("hello java value", response);
    }
}
