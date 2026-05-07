package com.loopers.interfaces.api.member;

import com.loopers.domain.member.Member;
import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.fixture.MemberFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberMeV1ApiE2ETest {
    private final TestRestTemplate testRestTemplate;
    private final MemberJpaRepository memberJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberMeV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            MemberJpaRepository memberJpaRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.memberJpaRepository = memberJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class Retrieve {
        private static final String ENDPOINT = "/api/v1/members/me";

        @DisplayName("내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다.")
        @Test
        void returnUserInfo_whenRetrieveMyInfo() {
            // arrange
            Member member = memberJpaRepository.save(MemberFixture.aMember());

            ParameterizedTypeReference<ApiResponse<MemberV1Dto.GetMemberProfileResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-MEMBER-ID", member.getId().toString());
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.GetMemberProfileResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data()).isNotNull(),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(member.getId())
            );
        }

        @Test
        @DisplayName("존재하지 않는 ID 번호로 조회할 경우, 404 Not Found 응답을 반환한다.")
        void returnNotFound_whenUserIdDoseNotExist() {
            // arrange
            Long nonExistUserId = 999L;

            ParameterizedTypeReference<ApiResponse<MemberV1Dto.GetMemberProfileResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-MEMBER-ID", nonExistUserId.toString());
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.GetMemberProfileResponse>> response = testRestTemplate.exchange(
                    ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }
}
