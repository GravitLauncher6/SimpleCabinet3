# Runtime modules

Place module JARs in the `modules` directory next to the application working
directory. Exploded module directories are supported as well. Entries are
loaded in filename order.

Every module configuration must be a normal Spring `@Configuration` class.
It can use `@ComponentScan`, `@EntityScan`, `@Import`, `@Bean`, and any other
Spring configuration mechanism. The module classpath has the application
classpath as its parent, so module code can depend on the application's
public classes and Spring beans in the usual way.

For example:

```kotlin
@Configuration
@ComponentScan("com.example.module")
@EntityScan("com.example.module.entity")
class ExampleModuleConfiguration
```

Configuration classes are registered before Spring Boot's configuration class
parser runs. This means imported configurations, component-scanned beans,
repositories, entities, controllers, and `@Bean` methods participate in the
same application context.
