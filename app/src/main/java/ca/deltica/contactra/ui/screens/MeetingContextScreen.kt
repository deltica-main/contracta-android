package ca.deltica.contactra.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import ca.deltica.contactra.domain.logic.ConnectionCatalog
import ca.deltica.contactra.domain.logic.IndustryCatalog
import ca.deltica.contactra.ui.components.AnchoredDropdownField
import ca.deltica.contactra.ui.components.AppBackground
import ca.deltica.contactra.ui.components.AppCard
import ca.deltica.contactra.ui.components.AppTextField
import ca.deltica.contactra.ui.components.AppTopBar
import ca.deltica.contactra.ui.components.IndustrySelector
import ca.deltica.contactra.ui.components.LoadingState
import ca.deltica.contactra.ui.components.PrimaryButton
import ca.deltica.contactra.ui.components.SecondaryButton
import ca.deltica.contactra.ui.components.StepIndicator
import ca.deltica.contactra.ui.navigation.Screen
import ca.deltica.contactra.ui.theme.AppDimens
import ca.deltica.contactra.ui.theme.AppTypeTokens
import ca.deltica.contactra.ui.viewmodel.MainViewModel
import ca.deltica.contactra.domain.logic.IndustrySource
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingContextScreen(navController: NavController, viewModel: MainViewModel) {
    val meetingState by viewModel.meetingContextUiState.collectAsState()
    val scanState by viewModel.scanUiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var relationshipExpanded by remember { mutableStateOf(false) }
    var industryExpanded by remember { mutableStateOf(false) }
    val selectedRelationshipOption = if (meetingState.relationship in ConnectionCatalog.options) {
        meetingState.relationship
    } else {
        ConnectionCatalog.OTHER
    }
    val isCustomRelationship = selectedRelationshipOption == ConnectionCatalog.OTHER

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            viewModel.prepareMeetingContext(useCurrentLocation = true)
        } else {
            viewModel.updateMeetingContext { it.copy(locationError = "Location permission denied.") }
        }
    }

    val dayFormatter = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Details",
                subtitle = "Step 3 of 3",
                navController = navController,
                showBack = true,
                showHome = true,
                showContacts = true
            )
        }
    ) { paddingValues ->
        AppBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.lg)
            ) {
                StepIndicator(steps = listOf("Scan", "Review", "Context"), currentIndex = 2)

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                        Text(
                            text = "Meeting details",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Optional context for this contact.",
                            style = AppTypeTokens.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppTextField(
                            value = meetingState.location,
                            onValueChange = { value ->
                                viewModel.updateMeetingContext { it.copy(location = value) }
                            },
                            label = "Location",
                            placeholder = "Cafe or office",
                            leadingIcon = Icons.Filled.LocationOn,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SecondaryButton(
                            text = if (meetingState.isLocating) "Locating..." else "Use current location",
                            onClick = {
                                val fineGranted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val coarseGranted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (fineGranted || coarseGranted) {
                                    viewModel.prepareMeetingContext(useCurrentLocation = true)
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !meetingState.isLocating
                        )
                        meetingState.locationError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        AnchoredDropdownField(
                            label = "Connection",
                            value = selectedRelationshipOption,
                            options = ConnectionCatalog.options,
                            expanded = relationshipExpanded,
                            onExpandedChange = { relationshipExpanded = it },
                            onOptionSelected = { option ->
                                if (option == ConnectionCatalog.OTHER) {
                                    if (meetingState.relationship in ConnectionCatalog.options) {
                                        viewModel.updateMeetingContext {
                                            it.copy(relationship = ConnectionCatalog.OTHER)
                                        }
                                    }
                                } else {
                                    viewModel.updateMeetingContext { it.copy(relationship = option) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            anchorTestTag = "connection_dropdown_anchor",
                            menuTestTag = "connection_dropdown_menu"
                        )
                        if (isCustomRelationship) {
                            AppTextField(
                                value = if (
                                    meetingState.relationship.equals(
                                        ConnectionCatalog.OTHER,
                                        ignoreCase = true
                                    )
                                ) {
                                    ""
                                } else {
                                    meetingState.relationship
                                },
                                onValueChange = { value ->
                                    viewModel.updateMeetingContext { it.copy(relationship = value) }
                                },
                                label = "Custom connection",
                                placeholder = "Advisor, Investor",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_connection_input")
                            )
                        }
                        IndustrySelector(
                            value = scanState.reviewFields.industry,
                            customValue = scanState.reviewFields.industryCustom,
                            onValueChange = { value ->
                                viewModel.updateReviewFields { current ->
                                    current.copy(
                                        industry = value,
                                        industryCustom = if (value.equals("Other", ignoreCase = true)) {
                                            current.industryCustom
                                        } else {
                                            ""
                                        },
                                        industrySource = IndustrySource.normalizeForDraft(
                                            industry = value,
                                            source = IndustrySource.USER_SELECTED
                                        )
                                    )
                                }
                            },
                            onCustomValueChange = { custom ->
                                viewModel.updateReviewFields { current ->
                                    current.copy(
                                        industryCustom = custom,
                                        industrySource = IndustrySource.normalizeForDraft(
                                            industry = current.industry,
                                            source = IndustrySource.USER_SELECTED
                                        )
                                    )
                                }
                            },
                            options = IndustryCatalog.manualSelectionIndustries,
                            label = "Industry",
                            expanded = industryExpanded,
                            onExpandedChange = { industryExpanded = it }
                        )
                        when (scanState.reviewFields.industrySource) {
                            IndustrySource.ENRICHMENT_INFERRED -> Text(
                                text = "Suggested based on website",
                                style = AppTypeTokens.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IndustrySource.HEURISTIC_INFERRED -> Text(
                                text = "Suggested based on company",
                                style = AppTypeTokens.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                        Text(
                            text = "Follow-up note",
                            style = MaterialTheme.typography.titleMedium
                        )
                        AppTextField(
                            value = meetingState.notes,
                            onValueChange = { value ->
                                viewModel.updateMeetingContext { it.copy(notes = value) }
                            },
                            label = "Notes",
                            placeholder = "Follow up in March",
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false
                        )
                        Text(
                            text = "Saved on ${dayFormatter.format(System.currentTimeMillis())}",
                            style = AppTypeTokens.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = AppDimens.xs)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = meetingState.isSaving,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LoadingState(message = "Saving contact...", modifier = Modifier.fillMaxWidth())
                }

                PrimaryButton(
                    text = if (meetingState.isSaving) "Saving..." else "Save contact",
                    onClick = {
                        scope.launch {
                            val success = viewModel.saveContactAndInteraction()
                            if (success) {
                                navController.navigate(Screen.ContactList.createRoute()) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !meetingState.isSaving
                )
                meetingState.saveError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
