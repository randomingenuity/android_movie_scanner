package com.movie.scanner.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.util.CsvExporter
import com.movie.scanner.util.ListLocationFilter
import com.movie.scanner.util.MovieListFormatter
import com.movie.scanner.util.ShareCsv
import kotlinx.coroutines.launch

private val ListRowHorizontalPadding = 12.dp
private val ListRowVerticalPadding = 8.dp
private val ListActionColumnWidth = 40.dp
private val ListDetailOverlayMaxHeight = 480.dp
private val ListDetailLabelWidth = 128.dp

/**
 * Saved-movie list with export, per-row TMDB open, and delete actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    viewModel: ListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allMovies = uiState.allMovies
    val displayedMovies = uiState.displayedMovies
    val showInitialLoading = uiState.isLoadingMovies && allMovies.isEmpty()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedMovie by remember { mutableStateOf<MovieEntity?>(null) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear list?") },
            text = { Text("Remove all ${allMovies.size} movies from the list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    selectedMovie?.let { movie ->
        ListMovieDetailOverlay(
            movie = movie,
            onDismiss = { selectedMovie = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("List") },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val exportMovies = viewModel.loadMoviesForExport()
                                ShareCsv.shareMoviesCsv(
                                    context = context,
                                    csvContent = CsvExporter.buildCsv(exportMovies),
                                    filename = CsvExporter.buildDefaultFilename(),
                                )
                            }
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export list")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (showInitialLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (allMovies.isEmpty()) {
                Text(
                    text = "No features yet. Click \"Scan\" to get started.",
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                ListLocationFilterField(
                    selectedDisplayLabel = ListLocationFilter.buildSelectedDisplayLabel(
                        selectedFilterKey = uiState.selectedLocationFilter,
                        options = uiState.locationFilterOptions,
                    ),
                    locationOptions = uiState.locationFilterOptions,
                    onLocationSelected = viewModel::selectLocationFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (displayedMovies.isEmpty()) {
                    Text(
                        text = "No movies at this location.",
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    val listState = rememberLazyListState()

                    LaunchedEffect(uiState.currentPageIndex) {
                        listState.scrollToItem(0)
                    }

                    ListHeaderRow()
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                    ) {
                        items(displayedMovies, key = { movie -> movie.id }) { movie ->
                            ListMovieRow(
                                movie = movie,
                                onRowClick = { selectedMovie = movie },
                                onOpenTmdb = {
                                    movie.tmdbUrl?.let { url -> uriHandler.openUri(url) }
                                },
                                onDelete = { viewModel.deleteMovie(movie.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                    if (uiState.totalPages > 1) {
                        ListPaginationBar(
                            pageRangeLabel = uiState.pageRangeLabel,
                            currentPageNumber = uiState.currentPageIndex + 1,
                            totalPages = uiState.totalPages,
                            hasPreviousPage = uiState.hasPreviousPage,
                            hasNextPage = uiState.hasNextPage,
                            onShowPreviousPage = viewModel::showPreviousPage,
                            onShowNextPage = viewModel::showNextPage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Button(
                onClick = { showClearDialog = true },
                enabled = allMovies.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Clear list")
            }
        }
    }
}

/**
 * Read-only location dropdown at the top of the list screen.
 */
@Composable
private fun ListLocationFilterField(
    selectedDisplayLabel: String,
    locationOptions: List<ListLocationFilter.Option>,
    onLocationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val menuInteractionSource = remember { MutableInteractionSource() }
    val dismissMenu = { menuExpanded = false }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedDisplayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Location") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = menuInteractionSource,
                    indication = null,
                    onClick = { menuExpanded = true },
                ),
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = dismissMenu,
        ) {
            locationOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.menuLabel) },
                    onClick = {
                        onLocationSelected(option.filterKey)
                        dismissMenu()
                    },
                )
            }
        }
    }
}

/**
 * Previous/next controls and page summary below the movie table.
 */
@Composable
private fun ListPaginationBar(
    pageRangeLabel: String,
    currentPageNumber: Int,
    totalPages: Int,
    hasPreviousPage: Boolean,
    hasNextPage: Boolean,
    onShowPreviousPage: () -> Unit,
    onShowNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onShowPreviousPage,
                enabled = hasPreviousPage,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                )
            }
            Text(
                text = "Page $currentPageNumber of $totalPages",
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = onShowNextPage,
                enabled = hasNextPage,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next page",
                )
            }
        }
        Text(
            text = pageRangeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ListHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ListRowHorizontalPadding,
                vertical = ListRowVerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Title",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "Year",
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        Box(modifier = Modifier.width(ListActionColumnWidth))
        Box(modifier = Modifier.width(ListActionColumnWidth))
    }
}

@Composable
private fun ListMovieRow(
    movie: MovieEntity,
    onRowClick: () -> Unit,
    onOpenTmdb: () -> Unit,
    onDelete: () -> Unit,
) {
    val hasTmdbUrl = !movie.tmdbUrl.isNullOrBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .padding(
                horizontal = ListRowHorizontalPadding,
                vertical = ListRowVerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = movie.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = movie.year,
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(
            onClick = onOpenTmdb,
            enabled = hasTmdbUrl,
            modifier = Modifier.size(ListActionColumnWidth),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open in TMDB",
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(ListActionColumnWidth),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Modal overlay showing every saved catalog field for one list row.
 */
@Composable
private fun ListMovieDetailOverlay(
    movie: MovieEntity,
    onDismiss: () -> Unit,
) {
    val detailFields = remember(movie) { MovieListFormatter.buildDetailFields(movie) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ListDetailOverlayMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    detailFields.forEachIndexed { index, (label, value) ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        ListMovieDetailFieldRow(
                            label = label,
                            value = value,
                        )
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * One label/value row in the list detail overlay table.
 */
@Composable
private fun ListMovieDetailFieldRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(ListDetailLabelWidth),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
