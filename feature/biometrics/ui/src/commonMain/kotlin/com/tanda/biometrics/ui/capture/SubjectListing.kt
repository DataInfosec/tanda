package com.tanda.biometrics.ui.capture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.tanda.core.ui.design.DesignTextField
import com.tanda.core.ui.extension.designScheme
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.biometrics.ui.generated.resources.Res
import tanda.feature.biometrics.ui.generated.resources.ic_close
import tanda.feature.biometrics.ui.generated.resources.ic_person
import tanda.feature.biometrics.ui.generated.resources.ic_right_arrow
import tanda.feature.biometrics.ui.generated.resources.ic_search
import tanda.feature.biometrics.ui.generated.resources.search
import tanda.feature.biometrics.ui.generated.resources.search_label

data class SubjectDetail(
    val name: String = "Atiku Abubakar",
    val date: String = "March 10, 2026",
    val image: String? = ""
)

val subjectList = List(17) { SubjectDetail() }

@Composable
fun SubjectListing(
    subjects: List<SubjectDetail> = emptyList(),
    searchQuery: TextFieldState = TextFieldState(),
    searchHint: String = "Subject name",
    onSubjectClick: (SubjectDetail) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SearchHeader(
            searchQuery = searchQuery,
            hint = searchHint
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(subjects) { subject ->
                SubjectBiometricItem(
                    subject = subject,
                    onClick = { onSubjectClick(subject) }
                )

            }
        }

    }
}

@Composable
fun SubjectBiometricItem(
    subject: SubjectDetail,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = subject.image,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape).border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            loading = { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) },
            error = {
                Image(
                    painter = painterResource(Res.drawable.ic_person),
                    contentDescription = null
                )
            }
        )

        Spacer(modifier = Modifier.width(24.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = subject.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subject.date,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.designScheme.text
                ),
            )
        }

        Icon(
            painter = painterResource(Res.drawable.ic_right_arrow),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
fun SearchHeader(
    searchQuery: TextFieldState = TextFieldState(),
    hint: String
) {
    var toggleSearch by remember { mutableStateOf(false) }
    val icon = if (toggleSearch) Res.drawable.ic_close  else Res.drawable.ic_search
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AnimatedContent(
            targetState = toggleSearch,
            transitionSpec = { fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally() },
            label = stringResource(Res.string.search_label),
            modifier = Modifier.weight(1f)
        ) { showSearchBar ->
            if (showSearchBar) {
                DesignTextField(
                    hint = hint,
                    state = searchQuery,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = stringResource(Res.string.search),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.designScheme.text
                    )
                )
            }
        }

        IconButton(
            onClick = {
                toggleSearch = !toggleSearch
            }
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.designScheme.text
            )
        }


    }
}

@Preview
@Composable
fun PreviewSearchHeader() {
    DesignTheme(darkTheme = false) {
        Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
            SearchHeader(hint = "Subject name")

            SubjectBiometricItem(
                subject = SubjectDetail()
            ) {}
        }
    }
}

@Preview
@Composable
fun PreviewSubjectListing() {
    DesignTheme(darkTheme = false) {
        SubjectListing(
            subjects = subjectList
        )
    }
}
