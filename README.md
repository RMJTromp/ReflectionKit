# ReflectionKit
ReflectionKit is a lightweight Java reflection library that makes reading fields, invoking methods, constructing objects, and navigating class hierarchies easier and cleaner.

**Required Java Version:** Java 8+

# Features
- Query by class or by instance
- Access private/protected fields and methods
- Constructor and method overload resolution
- Field/method annotation checks
- Class hierarchy, interfaces, and superclass navigation
- Optional-style safe field reads (`getOptional`)

# Installation (Maven)
Add the RMJTromp releases repository:

```xml
<repository>
  <id>rmjtromp-releases</id>
  <name>RMJTromp</name>
  <url>https://repo.rmjtromp.com/releases</url>
</repository>
```

Then add ReflectionKit:

```xml
<dependency>
  <groupId>com.rmjtromp</groupId>
  <artifactId>ReflectionKit</artifactId>
  <version>VERSION</version>
</dependency>
```

# Clone
```bash
git clone https://github.com/RMJTromp/ReflectionKit.git
cd ReflectionKit
```

# Build
```bash
mvn clean package
```

Run tests:
```bash
mvn test
```

# Quick Example
```java
import com.rmjtromp.ReflectionKit;

MyObject instance = new MyObject();

// Read private field
String value = ReflectionKit.query(instance)
    .field("myField")
    .get(String.class);

// Call private method
String result = ReflectionKit.query(instance)
    .method("greetUser")
    .call("John");

// Construct using private constructor
MyObject created = ReflectionKit.query(MyObject.class)
    .constructor()
    .newInstance();

// Chaining `instance.myField.toLowerCase()`
String loweredValue = ReflectionKit.query(instance)
    .field("myField").query()
    .method("toLowerCase").call();
```

# License
This project is subject to the [GNU General Public License v3.0](https://github.com/RMJTromp/ReflectionKit/blob/main/LICENSE). This does only apply for source code located directly in this clean repository.
For those who are unfamiliar with the license, here is a summary of its main points. This is by no means legal advice nor legally binding.
You are allowed to
- use
- share
- modify

this project entirely or partially for free and even commercially. However, please consider the following:

- **You must disclose the source code of your modified work and the source code you took from this project. This means you are not allowed to use code from this project (even partially) in a closed-source (or even obfuscated) application.**
- **Your modified application must also be licensed under the GPL**

Do the above and share your source code with everyone; just like we do.