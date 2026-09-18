package com.loopers.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    private static final int FIRST_PAGE = 0;

    @Nested
    @DisplayName("totalPages")
    class TotalPages {

        @Test
        @DisplayName("전체 개수가 size로 나누어떨어지면 그 몫이 전체 페이지 수다")
        void returnsQuotient_whenTotalCountIsDivisible() {
            // given
            int size = 2;
            long totalCount = 4L;
            int expectedTotalPages = 2;
            PageResult<String> pageResult = aPageResult(FIRST_PAGE, size, totalCount);

            // when
            int totalPages = pageResult.totalPages();

            // then
            assertThat(totalPages).isEqualTo(expectedTotalPages);
        }

        @Test
        @DisplayName("전체 개수가 size로 나누어떨어지지 않으면 남은 것을 담을 페이지를 더한다")
        void addsPageForRemainder_whenTotalCountIsNotDivisible() {
            // given
            int size = 2;
            long totalCount = 5L;
            int expectedTotalPages = 3;
            PageResult<String> pageResult = aPageResult(FIRST_PAGE, size, totalCount);

            // when
            int totalPages = pageResult.totalPages();

            // then
            assertThat(totalPages).isEqualTo(expectedTotalPages);
        }

        @Test
        @DisplayName("전체 개수가 0이면 전체 페이지 수는 0이다")
        void returnsZero_whenTotalCountIsZero() {
            // given
            int size = 20;
            long emptyTotalCount = 0L;
            int expectedTotalPages = 0;
            PageResult<String> pageResult = aPageResult(FIRST_PAGE, size, emptyTotalCount);

            // when
            int totalPages = pageResult.totalPages();

            // then
            assertThat(totalPages).isEqualTo(expectedTotalPages);
        }
    }

    @Nested
    @DisplayName("hasNext")
    class HasNext {

        @Test
        @DisplayName("현재 페이지 뒤에 페이지가 남아 있으면 참이다")
        void returnsTrue_whenPagesRemain() {
            // given
            int size = 2;
            long totalCountOfTwoPages = 4L;
            PageResult<String> firstOfTwoPages = aPageResult(FIRST_PAGE, size, totalCountOfTwoPages);

            // when
            boolean hasNext = firstOfTwoPages.hasNext();

            // then
            assertThat(hasNext).isTrue();
        }

        @Test
        @DisplayName("마지막 페이지면 거짓이다")
        void returnsFalse_whenPageIsLast() {
            // given
            int secondPage = 1;
            int size = 2;
            long totalCountOfTwoPages = 4L;
            PageResult<String> lastOfTwoPages = aPageResult(secondPage, size, totalCountOfTwoPages);

            // when
            boolean hasNext = lastOfTwoPages.hasNext();

            // then
            assertThat(hasNext).isFalse();
        }

        @Test
        @DisplayName("전체 개수가 0이면 거짓이다")
        void returnsFalse_whenTotalCountIsZero() {
            // given
            int size = 20;
            long emptyTotalCount = 0L;
            PageResult<String> emptyResult = aPageResult(FIRST_PAGE, size, emptyTotalCount);

            // when
            boolean hasNext = emptyResult.hasNext();

            // then
            assertThat(hasNext).isFalse();
        }
    }

    private static PageResult<String> aPageResult(int page, int size, long totalCount) {
        return new PageResult<>(List.of(), totalCount, page, size);
    }
}
