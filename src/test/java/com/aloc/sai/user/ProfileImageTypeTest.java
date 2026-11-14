package com.aloc.sai.user;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileImageTypeTest {

    @Test
    void JPEG_PNG_WEBP를_시그니처로_판별한다() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDB};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ProfileImageType.detect(jpeg)).contains(ProfileImageType.JPEG);
        assertThat(ProfileImageType.detect(png)).contains(ProfileImageType.PNG);
        assertThat(ProfileImageType.detect(webp)).contains(ProfileImageType.WEBP);
    }

    @Test
    void 아이폰_HEIC와_HEIF를_브랜드로_판별한다() {
        byte[] heic = "\0\0\0\u0018ftypheic\0\0\0\0mif1heic".getBytes(StandardCharsets.ISO_8859_1);
        byte[] heif = "\0\0\0\u0018ftypmif1\0\0\0\0mif1heic".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ProfileImageType.detect(heic)).contains(ProfileImageType.HEIC);
        assertThat(ProfileImageType.detect(heif)).contains(ProfileImageType.HEIF);
    }

    @Test
    void 아이폰_다중_이미지_브랜드도_HEIC로_판별한다() {
        byte[] heim = "\0\0\0\u0018ftypheim\0\0\0\0mif1heim".getBytes(StandardCharsets.ISO_8859_1);
        byte[] heis = "\0\0\0\u0018ftypheis\0\0\0\0mif1heis".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ProfileImageType.detect(heim)).contains(ProfileImageType.HEIC);
        assertThat(ProfileImageType.detect(heis)).contains(ProfileImageType.HEIC);
    }

    @Test
    void 대표_브랜드가_mif1이어도_호환_브랜드에_avif가_있으면_판별하지_않는다() {
        byte[] avifAsMif1 = "\0\0\0\u001Cftypmif1\0\0\0\0mif1miafavif".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ProfileImageType.detect(avifAsMif1)).isEmpty();
    }

    @Test
    void HEVC_시퀀스는_판별하지_않는다() {
        byte[] hevc = "\0\0\0\u0018ftyphevc\0\0\0\0msf1hevc".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ProfileImageType.detect(hevc)).isEmpty();
    }

    @Test
    void 같은_구조인_MP4와_AVIF는_판별하지_않는다() {
        byte[] mp4 = "\0\0\0\u0018ftypisom\0\0\0\0isomiso2".getBytes(StandardCharsets.ISO_8859_1);
        byte[] avif = "\0\0\0\u0018ftypavif\0\0\0\0avifmif1".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ProfileImageType.detect(mp4)).isEmpty();
        assertThat(ProfileImageType.detect(avif)).isEmpty();
    }

    @Test
    void 지원하지_않거나_너무_짧은_파일은_판별하지_않는다() {
        byte[] gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        byte[] riffWithoutWebp = "RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ProfileImageType.detect(gif)).isEmpty();
        assertThat(ProfileImageType.detect(riffWithoutWebp)).isEmpty();
        assertThat(ProfileImageType.detect(new byte[]{(byte) 0xFF})).isEmpty();
        assertThat(ProfileImageType.detect(new byte[0])).isEmpty();
    }
}
