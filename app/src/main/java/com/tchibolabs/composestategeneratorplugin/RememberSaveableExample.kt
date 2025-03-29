package com.tchibolabs.composestategeneratorplugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tchibolabs.forgetmenot.annotations.DefaultInject
import com.tchibolabs.forgetmenot.annotations.Key
import com.tchibolabs.forgetmenot.annotations.Provide
import com.tchibolabs.forgetmenot.annotations.Saveable
import com.tchibolabs.forgetmenot.annotations.RememberSaveable
import com.tchibolabs.forgetmenot.annotations.Value

@RememberSaveable
class RememberSaveableExampleState(
    @Value("10")
    @Saveable("index")
    @Key
    initialIndex: Int,
    @Provide("injectClass<User>")
    private val user: User,
    @Key
    @DefaultInject("100", "\"yellow\"")
    private val duck: Duck,
    @DefaultInject
    private val duck1: Duck,
) {
    @Saveable("index")
    var index by mutableIntStateOf(initialIndex)
        private set

    var myUser by mutableStateOf(user)
        private set

    var myDuck by mutableStateOf(duck)
        private set
}

@Composable
fun RememberSaveableExample(
    state: RememberSaveableExampleState = rememberRememberSaveableExampleState(),
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("Saveable Index: ${state.index}")
        Text("Saveable User: ${state.myUser}")
        Text("Saveable Duck: ${state.myDuck}")
    }
}