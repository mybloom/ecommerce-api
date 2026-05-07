package com.loopers.interfaces.api.point;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.point.PointFixture;
import com.loopers.domain.point.PointRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.fixture.MemberFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PointV1ApiE2ETest {
    private static final String HEADER_OF_MEMBER_ID = "X-MEMBER-ID";
    private static final long INITIAL_POINT_BALANCE = 500L;

    private Member member;

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final MemberRepository memberRepository;
    private final PointRepository pointRepository;

    @Autowired
    public PointV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            MemberRepository memberRepository,
            PointRepository pointRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.memberRepository = memberRepository;
        this.pointRepository = pointRepository;
    }

    @BeforeEach
    void setUp() {
        this.member = memberRepository.save(MemberFixture.aMember());
        pointRepository.save(PointFixture.aPointWithBalance(member.getId(), INITIAL_POINT_BALANCE));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/points/charge")
    class Charge {
        private static final String ENDPOINT = "/api/v1/points/charge";

        @Test
        @DisplayName("포인트 충전이 성공할 경우, 충전된 포인트 정보를 응답으로 반환한다.")
        void returnPointInfo_whenChargeIsSuccessful() {
            Long chargeAmount = 1000L;
            PointV1Dto.ChargeRequest request = new PointV1Dto.ChargeRequest(chargeAmount);

            ParameterizedTypeReference<ApiResponse<PointV1Dto.ChargeResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HEADER_OF_MEMBER_ID, member.getId().toString());
            HttpEntity<PointV1Dto.ChargeRequest> httpEntity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse<PointV1Dto.ChargeResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, httpEntity, responseType);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().memberId()).isEqualTo(member.getId()),
                    () -> assertThat(response.getBody().data().balance()).isEqualTo(INITIAL_POINT_BALANCE + chargeAmount)
            );
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 404를 응답한다")
        void returns404_whenMemberDoesNotExist() {
            Long nonExistentMemberId = Long.MAX_VALUE;
            Long chargeAmount = 1000L;
            PointV1Dto.ChargeRequest request = new PointV1Dto.ChargeRequest(chargeAmount);

            ParameterizedTypeReference<ApiResponse<PointV1Dto.ChargeResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HEADER_OF_MEMBER_ID, nonExistentMemberId.toString());
            HttpEntity<PointV1Dto.ChargeRequest> httpEntity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse<PointV1Dto.ChargeResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, httpEntity, responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/points")
    class Retrieve {
        private static final String ENDPOINT = "/api/v1/points";

        @Test
        @DisplayName("회원의 포인트 조회 시, 자신의 포인트 정보를 반환한다.")
        void returnPointInfo_whenRetrieveIsSuccessful() {
            ParameterizedTypeReference<ApiResponse<PointV1Dto.RetrieveResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_OF_MEMBER_ID, member.getId().toString());
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            ResponseEntity<ApiResponse<PointV1Dto.RetrieveResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, httpEntity, responseType);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().memberId()).isEqualTo(member.getId()),
                    () -> assertThat(response.getBody().data().balance()).isEqualTo(INITIAL_POINT_BALANCE)
            );
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 404를 응답한다")
        void returns404_whenMemberDoesNotExist() {
            Long nonExistentMemberId = Long.MAX_VALUE;

            ParameterizedTypeReference<ApiResponse<PointV1Dto.RetrieveResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_OF_MEMBER_ID, nonExistentMemberId.toString());
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            ResponseEntity<ApiResponse<PointV1Dto.RetrieveResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, httpEntity, responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
