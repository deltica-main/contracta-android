package ca.deltica.contactra.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import ca.deltica.contactra.ui.navigation.Screen
import ca.deltica.contactra.ui.theme.AppDimens
import ca.deltica.contactra.ui.theme.AppTypeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    navController: NavController,
    subtitle: String? = null,
    showBack: Boolean = true,
    showHome: Boolean = true,
    showContacts: Boolean = true,
    showSettings: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = AppTypeTokens.screenTitle,
                    fontWeight = FontWeight.Medium
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(AppDimens.xs))
                    Text(
                        text = subtitle,
                        style = AppTypeTokens.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        navigationIcon = {
            if (showBack && navController.previousBackStackEntry != null) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(AppDimens.iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = {
            if (showHome) {
                IconButton(
                    onClick = {
                        navController.navigate(Screen.Home.route) {
                            launchSingleTop = true
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    },
                    modifier = Modifier.size(AppDimens.iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Home"
                    )
                }
            }
            if (showContacts) {
                IconButton(
                    onClick = {
                        navController.navigate(Screen.ContactList.createRoute()) {
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.size(AppDimens.iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PeopleAlt,
                        contentDescription = "Contacts"
                    )
                }
            }
            if (showSettings) {
                IconButton(
                    onClick = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.size(AppDimens.iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
            actions()
        }
    )
}
