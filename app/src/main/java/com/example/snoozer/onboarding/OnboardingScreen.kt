package com.example.snoozer.onboarding

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * "How Snoozer works": the first-run onboarding, and the same pages opened again from "?".
 * [onExit] is told where to go once they are done (Home, or back to the opener).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onExit: (OnboardingExit) -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val spec = viewModel.spec
    val pager = rememberPagerState(initialPage = spec.startPage, pageCount = { OnboardingPageCount })
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) { viewModel.exitFlow.collect(onExit) }

    // System back steps through the pages first; at the first page it leaves as usual.
    BackHandler(enabled = spec.canGoBack(pager.currentPage)) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    OnboardingContent(
        spec = spec,
        pager = pager,
        onBack = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
        onNext = {
            if (pager.currentPage < OnboardingPageCount - 1) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            else viewModel.finish()
        },
        onSkipOrClose = viewModel::finish,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingContent(
    spec: OnboardingSpec,
    pager: PagerState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkipOrClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // On the unfolded Fold (or a tablet) the pages stay phone-width, centered.
    Box(modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            TopBar(spec, page = pager.currentPage, onBack = onBack, onSkipOrClose = onSkipOrClose)
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                OnboardingPage(page, spec.mode)
            }
            Column(
                Modifier.fillMaxWidth().padding(Spacing.M),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                PageDots(current = pager.currentPage, first = spec.startPage)
                DoodleButton(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                    hatchColor = MaterialTheme.colorScheme.primary,
                    containerColor = if (pager.currentPage == OnboardingPageCount - 1) SnoozerTheme.colors.bell
                        else MaterialTheme.colorScheme.secondaryContainer,
                    // Dark ink on the yellow finish button in both themes; the theme's text color would be pale in dark mode.
                    contentColor = if (pager.currentPage == OnboardingPageCount - 1) Color(0xFF2E2B26)
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                    seed = 400,
                ) {
                    Text(spec.buttonLabel(pager.currentPage))
                }
            }
        }
    }
}

@Composable
private fun TopBar(spec: OnboardingSpec, page: Int, onBack: () -> Unit, onSkipOrClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.S, vertical = Spacing.XS).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spec.canGoBack(page)) {
            DoodleButton(onClick = onBack, seed = 401, wobble = DoodleDefaults.WobbleLight,
                containerColor = Color.Transparent, borderColor = Color.Transparent) { Text("‹ Back") }
        }
        Spacer(Modifier.weight(1f))
        DoodleButton(onClick = onSkipOrClose, seed = 402, wobble = DoodleDefaults.WobbleLight,
            containerColor = Color.Transparent, borderColor = Color.Transparent) {
            Text(if (spec.showSkip) "Skip" else "Close")
        }
    }
}

/** One dot per page this mode shows; the current one filled. */
@Composable
private fun PageDots(current: Int, first: Int) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        modifier = Modifier.semantics { contentDescription = "Page ${current - first + 1} of ${OnboardingPageCount - first}" },
    ) {
        (first until OnboardingPageCount).forEach { i ->
            Box(
                Modifier.size(10.dp)
                    .doodleFill(if (i == current) ink else Color.Transparent, cornerRadius = DoodleDefaults.Round, wobble = 0.dp)
                    .sketchyBorder(ink, width = 1.5.dp, cornerRadius = DoodleDefaults.Round, wobble = 0.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagePreview(page: Int, mode: OnboardingMode = OnboardingMode.FirstRun) {
    SnoozerTheme {
        OnboardingContent(
            spec = OnboardingSpec.of(mode),
            pager = rememberPagerState(initialPage = page, pageCount = { OnboardingPageCount }),
            onBack = {}, onNext = {}, onSkipOrClose = {},
        )
    }
}

private const val W = 360
private const val H = 760

@Preview(name = "Onboarding 1 - welcome", widthDp = W, heightDp = H, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Composable private fun Page1Preview() = PagePreview(0)

@Preview(name = "Onboarding 2 - two kinds", widthDp = W, heightDp = H, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Onboarding 2 - two kinds, dark", widthDp = W, heightDp = H, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun Page2Preview() = PagePreview(1)

@Preview(name = "Onboarding 3 - overlaps", widthDp = W, heightDp = H, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Composable private fun Page3Preview() = PagePreview(2)

@Preview(name = "Onboarding 4 - heads-up", widthDp = W, heightDp = H, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Composable private fun Page4Preview() = PagePreview(3)

@Preview(name = "Onboarding 5 - plan, dark", widthDp = W, heightDp = H, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun Page5Preview() = PagePreview(4)

@Preview(name = "How it works (help) - unfolded", widthDp = 700, heightDp = 760, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Composable private fun HelpPreview() = PagePreview(1, OnboardingMode.Help)
