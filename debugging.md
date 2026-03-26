# Debugging Notes

## `mvn spring-boot:run` fails to start — no embedded Tomcat

**Symptom**: Spring Boot application fails to start, no embedded web container found.

**Root cause**: `peta-manager-server/pom.xml` had `<exclusions>` removing `tomcat-embed-core` from `spring-boot-starter-tomcat`, instead of using `<scope>provided</scope>`.

**Why it matters**:
- `<scope>provided</scope>`: Maven excludes the JAR from WAR packaging, but `spring-boot-maven-plugin` still includes it on the classpath for `spring-boot:run`. Embedded Tomcat works.
- `<exclusions>` on `tomcat-embed-core`: The dependency is completely removed from the dependency tree. Neither WAR nor `spring-boot:run` can see it. Embedded Tomcat is gone.

**Fix** (in `peta-manager-server/pom.xml`):
```xml
<!-- CORRECT: use provided scope -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-tomcat</artifactId>
    <scope>provided</scope>
</dependency>

<!-- WRONG: exclusions break spring-boot:run -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-tomcat</artifactId>
    <exclusions>
        <exclusion>
            <artifactId>tomcat-embed-core</artifactId>
            <groupId>org.apache.tomcat.embed</groupId>
        </exclusion>
    </exclusions>
</dependency>
```

**Also needed**: `spring-boot-maven-plugin` with Java 21 `--add-opens` flags:
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <jvmArguments>
            --add-opens java.base/java.lang=ALL-UNNAMED
            --add-opens java.base/java.lang.reflect=ALL-UNNAMED
            --add-opens java.base/java.util=ALL-UNNAMED
        </jvmArguments>
    </configuration>
</plugin>
```
