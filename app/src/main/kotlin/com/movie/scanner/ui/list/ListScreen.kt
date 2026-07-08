package com.movie.scanner.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.util.CsvExporter
import com.movie.scanner.util.ShareCsv
import kotlinx.coroutines.launch

private val ListRowHorizontalPadding = 12.dp
private val ListRowVerticalPadding = 8.dp
private val ListActionColumnWidth = 40.dp

/**
 * Saved-movie list with export, per-row TMDB open, and delete actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    viewModel: ListViewModel = hiltViewModel(),
) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear list?") },
            text = { Text("Remove all ${movies.size} movies from the list?") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (movies.isEmpty()) {
                Text(
                    text = "No features yet. Click \"Scan\" to get started.",
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                ListHeaderRow()
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(movies, key = { movie -> movie.id }) { movie ->
                        ListMovieRow(
                            movie = movie,
                            onOpenTmdb = {
                                movie.tmdbUrl?.let { url -> uriHandler.openUri(url) }
                            },
                            onDelete = { viewModel.deleteMovie(movie.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
            Button(
                onClick = { showClearDialog = true },
                enabled = movies.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Clear list")
            }
        }
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
    onOpenTmdb: () -> Unit,
    onDelete: () -> Unit,
) {
    val hasTmdbUrl = !movie.tmdbUrl.isNullOrBlank()

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
