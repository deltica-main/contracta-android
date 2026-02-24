package com.example.businesscardscanner.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.businesscardscanner.R
import com.example.businesscardscanner.ui.components.AppBackground
import com.example.businesscardscanner.ui.components.AppCard
import com.example.businesscardscanner.ui.components.AppTextField
import com.example.businesscardscanner.ui.components.AppTopBar
import com.example.businesscardscanner.ui.components.EmptyState
import com.example.businesscardscanner.ui.components.GhostButton
import com.example.businesscardscanner.ui.components.InfoChip
import com.example.businesscardscanner.ui.components.LoadingState
import com.example.businesscardscanner.ui.components.PrimaryButton
import com.example.businesscardscanner.ui.components.SectionHeader
import com.example.businesscardscanner.ui.components.SecondaryButton
import com.example.businesscardscanner.ui.components.StatCard
import com.example.businesscardscanner.ui.navigation.Screen
import com.example.businesscardscanner.ui.theme.AppDimens
import com.example.businesscardscanner.ui.viewmodel.MainViewModel
import com.example.businesscardscanner.domain.model.industryDisplayLabel

@Composable
fun HomeScreen(navController: NavController, viewModel: MainViewModel) {
    val homeState by viewModel.homeUiState.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.brand_attribution),
                navController = navController,
                showBack = false,
                showHome = false,
                showContacts = true
            )
        }
    ) { paddingValues ->
        AppBackground(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = AppDimens.lg, vertical = AppDimens.lg),
                verticalArrangement = Arrangement.spacedBy(AppDimens.lg)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                    Text(
                        text = "Your contact workspace",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Scan cards, confirm details, and keep context in one calm flow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.lg)) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.xs)) {
                            Text(
                                text = "Add a new contact",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Use camera for live capture or import a saved card.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HomeAddContactActions(
                            onScanClick = { navController.navigate(Screen.Scan.createRoute()) },
                            onImportClick = { navController.navigate(Screen.Scan.createRoute("gallery")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                        Text(
                            text = "Find contacts fast",
                            style = MaterialTheme.typography.titleMedium
                        )
                        AppTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = "Search",
                            placeholder = "Name, company, or notes",
                            leadingIcon = Icons.Filled.Search,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.sm)
                        ) {
                            PrimaryButton(
                                text = "Search",
                                onClick = { navController.navigate(Screen.ContactList.createRoute(searchQuery)) },
                                modifier = Modifier.weight(1f)
                            )
                            SecondaryButton(
                                text = "All contacts",
                                onClick = { navController.navigate(Screen.ContactList.createRoute()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.md)
                ) {
                    StatCard(
                        label = "Total contacts",
                        value = if (homeState.isLoading) "-" else homeState.totalContacts.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Scans this week",
                        value = if (homeState.isLoading) "-" else homeState.scansThisWeek.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }

                SectionHeader(
                    title = "Recent contacts",
                    action = {
                        GhostButton(
                            text = "View all",
                            onClick = { navController.navigate(Screen.ContactList.createRoute()) }
                        )
                    }
                )

                AnimatedVisibility(
                    visible = homeState.isLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LoadingState(message = "Loading contacts...", modifier = Modifier.fillMaxWidth())
                }

                if (!homeState.isLoading) {
                    if (homeState.recentContacts.isEmpty()) {
                        EmptyState(
                            title = "No contacts yet",
                            subtitle = "Scan a business card to get started.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                            homeState.recentContacts.forEach { summary ->
                                AppCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        navController.navigate(
                                            Screen.ContactDetail.createRoute(summary.contact.id)
                                        )
                                    }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = summary.contact.name ?: "Unknown",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            summary.lastInteractionTime?.let { lastInteraction ->
                                                val relative = DateUtils.getRelativeTimeSpanString(
                                                    lastInteraction,
                                                    System.currentTimeMillis(),
                                                    DateUtils.MINUTE_IN_MILLIS
                                                )
                                                Text(
                                                    text = relative.toString(),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        summary.contact.company?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(AppDimens.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            summary.contact.industryDisplayLabel()?.let { InfoChip(label = it) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeAddContactActions(
    onScanClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val useStackedLayout = maxWidth < 360.dp
        if (useStackedLayout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimens.sm)
            ) {
                PrimaryButton(
                    text = "Scan card",
                    onClick = onScanClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_scan_button"),
                    icon = Icons.Filled.CameraAlt
                )
                SecondaryButton(
                    text = "Import",
                    onClick = onImportClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_import_button"),
                    icon = Icons.Filled.PhotoLibrary
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.sm)
            ) {
                PrimaryButton(
                    text = "Scan card",
                    onClick = onScanClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_scan_button"),
                    icon = Icons.Filled.CameraAlt
                )
                SecondaryButton(
                    text = "Import",
                    onClick = onImportClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_import_button"),
                    icon = Icons.Filled.PhotoLibrary
                )
            }
        }
    }
}
