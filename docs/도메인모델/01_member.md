# Member 애그리거트

> **범위**: 큐레이션 오픈마켓 MVP — 사용자 관점

---

## A. 행위

> **책임**: 시스템을 사용하는 회원의 정보를 표현하고, 회원가입과 인증의 핵심 책임을 진다. 회원 정보(이메일, 생년월일, 성별 등)를 보관하며, 비밀번호는 해시 형태로만 보관하고 검증 행위로만 사용된다.

### UC-1. 회원가입

**목적**  
새로운 사용자가 회원 정보를 등록하여 시스템 사용 권한을 얻는다.

**입력**

- `loginId` : String — 로그인 식별자
- `email` : Email — 이메일 주소
- `birthDate` : LocalDate — 생년월일
- `gender` : Gender — 성별
- `password` : String — 평문 비밀번호 (저장 직전 해시 처리)

**출력**
 
가입 결과 
- ID(생성된 회원의 식별자), loginId

**전제 조건**
- 동일한 `loginId`로 가입한 회원이 존재하지 않아야 함
- 동일한 `email`로 가입한 회원이 존재하지 않아야 함

**처리 정책**
- 비밀번호는 평문으로 저장하지 않고 해시 처리하여 `passwordHash`로 보관
- 회원 등록 시점이 `createdAt`으로 기록됨
- 회원 생성과 동시에 잔액 0의 Point를 함께 생성 (참고: Point-001)

**도메인 협력**
- `Member.register(loginId, email, birthDate, gender, password)` 정적 팩토리로 새 인스턴스 생성
- `Point.createInitial(memberId)`로 초기 Point 생성
- 두 작업은 동일 트랜잭션에서 처리

---

### UC-2. 내 정보 조회

**목적**  
로그인한 회원이 자신의 회원 정보를 확인한다.

**입력**

- `memberId` : MemberId — 조회 대상 회원 (X-MEMBER-ID 헤더에서 추출)

**출력**

`MemberProfile` — 외부 노출 가능한 회원 정보:
- 회원 ID, 로그인 ID, 이메일, 생년월일, 성별, 가입 일시

**전제 조건**
- 사용자는 로그인 상태여야 함

**처리 정책**
- `passwordHash`는 절대 외부에 노출되지 않음
- Member 객체로부터 `MemberProfile`을 추출하여 반환

**도메인 협력**
- `member.toProfile()` 호출하여 `MemberProfile` 생성


---

### 도메인 이벤트

> 현재 단계에서는 이벤트 도입 보류.

향후 후보:
- `MemberRegistered` — 회원이 가입함

---

## B. 구조

> 위 행위를 가능하게 하는 내부 구성. 애그리거트 루트와 값 객체, 외부 관계로 이루어진다.

### B.1. Member : Entity, Aggregate Root

#### 속성

- `id` : Long — 식별자
- `loginId` : String — 로그인 식별자
- `email` : Email — 이메일
- `birthDate` : LocalDate — 생년월일
- `gender` : Gender — 성별
- `passwordHash` : String — 비밀번호 해시 (외부 노출 금지)
- `createdAt` : ZonedDateTime — 회원 등록 시각

**불변식**
- `loginId`는 시스템 내에서 유일
- `email`은 시스템 내에서 유일
- `passwordHash`는 외부에서 직접 접근할 수 없음 (게터 노출 안 함)

#### 행위

- `register(loginId, email, birthDate, gender, password)` : Member — 새 회원을 등록한다 (정적 팩토리). 평문 비밀번호를 받아 내부적으로 해시 처리하고, 
  createdAt을 등록 시점으로 설정
- `verifyPassword(rawPassword)` : Boolean — 평문 비밀번호가 저장된 해시와 일치하는지 검증한다
- `toProfile()` : MemberProfile — 외부 노출용 회원 정보를 반환한다. `passwordHash`는 포함하지 않음

---

### B.2. Email : Value Object

이메일을 표현하는 값 객체.

#### 속성

- `value` : String — 이메일 문자열

**불변식**
- 유효한 이메일 형식이어야 함 (RFC 5322 또는 단순 정규식 기준)

#### 행위

