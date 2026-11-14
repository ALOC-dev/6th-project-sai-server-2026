package com.aloc.sai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

// 자격 증명은 SDK 기본 체인(환경 변수 AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY 또는 IAM 역할)으로 읽는다.
// S3를 쓰는 도메인은 이 S3Client를 주입받아 쓴다. 애플리케이션 종료 시 close()가 자동으로 호출된다.
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
