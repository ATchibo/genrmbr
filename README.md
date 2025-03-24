# genrmbr: a Compose states boilerplate code generator

genrmbr is a Kotlin library intended for generating remember and rememberSaveable composable functions. These functions are used for instantiating state classes of certain composable functions.

## Installation:

### Step 1: Add the JitPack repository to your build file 
Add it in your settings.gradle.kts at the end of repositories:
```
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
```
plugins {
    id("com.google.devtools.ksp") version <ksp-version>
}
```

### Step 3: Add the dependency
Add this to your module's build.gradle file:
```
dependencies {
  implementation("com.github.ATchibo:genrmbr:<version>")
  ksp("com.github.ATchibo:genrmbr:<version>")
}
```

### Step 4 (optional): Add support for Koin
Add this to your module's build.gradle:
```
android {
    ...
    defaultConfig {
        ...
        ksp {
            arg("genrmbr.injectionType", "koin")
        }
    }
}
```

## Example Usages
We have our composable with a state:
```
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
```
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
```
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
```
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
```
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
