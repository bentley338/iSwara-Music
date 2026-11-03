package id.iswara.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class AppTab(val id: Int, val title: String, val icon: ImageVector)
@Composable fun appTabs() = listOf(
    AppTab(0, tr("Beranda", "Home"), Icons.Rounded.Home),
    AppTab(1, tr("Cari", "Search"), Icons.Rounded.Search),
    AppTab(6, "Radio", Icons.Rounded.Radio),
    AppTab(2, tr("Koleksi", "Library"), Icons.Rounded.LibraryMusic),
    AppTab(5, tr("Ruang", "Space"), Icons.Rounded.DashboardCustomize),
)

@Composable fun AppBottomNavigation(tab: Int, navigate: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, modifier = Modifier.testTag("bottom-navigation")) {
        appTabs().forEach { destination -> NavigationBarItem(selected = (when(tab){3->5;4->0;else->tab}) == destination.id,
            onClick = { navigate(destination.id) }, icon = { Icon(destination.icon, null) },
            label = { Text(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh, selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary)) }
    }
}

@Composable fun AppSideNavigation(tab: Int, navigate: (Int) -> Unit, settings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.width(112.dp).fillMaxHeight().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical))
            .verticalScroll(rememberScrollState()).padding(vertical = 16.dp).testTag("side-navigation"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("iSwara", Modifier.padding(horizontal = 12.dp, vertical = 16.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            (appTabs() + AppTab(4,tr("Jelajah","Explore"),Icons.Rounded.Explore) + AppTab(3, tr("Akun", "Account"), Icons.Rounded.AccountCircle)).forEach { destination ->
                NavigationRailItem(selected = tab == destination.id, onClick = { navigate(destination.id) }, icon = { Icon(destination.icon, null) },
                    label = { Text(destination.title, maxLines = 2, style = MaterialTheme.typography.labelMedium) })
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            NavigationRailItem(selected = false, onClick = settings, icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text(tr("Setelan", "Settings"), style = MaterialTheme.typography.labelMedium) })
        }
    }
}
