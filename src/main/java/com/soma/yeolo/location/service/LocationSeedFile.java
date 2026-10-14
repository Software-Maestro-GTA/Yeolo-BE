package com.soma.yeolo.location.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 기준 데이터셋 TSV 파일 (docs/location-dataset.md).
 *
 * <p>첫 줄은 {@code #version<TAB><버전>} 헤더이고, {@code #}로 시작하는 줄은 주석, 빈 줄은 무시한다.
 * 나머지는 탭 구분 데이터 줄이다. 열 수가 다르거나 필수 열이 비면 몇 번째 줄인지와 함께 실패한다 —
 * 이름 없는 국가·도시가 후보로 나가느니 적재를 실패시키는 편이 낫다.
 *
 * @param version 데이터셋 버전 ({@code #version} 헤더)
 * @param rows    데이터 줄. 선택 열이 비어 있으면 빈 문자열이다
 */
public record LocationSeedFile(String version, List<String[]> rows) {

    private static final String COMMENT_PREFIX = "#";
    private static final String VERSION_MARKER = "#version";

    /** 파일 앞머리의 {@code #version} 헤더만 읽는다. 평시 부팅이 전체 파싱을 피하기 위한 경로다. */
    public static String parseVersion(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(VERSION_MARKER)) {
                    String version = line.substring(VERSION_MARKER.length()).strip();
                    if (!version.isEmpty()) {
                        return version;
                    }
                }
                // 버전은 파일 앞머리에 있다. 데이터 줄까지 왔다면 헤더가 없는 것이다.
                if (!line.isBlank() && !line.startsWith(COMMENT_PREFIX)) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalStateException("기준 데이터셋에 #version 헤더가 없다");
    }

    /** 모든 열이 필수인 파일을 읽는다. */
    public static LocationSeedFile parse(InputStream in, int columns) {
        return parse(in, columns, columns);
    }

    /**
     * 파일을 읽는다. 앞의 {@code requiredColumns}개 열은 비면 실패하고, 그 뒤 열은 비어 있어도 된다
     * (예: countries.tsv 의 수도·통화 — 남극처럼 값이 없는 국가가 있다).
     */
    public static LocationSeedFile parse(InputStream in, int columns, int requiredColumns) {
        String version = null;
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                if (line.startsWith(COMMENT_PREFIX)) {
                    if (version == null && line.startsWith(VERSION_MARKER)) {
                        version = line.substring(VERSION_MARKER.length()).strip();
                    }
                    continue;
                }
                // limit=-1: 끝의 빈 필드도 남긴다. 기본값(0)이면 끝의 빈 필드가 잘려나가
                // "KR\t대한민국\t"(열이 하나 더 붙은 줄)가 검증을 통과해 버린다.
                String[] fields = line.split("\t", -1);
                if (fields.length != columns) {
                    throw new IllegalStateException(
                            "기준 데이터셋 %d번째 줄의 열 수가 %d이 아니다: %d".formatted(lineNo, columns, fields.length));
                }
                // 열 수만 세면 "KR\t"처럼 값이 빈 줄은 그대로 통과한다. 필수 열은 여기서 잡는다.
                for (int i = 0; i < requiredColumns; i++) {
                    if (fields[i].isBlank()) {
                        throw new IllegalStateException(
                                "기준 데이터셋 %d번째 줄의 %d번째 값이 비어 있다".formatted(lineNo, i + 1));
                    }
                }
                rows.add(fields);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (version == null || version.isEmpty()) {
            throw new IllegalStateException("기준 데이터셋에 #version 헤더가 없다");
        }
        return new LocationSeedFile(version, rows);
    }
}
