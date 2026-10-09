package com.hcdc.legalease.ui.screens.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hcdc.legalease.R
import com.hcdc.legalease.ui.components.cards.HistoryCard.HistoryCard
import com.hcdc.legalease.ui.components.searchBar.ScanSearchBar
import com.hcdc.legalease.ui.components.spacers.VerticalSpacer

@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = viewModel()
) {

    var searchQuery by remember { mutableStateOf("") }

    //DUMMY
    val itemsList = List(15) { index -> "Lorem Ipsum $index" }
    val filteredItems = itemsList.filter {
        it.contains(searchQuery, ignoreCase = true)
    }
    //DUMMY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
        modifier = Modifier.fillMaxWidth().padding(0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
            Column() {
                Text(
                    text = "My Scans",
                    style = MaterialTheme.typography.displayLarge.copy(
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                VerticalSpacer(5.dp)
                Text(
                    text = "Your contract history in one place.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
            Image(
                painter = painterResource(R.drawable.together),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(100.dp)
            )
        }

        HorizontalDivider(
            color = Color.Gray.copy(alpha = .3f),
            thickness = 1.dp,
        )
        VerticalSpacer(height = 15.dp)
        ScanSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it }
        )
        VerticalSpacer(height = 15.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            items(filteredItems) { item ->
                HistoryCard(
                    title = item,
                    // Placeholder rows have no analysis ID to open yet.
                    onClick = {}
                )
            }
        }
    }
}
