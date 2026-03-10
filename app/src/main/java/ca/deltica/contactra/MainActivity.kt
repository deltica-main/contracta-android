package ca.deltica.contactra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ca.deltica.contactra.ui.navigation.Screen
import ca.deltica.contactra.ui.screens.ContactDetailScreen
import ca.deltica.contactra.ui.screens.ContactListScreen
import ca.deltica.contactra.ui.screens.HomeScreen
import ca.deltica.contactra.ui.screens.MeetingContextScreen
import ca.deltica.contactra.ui.screens.ReviewScreen
import ca.deltica.contactra.ui.screens.ScanScreen
import ca.deltica.contactra.ui.screens.SettingsScreen
import ca.deltica.contactra.ui.screens.SupportScreen
import ca.deltica.contactra.ui.theme.ContactraTheme
import ca.deltica.contactra.ui.viewmodel.MainViewModel
import ca.deltica.contactra.ui.viewmodel.MainViewModelFactory

/**
 * The main activity hosts the root composable and sets up navigation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ContactraTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val mainViewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(context.applicationContext)
                )
                Surface(color = MaterialTheme.colorScheme.background) {
                    RootNavHost(navController = navController, mainViewModel)
                }
            }
        }
    }
}

@Composable
fun RootNavHost(navController: NavHostController, mainViewModel: MainViewModel) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController, viewModel = mainViewModel)
        }
        composable(
            route = Screen.Scan.route,
            arguments = listOf(
                navArgument(Screen.Scan.ARG_SOURCE) {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val source = backStackEntry.arguments?.getString(Screen.Scan.ARG_SOURCE)
            ScanScreen(
                navController = navController,
                viewModel = mainViewModel,
                launchGallery = source == "gallery"
            )
        }
        composable(Screen.Review.route) {
            ReviewScreen(navController = navController, viewModel = mainViewModel)
        }
        composable(Screen.MeetingContext.route) {
            MeetingContextScreen(navController = navController, viewModel = mainViewModel)
        }
        composable(
            route = Screen.ContactList.route,
            arguments = listOf(
                navArgument(Screen.ContactList.ARG_QUERY) {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString(Screen.ContactList.ARG_QUERY)
            ContactListScreen(
                navController = navController,
                viewModel = mainViewModel,
                initialQuery = query
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController, viewModel = mainViewModel)
        }
        composable(Screen.Support.route) {
            SupportScreen(navController = navController, viewModel = mainViewModel)
        }
        composable(
            route = Screen.ContactDetail.route,
            arguments = listOf(
                navArgument(Screen.ContactDetail.ARG_CONTACT_ID) { type = NavType.LongType }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "contactra://contact/{${Screen.ContactDetail.ARG_CONTACT_ID}}" }
            )
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getLong(Screen.ContactDetail.ARG_CONTACT_ID)
            ContactDetailScreen(navController = navController, viewModel = mainViewModel, contactId = contactId)
        }
    }
}
