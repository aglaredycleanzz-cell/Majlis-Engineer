package com.majlisengineer

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MajlisSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createProjectAndSaveSiteMeasurement() {
        composeRule.onNodeWithText("لوحة متابعة الأعمال").assertExists()
        composeRule.onNodeWithText("المشاريع").performClick()
        composeRule.onNodeWithText("＋ مشروع جديد").performClick()
        composeRule.onNodeWithText("اسم المشروع *").performTextInput("اختبار رحلة المشروع")
        composeRule.onNodeWithText("حفظ المشروع").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("اختبار رحلة المشروع").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("اختبار رحلة المشروع").performClick()
        composeRule.onNodeWithText("المعاينة").performClick()
        composeRule.onNodeWithText("＋ إضافة قياس").performClick()
        composeRule.onNodeWithText("اسم الفراغ *").performTextInput("الصالة")
        composeRule.onNodeWithText("الطول بالمتر *").performTextInput("6")
        composeRule.onNodeWithText("العرض بالمتر *").performTextInput("4")
        composeRule.onNodeWithText("حفظ القياس").performClick()
        composeRule.onNodeWithText("الصالة").assertExists()
        composeRule.onNodeWithText("24.00 م²").assertExists()
    }
}
