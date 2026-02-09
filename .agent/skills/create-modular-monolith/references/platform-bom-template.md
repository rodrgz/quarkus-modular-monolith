# Platform BOM Template

Template for `platform-bom/pom.xml` - the single source of truth for all versions.

## Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>${BASE_PACKAGE}</groupId>
    <artifactId>platform-bom</artifactId>
    <version>${BOM_VERSION}</version>
    <packaging>pom</packaging>

    <name>Platform BOM</name>
    <description>Bill of Materials for centralized dependency version management</description>

    <properties>
        <!-- Build Config -->
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- Framework Versions -->
        <quarkus.version>3.31.2</quarkus.version>
        
        <!-- Internal Module Versions -->
        <architecture-rules.version>1.0.0</architecture-rules.version>
        <!-- Add shared domain module versions here -->
        
        <!-- Test Library Versions -->
        <archunit.version>1.4.1</archunit.version>
        <mockito.version>5.14.2</mockito.version>
        <assertj.version>3.26.3</assertj.version>
        
        <!-- Plugin Versions -->
        <maven-compiler-plugin.version>3.13.0</maven-compiler-plugin.version>
        <maven-surefire-plugin.version>3.5.2</maven-surefire-plugin.version>
        <jandex-maven-plugin.version>3.2.0</jandex-maven-plugin.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- 1. Framework BOMs -->
            <dependency>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-bom</artifactId>
                <version>${quarkus.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 2. Test Libraries -->
            <dependency>
                <groupId>com.tngtech.archunit</groupId>
                <artifactId>archunit-junit5</artifactId>
                <version>${archunit.version}</version>
            </dependency>
            
            <dependency>
                <groupId>org.mockito</groupId>
                <artifactId>mockito-core</artifactId>
                <version>${mockito.version}</version>
            </dependency>
            
            <dependency>
                <groupId>org.assertj</groupId>
                <artifactId>assertj-core</artifactId>
                <version>${assertj.version}</version>
            </dependency>
            
            <!-- 3. Architecture Rules -->
            <dependency>
                <groupId>${BASE_PACKAGE}</groupId>
                <artifactId>architecture-rules</artifactId>
                <version>${architecture-rules.version}</version>
            </dependency>

            <!-- 4. Shared Internal Modules -->
            <!-- Add shared domain modules here -->
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${maven-surefire-plugin.version}</version>
                <configuration>
                    <systemPropertyVariables>
                        <archunit.target.package>${project.groupId}</archunit.target.package>
                    </systemPropertyVariables>
                    <dependenciesToScan>
                        <dependency>${BASE_PACKAGE}:architecture-rules</dependency>
                    </dependenciesToScan>
                </configuration>
            </plugin>
        </plugins>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>${maven-compiler-plugin.version}</version>
                    <configuration>
                        <source>${maven.compiler.source}</source>
                        <target>${maven.compiler.target}</target>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>${maven-surefire-plugin.version}</version>
                    <configuration>
                        <systemPropertyVariables>
                            <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
                            <maven.home>${maven.home}</maven.home>
                        </systemPropertyVariables>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>io.smallrye</groupId>
                    <artifactId>jandex-maven-plugin</artifactId>
                    <version>${jandex-maven-plugin.version}</version>
                </plugin>
                <plugin>
                    <groupId>io.quarkus.platform</groupId>
                    <artifactId>quarkus-maven-plugin</artifactId>
                    <version>${quarkus.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

## Variables to Replace

| Variable | Description | Example |
|----------|-------------|---------|
| `${BASE_PACKAGE}` | Base package/groupId | `com.company.project` |
| `${BOM_VERSION}` | BOM version (CalVer recommended) | `2026.02.0` |

## Adding Shared Domain Modules

When creating shared modules, add to properties and dependencyManagement:

```xml
<!-- In properties -->
<inventory-domain-module.version>1.0.0-SNAPSHOT</inventory-domain-module.version>

<!-- In dependencyManagement -->
<dependency>
    <groupId>${BASE_PACKAGE}.commerce</groupId>
    <artifactId>inventory-domain-module</artifactId>
    <version>${inventory-domain-module.version}</version>
</dependency>
```
