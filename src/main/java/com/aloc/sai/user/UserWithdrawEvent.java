package com.aloc.sai.user;

// 탈퇴하는 사용자를 참조하는 데이터를 각 도메인이 정리하도록 알린다.
// 사용자 행을 지우기 전에 같은 트랜잭션 안에서 발행된다.
public record UserWithdrawEvent(Long userId) {
}
