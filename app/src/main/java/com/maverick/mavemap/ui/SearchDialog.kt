package com.maverick.mavemap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maverick.mavemap.navigation.DestinationSearch
import kotlinx.coroutines.delay

@Composable
fun SearchDialog(
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit,
    results: List<DestinationSearch.Result>,
    onResultSelected: (DestinationSearch.Result) -> Unit,
    message: String?,
    isSearching: Boolean,
    width: Dp = 300.dp
) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            onSearch("")
        } else {
            delay(400)
            onSearch(query)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth(0.68f)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Enter destination") },
            placeholder = { Text("Search places") }
        )

        if (isSearching) Text("Searching...")
        if (message != null && !isSearching) Text(message)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            results.forEach { result ->
                Card(
                    onClick = { onResultSelected(result) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        result.name,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
