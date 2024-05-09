package example.micronaut;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import jakarta.inject.Inject;

@Controller("/hello")
@Produces(MediaType.TEXT_PLAIN)
public class HelloController {

    private HelloModule hello;

    @Get(uri="/")
    public String index() {
        return hello.createHello().hello("java");
    }

    @Inject
    public void inject(HelloModule hello) {
        this.hello = hello;
    }

}
