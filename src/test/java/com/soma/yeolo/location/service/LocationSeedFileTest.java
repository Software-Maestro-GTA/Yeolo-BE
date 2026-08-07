package com.soma.yeolo.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 기준 데이터셋 파서를 검증한다. 형식이 깨진 파일을 조용히 일부만 읽으면 자동완성 후보가 소리 없이
 * 비므로, 잘못된 입력은 반드시 예외로 드러나야 한다.
 */
class LocationSeedFileTest {

    private InputStream tsv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 버전과_데이터_행을_읽는다() {
        LocationSeedFile file = LocationSeedFile.parse(tsv("""
                #version\tcldr-geonames-20260807
                # countryId\tnameKo
                KR\t대한민국
                JP\t일본
                """), 2);

        assertThat(file.version()).isEqualTo("cldr-geonames-20260807");
        assertThat(file.rows()).hasSize(2);
        assertThat(file.rows().get(0)).containsExactly("KR", "대한민국");
    }

    @Test
    void 주석과_빈_줄은_건너뛴다() {
        LocationSeedFile file = LocationSeedFile.parse(tsv("""
                #version\tv1
                # 주석

                KR\t대한민국

                # 또 주석
                JP\t일본
                """), 2);

        assertThat(file.rows()).hasSize(2);
    }

    @Test
    void 버전_헤더가_없으면_실패한다() {
        assertThatThrownBy(() -> LocationSeedFile.parse(tsv("KR\t대한민국\n"), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#version");
    }

    @Test
    void 열_수가_맞지_않으면_줄_번호와_함께_실패한다() {
        assertThatThrownBy(() -> LocationSeedFile.parse(tsv("""
                #version\tv1
                KR\t대한민국
                JP\t일본\t군더더기
                """), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3번째 줄");
    }

    @Test
    void 열이_하나_더_붙은_줄도_실패한다() {
        // split 의 기본 limit(0)이면 끝의 빈 필드가 잘려나가 이런 줄이 통과해 버린다.
        assertThatThrownBy(() -> LocationSeedFile.parse(tsv("""
                #version\tv1
                KR\t대한민국\t
                """), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2번째 줄");
    }

    @Test
    void 값이_빈_줄은_실패한다() {
        assertThatThrownBy(() -> LocationSeedFile.parse(tsv("""
                #version\tv1
                KR\t
                """), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2번째 값이 비어 있다");
    }

    @Test
    void 버전만_따로_읽을_수_있다() {
        assertThat(LocationSeedFile.parseVersion(tsv("""
                #version\tv1
                # 주석
                KR\t대한민국
                """))).isEqualTo("v1");
    }

    @Test
    void 버전만_읽을_때도_헤더가_없으면_실패한다() {
        assertThatThrownBy(() -> LocationSeedFile.parseVersion(tsv("KR\t대한민국\n")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#version");
    }

    @Test
    void 빈_데이터_파일도_버전만_있으면_읽힌다() {
        LocationSeedFile file = LocationSeedFile.parse(tsv("#version\tv1\n"), 2);

        assertThat(file.version()).isEqualTo("v1");
        assertThat(file.rows()).isEmpty();
    }
}
