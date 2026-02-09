# Service Module Template

Template for creating hexagonal service modules in the modular monolith.

## Service Structure

```
{service}/
├── pom.xml                          # Parent POM (aggregator)
├── domain-modules/
│   ├── pom.xml
│   └── {domain}-domain-module/
│       ├── pom.xml
│       └── src/main/java/{package}/domain/
│           ├── api/                  # Public interfaces
│           │   └── {Entity}Service.java
│           ├── dto/                  # Shared DTOs
│           │   └── {Entity}DTO.java
│           └── internal/             # Hidden implementations
│               ├── {Entity}ServiceImpl.java
│               └── {Entity}Factory.java
├── application-module/
│   ├── pom.xml
│   └── src/main/java/{package}/application/
│       └── usecase/
│           └── {Action}{Entity}UseCase.java
└── infrastructure-module/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/{package}/infrastructure/
        │   │   ├── rest/
        │   │   │   ├── {Entity}Resource.java
        │   │   │   └── dto/
        │   │   │       ├── Create{Entity}Request.java
        │   │   │       └── {Entity}Response.java
        │   │   ├── persistence/
        │   │   │   ├── entity/{Entity}Entity.java
        │   │   │   ├── repository/{Entity}Repository.java
        │   │   │   └── adapter/{Entity}PersistenceAdapter.java
        │   │   └── config/
        │   │       └── {Service}Config.java
        │   └── resources/
        │       └── application.properties
        └── test/java/{package}/
            └── ArchitectureTest.java
```

## POM Files

### Service Parent pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>${BASE_PACKAGE}</groupId>
        <artifactId>platform-bom</artifactId>
        <version>${BOM_VERSION}</version>
        <relativePath>../platform-bom</relativePath>
    </parent>

    <groupId>${BASE_PACKAGE}.${SERVICE_NAME}</groupId>
    <artifactId>${SERVICE_NAME}</artifactId>
    <packaging>pom</packaging>

    <modules>
        <module>domain-modules</module>
        <module>application-module</module>
        <module>infrastructure-module</module>
    </modules>
</project>
```

### Domain Module pom.xml

```xml
<project>
    <parent>
        <groupId>${BASE_PACKAGE}.${SERVICE_NAME}</groupId>
        <artifactId>domain-modules</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>${DOMAIN_NAME}-domain-module</artifactId>

    <dependencies>
        <!-- Domain should be framework-agnostic -->
        <!-- Only standard Java dependencies -->
    </dependencies>
</project>
```

### Infrastructure Module pom.xml

```xml
<project>
    <parent>
        <groupId>${BASE_PACKAGE}.${SERVICE_NAME}</groupId>
        <artifactId>${SERVICE_NAME}</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>infrastructure-module</artifactId>

    <dependencies>
        <!-- Internal Modules -->
        <dependency>
            <groupId>${BASE_PACKAGE}.${SERVICE_NAME}</groupId>
            <artifactId>${DOMAIN_NAME}-domain-module</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>${BASE_PACKAGE}.${SERVICE_NAME}</groupId>
            <artifactId>application-module</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Quarkus Extensions -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest-jackson</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-hibernate-orm-panache</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-jdbc-${DATABASE_TYPE}</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>${BASE_PACKAGE}</groupId>
            <artifactId>architecture-rules</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                            <goal>generate-code</goal>
                            <goal>generate-code-tests</goal>
                            <goal>native-image-agent</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

## application.properties

```properties
# Application Info
quarkus.application.name=${SERVICE_NAME}

# HTTP Port (change per service)
quarkus.http.port=${HTTP_PORT}

# Database (H2 for dev)
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:${SERVICE_NAME}
quarkus.hibernate-orm.database.generation=drop-and-create

# Production Database Profile
%prod.quarkus.datasource.db-kind=${DATABASE_TYPE}
%prod.quarkus.datasource.username=${env.DB_USERNAME:admin}
%prod.quarkus.datasource.password=${env.DB_PASSWORD:admin}
%prod.quarkus.datasource.jdbc.url=${env.DB_URL}
%prod.quarkus.hibernate-orm.database.generation=validate

# Logging with Trace Context
quarkus.log.console.format=%d{HH:mm:ss} %-5p traceId=%X{traceId} spanId=%X{spanId} [%c{2.}] (%t) %s%e%n

# Remote Service Profile (for microservice extraction)
%remote.inventory.service.url=http://localhost:8082
```

## Key Java Templates

### Domain Service Interface

```java
package ${BASE_PACKAGE}.${SERVICE_NAME}.domain.api;

import java.util.List;
import java.util.Optional;

public interface ${Entity}Service {
    List<${Entity}DTO> findAll();
    Optional<${Entity}DTO> findById(String id);
    ${Entity}DTO create(Create${Entity}Data data);
    ${Entity}DTO update(String id, Update${Entity}Data data);
    void delete(String id);
}
```

### REST Resource

```java
package ${BASE_PACKAGE}.${SERVICE_NAME}.infrastructure.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/${entity-plural}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ${Entity}Resource {

    @Inject
    ${Entity}Service service;

    @GET
    public List<${Entity}Response> findAll() {
        return service.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @POST
    public ${Entity}Response create(Create${Entity}Request request) {
        var data = toData(request);
        return toResponse(service.create(data));
    }
    
    // ... other methods
}
```

### Persistence Adapter

```java
package ${BASE_PACKAGE}.${SERVICE_NAME}.infrastructure.persistence.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ${Entity}PersistenceAdapter implements ${Entity}Port {

    @Inject
    ${Entity}Repository repository;

    @Override
    @Transactional
    public ${Entity}DTO save(${Entity}DTO dto) {
        var entity = toEntity(dto);
        repository.persist(entity);
        return toDTO(entity);
    }
}
```

## Variables Reference

| Variable | Description | Example |
|----------|-------------|---------|
| `${BASE_PACKAGE}` | Root package | `com.company.project` |
| `${SERVICE_NAME}` | Service name (kebab-case) | `commerce-service` |
| `${DOMAIN_NAME}` | Domain name | `order` |
| `${Entity}` | Entity name (PascalCase) | `Order` |
| `${entity-plural}` | Entity plural (kebab-case) | `orders` |
| `${HTTP_PORT}` | Service port | `8080` |
| `${DATABASE_TYPE}` | Database type | `postgresql` |
| `${BOM_VERSION}` | BOM version | `2026.02.0` |
