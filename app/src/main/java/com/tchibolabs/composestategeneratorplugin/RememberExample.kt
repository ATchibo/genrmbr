package com.tchibolabs.composestategeneratorplugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tchibolabs.genrmbr.defaultval.DefaultCoroutineScope
import com.tchibolabs.genrmbr.defaultval.DefaultCustom
import com.tchibolabs.genrmbr.defaultval.DefaultInject
import com.tchibolabs.genrmbr.defaultval.DefaultInt
import com.tchibolabs.genrmbr.invalidate.InvalidateRemember
import com.tchibolabs.genrmbr.remembered.Remembered
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Remembered(injector = "injectClass")
class RememberExampleState(
    @DefaultInt(10)
    @InvalidateRemember
    initialIndex: Int,
    @DefaultCustom("injectClass<User>")
    @InvalidateRemember
    private val user: User,
    @DefaultInject
    private val duck: Duck,
    @DefaultCoroutineScope
    private val coroutineScope: CoroutineScope,
) {
    var index by mutableIntStateOf(initialIndex)
        private set

    var myUser by mutableStateOf(user)
        private set

    var myDuck by mutableStateOf(duck)
        private set

    init {
        coroutineScope.launch {  }
    }
}

inline fun <reified T> injectClass(): T = when {
    T::class == User::class -> User() as T
    else -> Duck() as T
}

@Composable
fun RememberExample(
    state: RememberExampleState = rememberRememberExampleState(),
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