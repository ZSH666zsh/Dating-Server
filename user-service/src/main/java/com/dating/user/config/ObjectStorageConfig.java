package com.dating.user.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * MinIO（S3 兼容）客户端对象存储配置。
 *
 * 从 Nacos 配置注入 dating.object-storage.*，提供 S3Client 和 S3Presigner 两个 Bean。
 * 对应 dev-onboarding.md §5 MinIO。
 *
 * <p>实现说明：这是在 user-service 接入真实 MinIO presigned URL 时补充的。
 */
@Slf4j
@Configuration
public class ObjectStorageConfig {

    @Value("${dating.object-storage.endpoint:https://minio-api.jianjiange.site}")
    private String endpoint;

    @Value("${dating.object-storage.region:us-east-1}")
    private String region;

    @Value("${dating.object-storage.access-key:}")
    private String accessKey;

    @Value("${dating.object-storage.secret-key:}")
    private String secretKey;

    @Value("${dating.object-storage.path-style-access:true}")
    private boolean pathStyleAccess;

    @Value("${dating.object-storage.bucket:dating-zhaoshihang}")
    private String bucket;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(pathStyleAccess)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    public String getBucket() {
        return bucket;
    }
}
