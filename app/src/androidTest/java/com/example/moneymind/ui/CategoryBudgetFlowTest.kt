package com.example.moneymind.ui

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.moneymind.MainActivity
import com.example.moneymind.core.ServiceLocator
import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.EntryType
import com.example.moneymind.domain.LedgerEntry
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import androidx.lifecycle.Lifecycle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryBudgetFlowTest {
    private companion object {
        const val CATEGORY_NAME = "TestBudget"
        const val CATEGORY_BUDGET = "50000"
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            val repository = ServiceLocator.repository(context)
            repository.clearFactoryData()
            repository.addManualEntry(
                LedgerEntry(
                    occurredAt = LocalDateTime.now(),
                    amount = 1_000L,
                    type = EntryType.INCOME,
                    category = CATEGORY_NAME,
                    description = "seed category",
                    merchant = null,
                    source = EntrySource.MANUAL
                )
            )
        }
        composeRule.activityRule.scenario.onActivity { activity ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
    }

    @Test
    fun canCreateCategoryBudgetFromOptions() {
        composeRule.onNodeWithTag("top_nav_options", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("options_section_control", useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag(
            testTag = "options_budget_category_picker_trigger",
            useUnmergedTree = true
        ).performClick()
        composeRule.onNodeWithText(CATEGORY_NAME, useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text = "선택된 카테고리: $CATEGORY_NAME",
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(
            testTag = "options_budget_category_amount",
            useUnmergedTree = true
        ).performTextClearance()
        composeRule.onNodeWithTag(
            testTag = "options_budget_category_amount",
            useUnmergedTree = true
        ).performTextInput(CATEGORY_BUDGET)

        composeRule.onNodeWithTag(
            testTag = "options_budget_category_save",
            useUnmergedTree = true
        ).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text = "0 / ${CATEGORY_BUDGET}원",
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text = "남은 예산: ${CATEGORY_BUDGET}원",
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
