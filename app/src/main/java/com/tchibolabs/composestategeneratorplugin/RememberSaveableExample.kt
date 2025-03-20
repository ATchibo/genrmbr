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
import com.tchibolabs.genrmbr.defaultval.DefaultCustom
import com.tchibolabs.genrmbr.defaultval.DefaultInject
import com.tchibolabs.genrmbr.defaultval.DefaultInt
import com.tchibolabs.genrmbr.remembered.Remembered
import com.tchibolabs.genrmbr.rememberedsaveable.RememberSaveable
import com.tchibolabs.genrmbr.rememberedsaveable.SaveableField

@RememberSaveable
class RememberSaveableExampleState(
    @DefaultInt(10)
    @SaveableField("index")
    initialIndex: Int,
    @DefaultCustom("injectClass<User>")
    private val user: User,
    @DefaultCustom("injectClass<Duck>")
    private val duck: Duck,
) {
    @SaveableField("index")
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