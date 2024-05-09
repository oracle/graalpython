package example.micronaut;

import org.graalvm.python.embedding.micronaut.annotations.GraalPyModuleBean;

@GraalPyModuleBean("hello")
public interface HelloModule {
    Hello createHello();

    public interface Hello {
        String hello(String txt);
    }
}
