package com.helper.beamnetworks

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.helper.beamnetworks.util.parseMpesaMessage
import java.util.regex.Pattern

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MessagePickerScreen(navController: NavController, viewModel: MessagePickerViewModel = viewModel()) {
    val smsPermissionState = rememberPermissionState(Manifest.permission.READ_SMS)
    val threads by viewModel.threads.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val context = LocalContext.current
    var selectedThreadId by remember { mutableStateOf<Long?>(null) }

    fun extractAmount(message: String): String? {
        // This regex looks for common keywords in transaction messages (case-insensitive)
        // and then captures the numerical amount that follows.
        // It handles amounts with commas and optional decimal places.
        // It prioritizes amounts linked to keywords like "sent", "paid", "amount", "cost".
        val pattern = Pattern.compile(
            "(?:\\b(sent|paid|amount|cost|transaction of|worth)\\b(?:\\s+of)?\\s+(?:Ksh|KES)?\\s*|\\b(Ksh|KES)\\.?\\s+)([\\d,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(message)

        if (matcher.find()) {
            // Group 3 should contain the number
            val amount = matcher.group(3)
            if(amount != null) {
                return amount.replace(",", "")
            }
        }

        // A simple fallback if the main pattern doesn't match
        // Tries to find a number that looks like a monetary value
        val fallbackPattern = Pattern.compile("([\\d,]+\\.\\d{2})")
        val fallbackMatcher = fallbackPattern.matcher(message)
        if(fallbackMatcher.find()){
            val amount = fallbackMatcher.group(1)
            if(amount != null){
                return amount.replace(",", "")
            }
        }

        return null
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (selectedThreadId == null) "Select a Sender" else "Select a Message") },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (selectedThreadId == null) {
                            navController.popBackStack()
                        } else {
                            selectedThreadId = null
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (smsPermissionState.status.isGranted) {
                if (selectedThreadId == null) {
                    LaunchedEffect(Unit) {
                        viewModel.fetchThreads(context.contentResolver)
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(threads) { thread ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable { selectedThreadId = thread.threadId },
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = thread.sender, fontWeight = FontWeight.Bold)
                                    Text(text = thread.lastMessage)
                                }
                            }
                        }
                    }
                } else {
                    LaunchedEffect(selectedThreadId) {
                        viewModel.fetchMessagesForThread(context.contentResolver, selectedThreadId!!)
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(messages) { message ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable {
                                        val parsed = parseMpesaMessage(message.body)
                                        val handle = navController.previousBackStackEntry?.savedStateHandle
                                        (parsed.amount ?: extractAmount(message.body))?.let { handle?.set("picked_amount", it) }
                                        parsed.paidTo?.let { handle?.set("picked_paid_to", it) }
                                        parsed.code?.let { handle?.set("picked_code", it) }
                                        parsed.date?.let { handle?.set("picked_date", it.toString()) }
                                        navController.popBackStack()
                                    },
                            ) {
                                Text(
                                    text = message.body,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SMS permission is required to read messages and pick an amount.")
                    Button(onClick = { smsPermissionState.launchPermissionRequest() }) {
                        Text("Request Permission")
                    }
                }
            }
        }
    }
}
