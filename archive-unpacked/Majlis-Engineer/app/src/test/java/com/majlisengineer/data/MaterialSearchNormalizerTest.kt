package com.majlisengineer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialSearchNormalizerTest {
    @Test
    fun normalizesArabicAlefAndTaaMarbuta() {
        assertEquals("اناره", MaterialSearchNormalizer.normalize("إِنَارَة"))
    }

    @Test
    fun stripsDiacriticsAndPunctuation() {
        assertEquals("كهرباء واناره", MaterialSearchNormalizer.normalize("كَهْرَبَاء، وإنارة!"))
    }

    @Test
    fun normalizesWhitespaceAndLatinCase() {
        assertEquals("led panel", MaterialSearchNormalizer.normalize("  LED   Panel  "))
    }
}
