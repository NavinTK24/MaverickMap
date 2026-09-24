package com.maverick.mavemap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maverick.mavemap.navigation.DestinationSearch

@Composable
fun SearchDialog(
    onSearch: (String) -> Unit,
    results: List<DestinationSearch.Result>,
    onResultSelected: (DestinationSearch.Result) -> Unit
) {
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Search destination") }
            )

            Button(onClick = { onSearch(query) }) {
                Text("SEARCH")
            }
        }

        results.forEach { result ->
            Button(
                onClick = { onResultSelected(result) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(result.name)
            }
        }
    }
}
