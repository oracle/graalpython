package example.micronaut;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

@Controller("/hellovalue")
@Produces(MediaType.TEXT_PLAIN)
public class HelloValueController {

    private Value value;

    public HelloValueController(Context context) {
        this.value = context.eval("python", "import hello; hello");
    }

    @Get(uri="/")
    public String index() {
        return value.invokeMember("createHello").invokeMember("hello", "java value").asString();
    }
}
