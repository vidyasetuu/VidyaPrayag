package com.littlebridge.vidyaprayag.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun BaseScreen(
    onBackClick: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            EduTrustDrawerSheet()
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                EduTrustTopBar(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBackClick = onBackClick
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}
