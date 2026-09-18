package com.loopers.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PageQueryTest {

    private static final int DEFAULT_SIZE = 20;

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("page가 음수면 0으로 보정한다")
        void correctsNegativePageToZero() {
            // given
            int negativePage = -1;
            int expectedPage = 0;

            // when
            PageQuery pageQuery = new PageQuery(negativePage, DEFAULT_SIZE);

            // then
            assertThat(pageQuery.page()).isEqualTo(expectedPage);
        }

        @Test
        @DisplayName("page는 0부터 시작하므로 0은 보정하지 않는다")
        void keepsFirstPage() {
            // given
            int firstPage = 0;

            // when
            PageQuery pageQuery = new PageQuery(firstPage, DEFAULT_SIZE);

            // then
            assertThat(pageQuery.page()).isEqualTo(firstPage);
        }

        @Test
        @DisplayName("size가 1보다 작으면 1로 보정한다")
        void correctsSizeBelowOneToOne() {
            // given
            int firstPage = 0;
            int zeroSize = 0;
            int expectedSize = 1;

            // when
            PageQuery pageQuery = new PageQuery(firstPage, zeroSize);

            // then
            assertThat(pageQuery.size()).isEqualTo(expectedSize);
        }

        @Test
        @DisplayName("size가 1과 100 사이면 그대로 둔다")
        void keepsSizeWithinRange() {
            // given
            int firstPage = 0;
            int minSize = 1;
            int maxSize = 100;

            // when
            PageQuery minSizeQuery = new PageQuery(firstPage, minSize);
            PageQuery maxSizeQuery = new PageQuery(firstPage, maxSize);

            // then
            assertAll(
                    () -> assertThat(minSizeQuery.size()).isEqualTo(minSize),
                    () -> assertThat(maxSizeQuery.size()).isEqualTo(maxSize)
            );
        }

        @Test
        @DisplayName("size가 100보다 크면 100으로 보정한다")
        void correctsSizeAboveMaxToMax() {
            // given
            int firstPage = 0;
            int tooLargeSize = 101;
            int expectedSize = 100;

            // when
            PageQuery pageQuery = new PageQuery(firstPage, tooLargeSize);

            // then
            assertThat(pageQuery.size()).isEqualTo(expectedSize);
        }
    }

    @Nested
    @DisplayName("offset")
    class Offset {

        @Test
        @DisplayName("첫 페이지(page 0)는 앞의 어떤 것도 건너뛰지 않는다")
        void skipsNothing_whenPageIsFirst() {
            // given
            int firstPage = 0;
            long expectedOffset = 0L;
            PageQuery pageQuery = new PageQuery(firstPage, DEFAULT_SIZE);

            // when
            long offset = pageQuery.offset();

            // then
            assertThat(offset).isEqualTo(expectedOffset);
        }

        @Test
        @DisplayName("page가 2이고 size가 20이면 앞의 40개를 건너뛴다")
        void skipsPrecedingPages_whenPageIsNotFirst() {
            // given
            int thirdPage = 2;
            int size = 20;
            long expectedOffset = 40L;
            PageQuery pageQuery = new PageQuery(thirdPage, size);

            // when
            long offset = pageQuery.offset();

            // then
            assertThat(offset).isEqualTo(expectedOffset);
        }

        @Test
        @DisplayName("건너뛸 개수가 int 범위를 넘어도 오버플로 없이 계산한다")
        void returnsOffsetBeyondIntRange() {
            // given
            int hugePage = 100_000_000;
            int maxSize = 100;
            long expectedOffset = 10_000_000_000L;
            PageQuery pageQuery = new PageQuery(hugePage, maxSize);

            // when
            long offset = pageQuery.offset();

            // then
            assertThat(offset).isEqualTo(expectedOffset);
        }
    }
}
