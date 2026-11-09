# CLAUDE.md — 사이(SAI) 백엔드

백엔드 팀 공통 지침입니다. Claude Code로 작업할 때와 AI PR 리뷰 모두 이 파일을 기준으로 합니다.
리뷰 지침은 `.github/claude-review.md`에 따로 있습니다.
규칙을 바꾸려면 이 파일을 수정하는 PR을 올려 팀 합의를 거칩니다.

## 프로젝트 개요
- 서비스: 공강 시간이 겹치는 친구를 찾고 활동을 추천하는 앱 '사이'
- 스택: Java 21 / Spring Boot 4.1.1 / Spring Data JPA / MySQL
- 인증: Spring Security + JWT(jjwt), 카카오 로그인
- API 문서: springdoc Swagger UI (`/swagger-ui.html`)
- 클라이언트: React Native(Expo)
- API 명세의 기준은 [노션 API 명세서](https://app.notion.com/p/API-40931c4f7fe780848a56c6dbf93a87c2)입니다.

## 명령어
```bash
./gradlew build                      # 빌드
./gradlew test                       # 전체 테스트
./gradlew test --tests "*UserUpdateTest"   # 특정 테스트
./gradlew bootRun --args='--spring.profiles.active=local'   # 로컬 실행
```
- 로컬 실행 시 환경 변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`을 주입합니다. `JWT_SECRET`은 Base64 인코딩된 값이며 디코딩 후 32바이트 이상이어야 합니다. 값은 `.env`에 두며 저장소에 올리지 않습니다.
- 테스트는 `@ActiveProfiles("test")` + H2 인메모리(MySQL 모드)를 사용하므로 DB가 필요 없습니다.
- `local` 프로필에서만 개발용 토큰 발급 API(`dev/`)가 활성화됩니다.

## 패키지 구조
```
com.aloc.sai
├── common/exception/   # ApiException, ErrorResponse, GlobalExceptionHandler
├── config/             # Security, Swagger, Scheduling 설정
├── security/           # JWT 필터·프로바이더, @LoginUserId
├── dev/                # local 프로필 전용 (테스트 토큰 발급)
├── auth/               # 로그인·토큰 (+ kakao/, dto/)
├── user/               # 회원
├── chat/               # 채팅 (+ dto/)
└── appointment/        # 약속
```
- 도메인 패키지 바로 아래에 Controller·Service·Repository·Entity를 두고, 하위 패키지는 `dto/`만 둡니다.
- 새 도메인도 같은 방식으로 `com.aloc.sai.{도메인}`에 만듭니다.

## 도메인과 담당자
| 도메인 | 패키지 | 담당 |
|---|---|---|
| 로그인·토큰 | `auth` | 정지윤 |
| 회원 | `user` | 정지윤 |
| 친구 | `friend` | 정지윤 |
| 시간표·공강 | `timetable`, `freetime` | 정지윤 |
| 알림 | `notification` | 정지윤 |
| 채팅 | `chat` | 배은주 |
| 약속 | `appointment` | 배은주 |
| 별점 | `review` | 배은주 |
| 활동 추천 | `recommend` | 배은주 |

## 도메인 규칙
각 담당자가 자기 도메인에서 **깨지면 안 되는 규칙**을 적습니다.

- **공통**: 다른 도메인의 Repository를 직접 호출하지 않고 해당 Service를 거칩니다. 단, 기존 `auth`↔`user` 간 Repository 참조는 예외로 두고, 새로 만드는 도메인부터 적용합니다.
- **알림**
  - 푸시(Expo Push)는 각 도메인이 직접 보내지 않고 `notification`의 공통 발송 인터페이스를 호출합니다. 각 도메인은 "언제·누구에게·무슨 알림"만 정합니다.
  - 발송은 트랜잭션 커밋 이후에 일어나야 하며, 발송 실패가 원래 비즈니스 로직을 실패시키지 않습니다.
  - 예약 발송을 건 도메인은 원본이 바뀌거나 취소되면(예: 약속 시간 변경·취소) 예약 알림도 함께 수정·취소해야 합니다.
- **로그인·토큰**
  - 리프레시 토큰은 SHA-256 해시로만 저장합니다. 토큰 원문을 DB에 저장하거나 로그에 남기지 않습니다.
  - 재발급 시 기존 리프레시 토큰을 삭제하고 새 토큰을 발급합니다(회전). 이미 사용·폐기된 토큰은 401입니다.
  - 만료된 리프레시 토큰은 매일 04시(KST) 스케줄러가 정리합니다.
- **회원**
  - 닉네임은 앞뒤 공백 제거 후 1~20자(코드 포인트 기준)이며 전체에서 유일합니다. 중복 확인과 수정은 같은 정규화 규칙을 씁니다.
  - 친구 코드는 가입 시 8자로 생성되며 유일하고 변경할 수 없습니다.
  - 초기 설정 완료는 학번과 학과가 모두 있는 상태입니다. 초기 설정 전에는 둘을 함께 받아야 합니다.
  - 탈퇴 시 사용자를 참조하는 데이터를 함께 정리합니다. **`users`를 참조하는 테이블을 새로 만들면 `UserService.withdraw`에 정리 로직을 추가해야 합니다.**
- **친구**
  - 친구 맺기는 초대 링크(QR 포함)가 메인, 친구 코드 입력이 보조이며, 모든 경로는 친구 요청 → 수락으로 통일합니다. 수락 전에는 친구가 아닙니다.
  - 두 사용자 사이의 친구 관계·대기 중인 요청은 방향과 관계없이 하나만 존재합니다. 동시에 요청·수락해도 중복이 생기지 않도록 DB 제약으로 보장합니다.
  - 자기 자신에게는 친구 요청을 보낼 수 없습니다.
  - 친구 삭제는 양쪽 모두에서 관계가 사라집니다.
- **시간표·공강**
  - 다른 사용자의 시간표·공강은 친구 관계일 때만 조회할 수 있습니다.
  - 요일·시간 계산은 KST 기준입니다.
  - 공강 조회 캐시는 시간표 변경, 친구 추가·삭제 시 무효화되어야 합니다.
- **채팅**: <!-- TODO -->
- **약속**: <!-- TODO -->
- **별점**: <!-- TODO -->
- **활동 추천**: <!-- TODO -->

## 코드 컨벤션
- 계층: `Controller → Service → Repository`. Controller에 비즈니스 로직을 두지 않습니다.
- API 경로는 `/api/v1/...` 형식입니다.
- 로그인 사용자는 Controller 파라미터에서 `@LoginUserId Long userId`로 받습니다.
- 요청/응답은 DTO(record)로 다루고, Entity → DTO 변환은 `from()` 정적 메서드로 합니다. Entity를 Controller 밖으로 노출하지 않습니다.
- 응답: 공통 래퍼 없이 성공 시 DTO를 그대로 반환합니다. 실패 응답은 `ErrorResponse(success, message, code)`로 `GlobalExceptionHandler`가 만듭니다.
- 예외: `ApiException(HttpStatus, 메시지)`를 던집니다. 메시지는 사용자에게 그대로 보여도 되는 한국어 문장으로 씁니다.
- `@Transactional`은 Service 계층에만, 조회는 `readOnly = true`.
- Entity에는 `@Setter`를 쓰지 않고, 상태 변경은 의미 있는 이름의 메서드로 합니다. 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
- 브랜치: `feat/기능명`, `refactor/대상` 등 → `main`으로 PR
- 커밋 메시지: `feat|fix|refactor|test|docs|chore: 한글 설명`

## 작업 규칙
- 작업을 마치기 전에 변경한 도메인의 테스트를 실행하고, 실패하면 고친 뒤 끝냅니다.
- 새 비즈니스 로직에는 테스트를 함께 작성합니다. 성공 케이스뿐 아니라 실패·예외 케이스도 포함합니다.
- 다른 사람 담당 도메인의 코드는 꼭 필요할 때만 고치고, 고쳤다면 무엇을 왜 고쳤는지 알려 줍니다.
- API 요청·응답을 바꾸면 노션 API 명세서와 맞는지 확인하고, 다르면 작업 결과에 명시합니다.
- 기존 코드의 패턴(응답 래퍼, 예외, 네이밍)을 먼저 찾아보고 그대로 따릅니다. 새 패턴을 만들지 않습니다.
- 요청받은 범위만 수정합니다. 관련 없는 리팩터링이나 포맷 변경을 섞지 않습니다.

## 팀 합의 없이 하지 않을 것
- `config/`, `security/`, `common/`의 공통 설정·보안·예외 처리 변경
- DB 스키마(Entity 필드·연관관계) 변경 — `ddl-auto: update`라서 Entity를 바꾸면 개발 DB 스키마가 바로 바뀝니다. 필요하면 변경안을 먼저 제안합니다
- 설정 파일에 키·비밀번호·토큰 하드코딩 (환경 변수 사용)
