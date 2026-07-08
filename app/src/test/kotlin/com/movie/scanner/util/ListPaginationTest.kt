package com.movie.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListPaginationTest {
    @Test
    fun paginate_emptyList_returnsEmptyPage() {
        val page = ListPagination.paginate(emptyList<String>(), pageIndex = 0)

        assertTrue(page.items.isEmpty())
        assertEquals(0, page.currentPageIndex)
        assertEquals(0, page.totalPages)
        assertEquals(0, page.totalItemCount)
        assertFalse(page.hasPreviousPage)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun paginate_firstPage_returnsFirstSlice() {
        val items = (1..30).map { index -> "item-$index" }

        val page = ListPagination.paginate(items, pageIndex = 0, pageSize = 25)

        assertEquals(items.take(25), page.items)
        assertEquals(0, page.currentPageIndex)
        assertEquals(2, page.totalPages)
        assertEquals(30, page.totalItemCount)
        assertFalse(page.hasPreviousPage)
        assertTrue(page.hasNextPage)
    }

    @Test
    fun paginate_lastPage_returnsRemainingItems() {
        val items = (1..30).map { index -> "item-$index" }

        val page = ListPagination.paginate(items, pageIndex = 1, pageSize = 25)

        assertEquals(items.drop(25), page.items)
        assertEquals(1, page.currentPageIndex)
        assertTrue(page.hasPreviousPage)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun paginate_outOfRangePageIndex_clampsToLastPage() {
        val items = (1..30).map { index -> "item-$index" }

        val page = ListPagination.paginate(items, pageIndex = 99, pageSize = 25)

        assertEquals(items.drop(25), page.items)
        assertEquals(1, page.currentPageIndex)
    }

    @Test
    fun buildPageRangeLabel_formatsVisibleRange() {
        val page = ListPagination.paginate(
            items = (1..30).map { index -> "item-$index" },
            pageIndex = 1,
            pageSize = 25,
        )

        assertEquals("26–30 of 30", ListPagination.buildPageRangeLabel(page))
    }
}
