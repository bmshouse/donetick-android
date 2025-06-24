package com.donetick.app.ui.webview

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.donetick.app.R
import com.donetick.app.ui.components.ErrorMessage

/**
 * WebView screen for displaying DoneTick server interface
 */
@Composable
fun WebViewScreen(
    uiState: WebViewUiState,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onChoresClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WebViewTopBar(
                uiState = uiState,
                onRefresh = onRefresh,
                onMenuClick = onMenuClick,
                onChoresClick = onChoresClick
            )
        }
    ) { paddingValues ->
        WebViewContent(
            uiState = uiState,
            onRefresh = onRefresh,
            onWebViewCreated = onWebViewCreated,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewTopBar(
    uiState: WebViewUiState,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit,
    onChoresClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            // Chores button - only show if there are chores available
            if (uiState.choresList.isNotEmpty()) {
                IconButton(onClick = onChoresClick) {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = "View chores - Swipe left or tap"
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.menu_refresh)
                )
            }
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.menu_settings)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
    )
}

@Composable
private fun WebViewContent(
    uiState: WebViewUiState,
    onRefresh: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Loading Progress Bar
        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Content
        when {
            uiState.errorMessage != null -> {
                ErrorContent(
                    errorMessage = uiState.errorMessage,
                    onRetry = onRefresh
                )
            }
            
            uiState.serverUrl.isNotEmpty() -> {
                WebViewContainer(
                    onWebViewCreated = onWebViewCreated
                )
            }
            
            else -> {
                LoadingContent()
            }
        }
    }
}

@Composable
private fun ErrorContent(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        ErrorMessage(
            message = errorMessage,
            onRetry = onRetry,
            retryText = stringResource(R.string.retry)
        )
    }
}

@Composable
private fun WebViewContainer(
    onWebViewCreated: (WebView) -> Unit
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                onWebViewCreated(this)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
