package com.example.dreamnet.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.dreamnet.data.DiaryEntryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.platform.LocalContext
import com.example.dreamnet.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(
    viewModel: DiaryViewModel,
    entry: DiaryEntryEntity? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var content by remember { mutableStateOf(entry?.content ?: "") }
    var date by remember { mutableStateOf(entry?.date ?: SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entry == null) stringResource(R.string.diary_new_entry) else stringResource(R.string.diary_edit_entry), color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back), tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (content.isNotBlank()) {
                            if (entry == null) {
                                viewModel.saveEntry(title, content, date)
                            } else {
                                viewModel.updateEntry(entry.copy(title = title, content = content, date = date))
                            }
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.diary_save), tint = Color(0xFFD0BCFF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1C1E))
            )
        },
        containerColor = Color(0xFF1A1C1E)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.diary_label_date), color = Color(0xFFD0BCFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            TextField(
                value = date,
                onValueChange = { date = it },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.diary_label_title), color = Color(0xFFD0BCFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(stringResource(R.string.diary_placeholder_title), color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.diary_label_content), color = Color(0xFFD0BCFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            TextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text(stringResource(R.string.diary_placeholder_content), color = Color.Gray) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White
                )
            )
            
            if (entry?.aiInterpretation?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.diary_label_ai), color = Color(0xFFD0BCFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2F31)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = entry.aiInterpretation,
                        modifier = Modifier.padding(16.dp),
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
