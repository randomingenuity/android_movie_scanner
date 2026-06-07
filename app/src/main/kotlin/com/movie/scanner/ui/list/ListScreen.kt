package com.movie.scanner.ui.list

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.util.BarcodeDecoder
import com.movie.scanner.util.CsvExporter
import com.movie.scanner.util.ShareCsv
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    viewModel: ListViewModel = hiltViewModel(),
) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var detailMovie by remember { mutableStateOf<MovieEntity?>(null) }

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

    detailMovie?.let { movie ->
        AlertDialog(
            onDismissRequest = { detailMovie = null },
            title = { Text(movie.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Year: ${movie.year}")
                    if (!movie.upc.isNullOrBlank()) {
                        Text(BarcodeDecoder.formatBarcodeLine(movie.upc))
                    }
                    Text("No TMDB match")
                }
            },
            confirmButton = {
                TextButton(onClick = { detailMovie = null }) {
                    Text("Close")
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
                    text = "No movies yet. Scan a cover to get started.",
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(movies, key = { it.id }) { movie ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.deleteMovie(movie.id)
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            },
                            content = {
                                MovieRow(
                                    movie = movie,
                                    onClick = {
                                        if (movie.isForceAdded || movie.tmdbUrl.isNullOrBlank()) {
                                            detailMovie = movie
                                        } else {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(movie.tmdbUrl))
                                            context.startActivity(intent)
                                        }
                                    },
                                )
                            },
                        )
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
private fun MovieRow(
    movie: MovieEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = movie.posterUrl,
            contentDescription = movie.title,
            modifier = Modifier.size(48.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = movie.title, style = MaterialTheme.typography.bodyLarge)
            Text(text = movie.year, style = MaterialTheme.typography.bodyMedium)
            if (!movie.upc.isNullOrBlank()) {
                Text(
                    text = BarcodeDecoder.formatBarcodeLine(movie.upc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (movie.isForceAdded) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Unmatched") },
            )
        }
    }
}
