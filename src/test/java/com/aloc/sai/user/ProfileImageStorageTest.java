package com.aloc.sai.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 다른 테스트는 이 클래스를 목으로 바꾸므로, S3에 보내는 키와 삭제 대상 판별은 여기서 확인한다.
class ProfileImageStorageTest {
    private static final String BASE_URL = "https://sai-bucket.s3.ap-northeast-2.amazonaws.com/";

    private S3Client s3Client;
    private ProfileImageStorage storage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        storage = new ProfileImageStorage(s3Client, "ap-northeast-2", "sai-bucket");
    }

    // 서버용 IAM 정책이 profile/* 에만 쓰기를 허용하므로 키가 이 경로 아래여야 한다.
    @Test
    @SuppressWarnings("unchecked")
    void IAM_정책이_허용하는_profile_경로_아래에_올린다() {
        String url = storage.upload(7L, new byte[]{1}, ProfileImageType.PNG);

        ArgumentCaptor<Consumer<PutObjectRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest.Builder builder = PutObjectRequest.builder();
        captor.getValue().accept(builder);
        PutObjectRequest request = builder.build();

        assertThat(request.bucket()).isEqualTo("sai-bucket");
        assertThat(request.key()).startsWith("profile/7/").endsWith(".png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo(BASE_URL + request.key());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 우리_버킷의_profile_경로_이미지만_지운다() {
        storage.delete(BASE_URL + "profile/7/abc.png");

        ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(s3Client).deleteObject(captor.capture());
        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build().key()).isEqualTo("profile/7/abc.png");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 카카오_프로필이나_다른_경로_URL은_지우지_않는다() {
        storage.delete("https://k.kakaocdn.net/profile.png");
        storage.delete(BASE_URL + "chat/7/abc.png");
        storage.delete(null);

        verify(s3Client, never()).deleteObject(any(Consumer.class));
    }
}
