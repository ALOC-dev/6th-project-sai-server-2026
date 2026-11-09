# 사이(SAI) 서버

알록 6기 프로젝트 '사이'의 백엔드 서버입니다. 공강 시간이 겹치는 친구를 찾고 활동을 추천합니다.

## 기술 스택
- Java 21, Spring Boot 4.1.1, Spring Data JPA, MySQL
- Spring Security + JWT, 카카오 로그인

## 실행
```bash
./gradlew test                                             # 테스트 (H2 사용, DB 불필요)
./gradlew bootRun --args='--spring.profiles.active=local'  # 로컬 실행
```
로컬 실행에는 환경 변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`가 필요합니다.

## 문서
- API 문서: 서버 실행 후 `/swagger-ui.html`
- [노션 API 명세서](https://app.notion.com/p/API-40931c4f7fe780848a56c6dbf93a87c2)
- 개발 규칙·도메인 규칙: [CLAUDE.md](CLAUDE.md)
