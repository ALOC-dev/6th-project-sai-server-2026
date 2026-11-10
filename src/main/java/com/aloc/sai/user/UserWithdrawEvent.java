package com.aloc.sai.user;

// 탈퇴하는 사용자를 참조하는 데이터를 각 도메인이 정리하도록 알린다.
// users를 참조하는 테이블을 만든 도메인은 이 이벤트의 @EventListener를 만들어야 한다. 빠뜨리면 탈퇴 시 FK 위반으로 실패한다.
//
// 리스너 규칙
// - 사용자 행을 지우기 전에 탈퇴와 같은 트랜잭션에서 동기로 실행된다(@TransactionalEventListener를 쓰지 않는다).
// - DB 정리만 한다. 푸시·외부 API 호출을 하지 않는다. 커밋 전에 실행되므로 탈퇴가 롤백돼도 되돌릴 수 없다.
// - 예외를 삼키지 않는다. 정리에 실패하면 탈퇴 전체가 롤백되어야 한다.
public record UserWithdrawEvent(Long userId) {
}
