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
import com.tchibolabs.genrmbr.annotations.Inject
import com.tchibolabs.genrmbr.annotations.InjectCustom
import com.tchibolabs.genrmbr.annotations.Key
import com.tchibolabs.genrmbr.annotations.RememberSaveable
import com.tchibolabs.genrmbr.annotations.Saveable
import com.tchibolabs.genrmbr.annotations.Value

@RememberSaveable
class RememberSaveableExampleState(
    @Value("10")
    @Saveable("index")
    @Key
    initialIndex: Int,
    @Inject
    private val user: User,
    @Key
    @InjectCustom("injectClass<Duck>")
    private val duck: Duck,
    @Value("Duck()")
    val duck1: Duck,
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
        Text("Saveable Duck1: ${state.duck1}")
    }
}