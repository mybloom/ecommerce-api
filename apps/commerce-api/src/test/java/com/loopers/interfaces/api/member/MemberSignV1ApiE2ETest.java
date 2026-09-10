package com.loopers.interfaces.api.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
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
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberSignV1ApiE2ETest {
    private static final String ENDPOINT = "/api/v1/members";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final MemberRepository memberRepository;

    @Autowired
    public MemberSignV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            MemberRepository memberRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.memberRepository = memberRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.")
    @Test
    void returnUserInfo_whenJoinIsSuccessful() {
        MemberV1Dto.RegisterRequest request = MemberFixture.aRegisterRequest();

        ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<MemberV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

        //act
        ResponseEntity<ApiResponse<MemberV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, httpEntity, responseType);

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(MemberFixture.DEFAULT_LOGIN_ID)
        );
    }

    @DisplayName("이미 가입된 이메일로 회원 가입 시, 409 Conflict 응답을 반환하고 회원은 늘어나지 않는다.")
    @Test
    void returnConflict_whenEmailIsAlreadyRegistered() {
        // given
        memberRepository.save(MemberFixture.aMemberWithEmail(MemberFixture.DEFAULT_EMAIL));

        String otherLoginId = "otherUser";
        MemberV1Dto.RegisterRequest request = MemberFixture.aRegisterRequestWithLoginId(otherLoginId);
        ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<MemberV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

        // when
        ResponseEntity<ApiResponse<MemberV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, httpEntity, responseType);

        // then
        boolean otherLoginIdRegistered = memberRepository.existsByLoginId(otherLoginId);

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().data()).isNull(),
                () -> assertThat(otherLoginIdRegistered).isFalse()
        );
    }

    @DisplayName("회원 가입 시에 유효하지 않는 loginId일 경우, 400 Bad Request 응답을 반환한다.")
    @Test
    void returnBadRequest_whenGenderIsMissing() {
        //arrange
        MemberV1Dto.RegisterRequest registerRequest = MemberFixture.aRegisterRequestWithLoginId("");

        ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<MemberV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(registerRequest, headers);

        //act
        ResponseEntity<ApiResponse<MemberV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, httpEntity, responseType);

        //assert
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().data()).isNull()
        );
    }

}
