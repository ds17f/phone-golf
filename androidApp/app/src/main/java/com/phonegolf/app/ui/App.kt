package com.phonegolf.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phonegolf.app.Prefs

sealed interface Screen {
    /** The Round screen: the scorecard. */
    data object Home : Screen
    data object Settings : Screen
    data class Group(val id: String) : Screen
    data class Course(val id: String) : Screen
    data class GroupFlow(val join: Boolean) : Screen
}

/** Simple back stack. The app has a few screens, so no navigation library. */
class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun reset() { stack.clear(); stack.add(Screen.Home) }
}

@Composable
fun PhoneGolfApp() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    // Prefs is not observable. Bump [epoch] after any change of mode, login or solo data.
    var epoch by remember { mutableIntStateOf(0) }
    val nav = remember { Nav() }
    val changed: () -> Unit = { nav.reset(); epoch++ }
    val signedIn = remember(epoch) { prefs.isSignedIn }
    var welcomeGroup by remember { mutableStateOf(false) }

    if (!signedIn) {
        if (welcomeGroup) {
            BackHandler { welcomeGroup = false }
            GroupFlowScreen(prefs, startJoin = true, onBack = { welcomeGroup = false }, onDone = { welcomeGroup = false; changed() })
        } else {
            WelcomeScreen(prefs, onGroup = { welcomeGroup = true }, onDone = changed)
        }
        return
    }
    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
    key(epoch) {
        when (val s = nav.current) {
            Screen.Home -> RoundScreen(prefs, nav)
            Screen.Settings -> SettingsScreen(prefs, nav, onChanged = changed)
            is Screen.Group -> GroupScreen(prefs, nav, s.id)
            is Screen.Course -> CourseScreen(prefs, nav, s.id)
            is Screen.GroupFlow -> GroupFlowScreen(prefs, s.join, onBack = { nav.pop() }, onDone = changed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    nav: Nav,
    showSettings: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (nav.stack.size > 1) {
                        IconButton(onClick = { nav.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (showSettings) {
                        IconButton(onClick = { nav.push(Screen.Settings) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun SectionCard(title: String? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun Loading() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorText(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
}
