<p align="center">
  <img src="https://github.com/user-attachments/assets/ae0daaf5-e5ec-4337-820c-4a5fba486f61" height=256 width=256>
</p>

# forgetmenot: a Compose states boilerplate code generator

forgetmenot is a Kotlin library intended for generating remember and rememberSaveable composable functions. These functions are used for instantiating state classes of certain composable functions.

## Installation:

### Step 1: Add the JitPack repository to your build file 
Add it in your settings.gradle.kts at the end of repositories:
```gradle
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}
```

### Step 2: Add KSP
Add this to your module's build.gradle file. You can also check [the quickstart guide](https://kotlinlang.org/docs/ksp-quickstart.html):
```gradle
plugins {
    id("com.google.devtools.ksp") version <ksp-version>
}
```

### Step 3: Add the dependency
Add this to your module's build.gradle file:
```gradle
dependencies {
  implementation("com.github.ATchibo:forgetmenot:<version>")
  ksp("com.github.ATchibo:forgetmenot:<version>")
}
```

### Step 4 (optional): Add support for Koin
Add this to your module's build.gradle:
```gradle
android {
    ...
    defaultConfig {
        ...
        ksp {
            arg("forgetmenot.injectionType", "koin")
        }
    }
}
```

## Example Usages
We have our composable with a state:
```kotlin
class ExampleState(
    initialIndex: Int,
    private val user: User,
    private val duck: Duck,
    private val getAgencies: GetAgencies,
    private val coroutineScope: CoroutineScope,
) {
    var index by mutableIntStateOf(initialIndex)
        private set

    var myUser by mutableStateOf(user)
        private set

    var myDuck by mutableStateOf(duck)
        private set

    init {
      getAgencies().onEach {
          // do something
      }.launchIn(coroutineScope)
    }
}

@Composable
fun Example(
    state: ExampleState,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("Index: ${state.index}")
        Text("User: ${state.myUser}")
        Text("Duck: ${state.myDuck}")
    }
}
```
with these data classes:
```kotlin
data class Duck(
    val speed: Int = 300,
    val color: String = "white",
)

data class User(
    val name: String = "Username",
    val age: Int = 95
)
```
and this usecase:
```kotlin
fun interface GetAgencies {
    operator fun invoke(): Flow<List<Agency>>
}

class GetAgenciesUseCase(
    private val agencyRepository: AgencyRepository,
) : GetAgencies {
    override fun invoke(): Flow<List<Agency>> =
        agencyRepository.getAgencies()
}
```

In order to create a remembered instance of ```RememberExampleState```, you would normally need to write something like this:
```kotlin
@Composable
fun rememberExampleState(
    initialIndex:Int,
    user: User,
    duck: Duck,
    getAgencies: GetAgencies,  // this needs to be injected (e.g. with koinInject())
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): ExampleState {
    return remember(initialIndex) { ExampleState(initialIndex, user, duck, getAgencies, coroutineScope) }
}
```

This is not so bad, but for states with a lot of parameters, this can grow big quickly. Not to mention what happens when you want to generate a rememberSaveable function:
```kotlin
fun getExampleStateSaver(
    user: User,
    duck: Duck,
    getAgencies: GetAgencies,
    coroutineScope: CoroutineScope,
): Saver<ExampleState, *> = mapSaver(
    save = {
        mapOf(
            "index" to it.index
        )
    },
    restore = {
        ExampleState(
            initialIndex = it["index"] as kotlin.Int,
            user = user,
            duck = duck,
            getAgencies = getAgencies,
            coroutineScope = coroutineScope,
        )
    }
)

@Composable
fun rememberExampleState(
    initialIndex:Int,
    user: User,
    duck: Duck,
    getAgencies: GetAgencies,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): ExampleState {
    return rememberSaveable(
      user, duck,
      saver = getExampleStateSaver(user, duck, getAgencies, coroutineScope)
    ) {
      ExampleState(initialIndex, user, duck, getAgencies, coroutineScope)
    }
}
```

With this library, you only need some annotations to generate all this!
You can check ```RememberExample.kt``` and ```RememberSaveableExample.kt``` to see how the annotations can be used.

### Annotations Explained
This library contains the following annotations:
- ```@Remember```
- ```@RememberSaveable```
- ```@Value```
- ```@DefaultInject```
- ```@Provide```
- ```@Key```
- ```@Saveable```

### @Remember
This annotation is put on the state class. Can receive a custom injector function as a parameter.
The following code snippet:
```kotlin
@Remember
class ExampleState (
  val nr: Int,
  val user: User,
)
```
will generate:
```kotlin
@Composable
fun rememberExampleState(
    nr: kotlin.Int,
    user: <path>.User,
): ExampleState {
    return remember { ExampleState(nr, user) }
}
```

### @RememberSaveable
This annotation is put on the state class. Can receive a custom injector function as a parameter.
The following code snippet:
```kotlin
@RememberSaveable
class ExampleState (
  val nr: Int,
  val user: User,
)
```
will generate:
```kotlin
fun getExampleStateSaver(
    nr: kotlin.Int,
    user: <path>.User,
): Saver<ExampleState, *> = mapSaver(
    save = { mapOf()},
    restore = {
        ExampleState(
            nr = nr,
            user = user,
        )
    }
)
```
and
```kotlin
@Composable
fun rememberExampleState(
    nr: kotlin.Int,
    user: <path>.User,
): ExampleState {
    return rememberSaveable(
        saver = getExampleStateSaver(nr = nr, user = user)
    ) { ExampleState(nr, user) }
}
// this is not very useful but by using @Saveable you can make it powerful
```

