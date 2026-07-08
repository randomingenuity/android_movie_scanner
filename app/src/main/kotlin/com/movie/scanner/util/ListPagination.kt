package com.movie.scanner.util

/**
 * Slices a filtered movie list into fixed-size pages for the list screen.
 */
object ListPagination {
    const val DEFAULT_PAGE_SIZE = 25

    /**
     * One page of list rows plus paging metadata for the list UI.
     */
    data class Page<T>(
        val items: List<T>,
        val currentPageIndex: Int,
        val totalPages: Int,
        val totalItemCount: Int,
        val pageSize: Int = DEFAULT_PAGE_SIZE,
    ) {
        val hasPreviousPage: Boolean
            get() = currentPageIndex > 0

        val hasNextPage: Boolean
            get() = currentPageIndex < totalPages - 1
    }

    /**
     * Returns the requested [pageIndex] slice of [items], clamping the index when it is out of range.
     */
    fun <T> paginate(
        items: List<T>,
        pageIndex: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Page<T> {
        if (items.isEmpty()) {
            return Page(
                items = emptyList(),
                currentPageIndex = 0,
                totalPages = 0,
                totalItemCount = 0,
                pageSize = pageSize,
            )
        }

        val totalPages = (items.size + pageSize - 1) / pageSize
        val safePageIndex = pageIndex.coerceIn(0, totalPages - 1)
        val startIndex = safePageIndex * pageSize
        val endIndex = minOf(startIndex + pageSize, items.size)

        return Page(
            items = items.subList(startIndex, endIndex),
            currentPageIndex = safePageIndex,
            totalPages = totalPages,
            totalItemCount = items.size,
            pageSize = pageSize,
        )
    }

    /**
     * Builds a human-readable range label such as "26–50 of 120".
     */
    fun buildPageRangeLabel(page: Page<*>): String {
        if (page.totalItemCount == 0) {
            return ""
        }

        val rangeStart = page.currentPageIndex * page.pageSize + 1
        val rangeEnd = rangeStart + page.items.size - 1

        return "$rangeStart–$rangeEnd of ${page.totalItemCount}"
    }
}
