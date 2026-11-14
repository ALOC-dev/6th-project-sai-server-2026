package com.aloc.sai.user;

// 프로필 이미지 저장소. 테스트에서 S3 대신 바꿔 끼울 수 있도록 인터페이스로 둔다.
public interface ProfileImageStorage {

    // 이미지를 저장하고 클라이언트가 바로 쓸 수 있는 URL을 돌려준다.
    String upload(Long userId, byte[] content, ProfileImageType type);

    // 이 저장소에 올린 이미지만 지운다. 카카오 프로필 등 외부 URL이면 아무 것도 하지 않는다.
    void delete(String imageUrl);
}