### @Value
This is the simplest annotation. This is used on a class parameter in order to give it a default value in the remember<className>() function.
The following code snippet:
```kotlin
@Remember
class ExampleState (
  val nr: Int,
  @Value("10") val nrWithDefault: Int
)
```
will generate:
```kotlin
@Composable
fun rememberExampleState(
    nr: kotlin.Int,
    nrWithDefault: kotlin.Int = 10,
): ExampleState {
    return remember { ExampleState(nr, nrWithDefault) }
}
```

### @DefaultInject
This is used on a class parameter in order to give it a default value in the remember<className>() function. It should be used only if you configured default injection with Koin or if you set a default inject function as a parameter for ```@Remember``` or ```@RememberSaveable```.
The annotation parameter function has higher priority over default project injection. ```CoroutineScope``` is always injected by default (no annotation needed).
The following code snippet:
```kotlin
inline fun <reified T> injectClass(): T = when {
    T::class == User::class -> User() as T
    else -> Duck() as T
}

@Remember(injector = "injectClass")
class ExampleState(
    @Value("10")
    initialIndex: Int,
    @DefaultInject
    private val user: User,
    @DefaultInject
    private val duck: Duck,
    private val coroutineScope: CoroutineScope,
) {
```
will generate the following code even if you have set default project injection:
```kotlin
@Composable
fun rememberExampleState(
    initialIndex: kotlin.Int = 10,
    user: User = injectClass<User>(),
    duck: Duck = injectClass<Duck>(),
    coroutineScope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()
): ExampleState {
    return remember { ExampleState(initialIndex, user, duck, coroutineScope) }
}
```
If you do not pass an injector for ```@Remember``` or ```@RememberSaveable``` and you use Koin injection, the generated code will look like this:
```kotlin
@Composable
fun rememberExampleState(
    initialIndex: kotlin.Int = 10,
    user: User = koinInject(),
    duck: Duck = koinInject(),
    coroutineScope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()
): ExampleState {
    return remember { ExampleState(initialIndex, user, duck, coroutineScope) }
}
```
You can pass arguments to ```@DefaultInject``` which will be used by the injector function (works for both cases):
```kotlin
@Remember(injector = "injectClass")
class ExampleState(
    @Value("10")
    initialIndex: Int,
    @DefaultInject
    private val user: User,
    @DefaultInject("\"Yellow\"", "true")
    private val duck: Duck,
    private val coroutineScope: CoroutineScope,
) {
```
will generate:
```kotlin
@Composable
fun rememberExampleState(
    initialIndex: kotlin.Int = 10,
    user: User = injectClass<User>(),
    duck: Duck = injectClass<Duck>("Yellow", 10),
    coroutineScope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()
): ExampleState {
    return remember { ExampleState(initialIndex, user, duck, coroutineScope) }
}
```

### @Provide
This is a combination of ```@DefaultInject``` and an injector function. You pass a method name and that method will be called in order to give your parameter a default value.
The following code snippet:
```kotlin
inline fun <reified T> injectClass(): T = when {
    T::class == User::class -> User() as T
    else -> Duck() as T
}

@Remember(injector = "injectClass")
class ExampleState(
    @Value("10")
    initialIndex: Int,
    @DefaultInject
    private val user: User,
    @Provide("injectClass<Duck>")
    private val duck: Duck,
) {
```
will generate the following code ignoring any other injector:
```kotlin
@Composable
fun rememberExampleState(
    initialIndex: kotlin.Int = 10,
    user: User = injectClass<User>(),
    duck: Duck = injectClass<Duck>(),
): ExampleState {
    return remember { ExampleState(initialIndex, user, duck) }
}
```

### @Key
You can annotate a class parameter in order to make ```remember``` and ```rememberSaveable``` invalidate when this value changes.
The following code snippet:
```kotlin
@Remember
class ExampleState (
  @Key val nr1: Int,
  @Key val nr2: Int,
  @Value("10") val nrWithDefault: Int
)
```
will generate:
```kotlin
@Composable
fun rememberExampleState(
    nr1: kotlin.Int,
    nr2: kotlin.Int,
    nrWithDefault: kotlin.Int = 10,
): ExampleState {
    return remember(nr1, nr2) { ExampleState(nr1, nr2, nrWithDefault) }
}
```

### @Saveable
This annotation is designed only for classes annotated with ```@RememberSaveable``` and it is used in pairs. You should put ```@Saveable(<key>)``` on both the class parameter and the property which will use this default value. Make sure they have the same key.
The following code snippet:
```kotlin
@RememberSaveable
class ExampleState (
  @Key val nr: Int,
  @Saveable("user") initialUser: User,
) {
  @Saveable("user")
  var user by mutableStateOf(initialUser)
    private set
}
```
will generate:
```kotlin
fun getExampleStateSaver(
    nr: kotlin.Int,  // initialUser is no longer added as a saver parameter
): Saver<ExampleState, *> = mapSaver(
    save = { mapOf("user" to it.user)},
    restore = {
        ExampleState(
            nr = nr,
            initialUser = it["user"] as User,
        )
    }
)
```
and
```kotlin
@Composable
fun rememberExampleState(
    nr: kotlin.Int,
    user: <path>.User,
): ExampleState {
    return rememberSaveable(
        nr,
        saver = getExampleStateSaver(nr = nr)
    ) { ExampleState(nr, user) }
}
// this is not very useful but by using @Saveable you can make it powerful
```
