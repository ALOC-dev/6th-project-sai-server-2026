package com.aloc.sai.user;

import com.aloc.sai.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.UUID;

// 프로필 이미지를 S3에 저장·삭제한다.
// 키 prefix는 서버용 IAM 정책(sai-server-s3-policy)이 허용하는 profile/ 와 같아야 한다. 다르면 업로드가 AccessDenied로 실패한다.
// 앱은 응답의 URL로 이미지를 받는다. 버킷을 비공개로 둘지(CloudFront) profile/ 경로만 공개할지는 아직 정하지 않았다.
@Slf4j
@Component
public class ProfileImageStorage {
    private static final String KEY_PREFIX = "profile/";
    // 키에 UUID가 들어가 같은 URL의 내용이 바뀌지 않으므로 오래 캐시해도 된다.
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final String bucket;
    private final String baseUrl;

    public ProfileImageStorage(
            S3Client s3Client,
            @Value("${aws.region}") String region,
            @Value("${aws.s3.bucket}") String bucket
    ) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.baseUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/";
    }

    // 이미지를 저장하고 클라이언트가 바로 쓸 수 있는 URL을 돌려준다.
    public String upload(Long userId, byte[] content, ProfileImageType type) {
        String key = KEY_PREFIX + userId + "/" + UUID.randomUUID() + "." + type.extension();
        try {
            s3Client.putObject(request -> request
                            .bucket(bucket)
                            .key(key)
                            .contentType(type.contentType())
                            .cacheControl(CACHE_CONTROL),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "프로필 이미지를 저장하지 못했습니다.", e);
        }
        return baseUrl + key;
    }

    // 이 버킷에 올린 이미지만 지운다. 카카오 프로필 등 외부 URL이면 아무 것도 하지 않는다.
    // 정리 작업이라 실패해도 요청을 실패시키지 않는다. 남은 객체는 로그로 확인한다.
    public void delete(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(baseUrl + KEY_PREFIX)) {
            return;
        }
        String key = imageUrl.substring(baseUrl.length());
        try {
            s3Client.deleteObject(request -> request.bucket(bucket).key(key));
        } catch (SdkException e) {
            log.warn("프로필 이미지 삭제 실패: key={}", key, e);
        }
    }
}
