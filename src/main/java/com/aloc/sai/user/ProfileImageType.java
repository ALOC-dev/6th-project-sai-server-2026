package com.aloc.sai.user;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

// 업로드를 허용하는 이미지 형식. 클라이언트가 보낸 Content-Type이나 파일명은 믿지 않고 파일 앞부분의 시그니처로 판별한다.
public enum ProfileImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    // 아이폰 카메라 기본 형식
    HEIC("image/heic", "heic"),
    HEIF("image/heif", "heif");

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_MARK = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix");
    private static final Set<String> HEIF_BRANDS = Set.of("mif1");

    private final String contentType;
    private final String extension;

    ProfileImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ProfileImageType> detect(byte[] content) {
        if (startsWith(content, 0, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        // WEBP는 "RIFF" + 4바이트 크기 + "WEBP"로 시작한다.
        if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_MARK)) {
            return Optional.of(WEBP);
        }
        // HEIC·HEIF는 4바이트 크기 + "ftyp" + 4바이트 브랜드로 시작한다. 같은 구조인 MP4·AVIF는 브랜드로 걸러낸다.
        if (startsWith(content, 4, FTYP) && content.length >= 12) {
            String brand = new String(content, 8, 4, StandardCharsets.US_ASCII);
            if (HEIC_BRANDS.contains(brand)) {
                return Optional.of(HEIC);
            }
            if (HEIF_BRANDS.contains(brand)) {
                return Optional.of(HEIF);
            }
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        return Arrays.equals(content, offset, offset + signature.length, signature, 0, signature.length);
    }
}
