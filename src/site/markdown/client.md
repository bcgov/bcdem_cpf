## Client Developer Guide
The Concurrent Processing Framework (CPF) provides a [REST Web Service API](cpf-api-app/rest-api/)
that can be used to develop client applications in Java, Python or another programming language to
submit batch jobs to the CPF and to download the results of those batch jobs. The API can also be
accessed directly in a web browser. JavaScript is not supported at this time.

### Security
Access to the REST web service API is limited to authorized users.

### Direct Web Browser Access
The [REST Web Service API](cpf-api-app/rest-api/) can be accessed directly using a web browser.
In this mode the service returns styled HTML pages and forms that canbe used without any programming.

### Java

The CPF provides a [Client API](cpf-api-client/java-api/) for the Java programming language. This
can be included in a [Maven](http://maven.apache.org) project using the following dependency in the
project's pom.xml file. The Java Client API can also be used in
[Other Build Systems](http://pauldaustin.github.io/cpf/cpf-api-client/dependency-info.html).

```xml
<project>
  <dependencies>
    <dependency>
      <groupId>ca.bc.gov.open.cpf</groupId>
      <artifactId>cpf-api-client</artifactId>
      <version>${project.version}</version>
    </dependency>
  </dependencies>
</project>
```