- `of(value)` : Email — 새 인스턴스를 생성한다 (정적 팩토리). 유효성 검사를 포함하여 유효하지 않은 이메일은 예외를 던진다.

---

### B.3. Gender : Value Object

성별을 나타내는 열거형.

#### 속성

- `MALE` — 남성
- `FEMALE` — 여성


#### 행위

> 현재 단계에서는 정의된 행위가 없다.

---

### B.4. MemberProfile : Value Object

회원의 외부 노출용 정보를 담는 값 객체. `passwordHash` 같은 민감 정보를 제외한 안전한 표현.

#### 속성

- `id` : Long — 회원 식별자
- `loginId` : String — 로그인 식별자
- `email` : Email — 이메일
- `birthDate` : LocalDate — 생년월일
- `gender` : Gender — 성별
- `createdAt` : ZonedDateTime — 회원 등록 시각

#### 행위

- `of(id, loginId, email, birthDate, gender, registeredAt)` : MemberProfile — 새 인스턴스를 생성한다 (정적 팩토리)

> 회원의 노출용 스냅샷이며, 자체적으로 상태가 변하지 않는다.

---

### B.5. 다른 애그리거트와의 관계

- **ProductLike** ← `ProductLike`가 `memberId`로 이 애그리거트를 참조함
    - 좋아요 누르기/취소/위시리스트 조회의 행위 주체로 사용됨
- 추후 다른 애그리거트(주문, 리뷰 등)가 추가되면 함께 등장하게 된다

---

## C. 설계 결정

> 확정된 결정과 보류된 결정을 함께 모은다. 보류된 항목이 결정되면 C.2 → C.1로 이동한다.

### C.1. 확정된 결정

> **왜 이런 선택을 했는지** 보존한다. 결정은 누적될 뿐 수정되지 않는다.

#### Member-001. 비밀번호는 해시 형태로만 보관하고 외부 노출 금지
- **일시**: 2026-05-07
- **결정**: 비밀번호는 `passwordHash` 속성에 해시된 형태로만 보관한다. 게터 노출하지 않으며, 외부에서는 `verifyPassword(rawPassword)` 메서드로만 검증할 수 있다. `toProfile()`로 추출되는 `MemberProfile`에도 포함되지 않는다.
- **이유**:
    - 평문 저장은 보안상 절대 금지
    - 외부 노출 시 의도치 않은 유출 위험 (로그, 직렬화, DTO 등)
    - 비밀번호 검증은 항상 Member 내부에서 일어나야 캡슐화가 유지됨
- **영향**:
    - 회원 정보를 외부로 전달할 때는 항상 `MemberProfile`로 변환
    - 비밀번호 검증은 도메인 모델 내부 행위로만 수행
    - 영속화 계층에서 직렬화 시 `passwordHash`가 노출되지 않도록 별도 매핑 필요

#### Member-002. 회원 정보의 외부 노출은 MemberProfile 값 객체로 표현
- **일시**: 2026-05-07
- **결정**: Member 애그리거트의 정보를 외부(다른 컨텍스트, API 응답 등)로 전달할 때는 `MemberProfile` 값 객체로 변환하여 사용한다.
- **이유**:
    - `passwordHash` 등 민감 정보의 실수 노출 방지
    - 어떤 정보가 노출 가능한지 타입 자체로 표현됨 (컴파일 타임 안전성)
    - 향후 노출 정책 변경 시 `Member.toProfile()` 한 곳만 수정
- **영향**:
    - Member 외부에서는 게터로 직접 정보를 꺼내지 않고 `toProfile()`을 통해 접근
    - 노출 범위가 다양해지면 별도 Profile 타입 추가 가능 (예: `PublicProfile`, `AdminMemberView`)
- **재검토 시점**: 노출 범위가 다양해져 단일 `MemberProfile`로는 부족할 때.

### C.2. 보류된 결정

> 아직 결정되지 않았거나 **앞으로 다룰 항목**을 모은다. 결정되면 C.1로 이동한다.

- [ ] 회원 정보 변경 유스케이스 (이메일 변경, 비밀번호 변경 등)
- [ ] 로그인 유스케이스 정의 (`verifyPassword` 행위가 사용될 곳)
- [ ] 도메인 이벤트 도입 (`MemberRegistered` 등)