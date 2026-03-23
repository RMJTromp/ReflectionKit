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

# Reflection Proxy Mapping

Map opaque objects to clean abstract proxy classes. Fields are eagerly populated, and abstract methods are delegated to the target.

```java
import com.rmjtromp.ReflectionKit;
import com.rmjtromp.reflection.mapping.*;

// Given some internal class you can't modify:
// class CraftPlayer {
//     private int hp = 20;
//     private boolean alive = true;
//     public void printHp() { System.out.println(hp); }
// }

@Proxy
abstract class Player extends MappedBase {
    // Field mapped directly by name
    int hp;

    // Field mapped via alias (proxy field name differs from target field name)
    @Alias("hp")
    int health;

    // Abstract methods delegate to target methods
    abstract void printHp();

    // Getter resolves to target field "hp" (tries method first, falls back to field)
    abstract int getHp();

    // Setter resolves to target field "hp"
    abstract void setHp(int value);

    // @FieldAccess skips method lookup, goes straight to field
    @FieldAccess
    abstract boolean isAlive();

    // @Alias on getter/setter controls which field name to look up
    @FieldAccess
    @Alias("hp")
    abstract int getHealth();
}

CraftPlayer internal = new CraftPlayer();
Player player = ReflectionKit.map(internal).to(Player.class);

player.hp;           // 20 (direct field)
player.getHp();      // 20 (getter -> field fallback)
player.setHp(10);    // sets target's "hp" field to 10
player.isAlive();    // true (field access, "is" prefix -> "alive" field)
player.getHealth();  // 10 (@Alias("hp") -> reads "hp" field)
player.printHp();    // delegates to CraftPlayer.printHp()
```

## Getter/Setter Resolution

Methods with `get`/`is`/`has` prefix (0 params, non-void return) and `set` prefix (1 param) are treated as potential field accessors:

1. **Default** — tries to find a matching method on the target first, falls back to field lookup
2. **`@FieldAccess`** — skips method lookup, resolves directly to a field
3. **`@Alias("name")`** — overrides which field/method name(s) to search for

## Version-Specific Method Dispatch

Abstract methods are resolved lazily — only when actually called. This lets you write concrete methods that branch between abstract methods that may or may not exist on the target, without errors at proxy creation time.

```java
@Proxy
abstract class Player {
    @Alias("teleportTo")
    protected abstract void legacyTeleport(Object location);

    @Alias("teleportPlayer")
    protected abstract void newerTeleport(Object player);

    // Concrete method — only the called branch needs to resolve
    public void teleport(Object obj, boolean legacy) {
        if (legacy) {
            legacyTeleport(obj);   // only resolves "teleportTo" if called
        } else {
            newerTeleport(obj);    // only resolves "teleportPlayer" if called
        }
    }
}

Player player = ReflectionKit.map(craftPlayer).to(Player.class);
player.teleport(location, true);  // works if target has teleportTo()
player.teleport(location, false); // works if target has teleportPlayer()
```

# License
This project is subject to the [GNU General Public License v3.0](./LICENSE). This does only apply for source code located directly in this clean repository.
For those who are unfamiliar with the license, here is a summary of its main points. This is by no means legal advice nor legally binding.
You are allowed to
- use
- share
- modify

this project entirely or partially for free and even commercially. However, please consider the following:

- **You must disclose the source code of your modified work and the source code you took from this project. This means you are not allowed to use code from this project (even partially) in a closed-source (or even obfuscated) application.**
- **Your modified application must also be licensed under the GPL**

Do the above and share your source code with everyone; just like we do.