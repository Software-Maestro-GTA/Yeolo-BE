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
 * 기준 데이터셋(TSV) 파서. 국가·도시 파일을 같은 규칙으로 읽는다.
 *
 * <p>형식은 이렇다 — 첫 줄에 버전, {@code #}로 시작하는 줄은 주석, 나머지는 탭으로 구분된 데이터다.
 * <pre>
 * #version	geonames-20260807
 * # cityId	nameKo	countryId	population
 * 1835848	서울	KR	10349312
 * </pre>
 *
 * <p>버전은 필수다. 재적재 여부를 이 값으로 판단하므로(={@code location_seed_state}), 없으면
 * 데이터셋이 바뀌었는지 알 수 없다 — 조용히 넘어가지 않고 적재를 실패시킨다.
 *
 * <p>검색 키({@code searchName}·{@code searchChosung})는 파일에 담지 않고 적재 시점에 계산한다.
 * 파일에 넣으면 앱의 키 규칙과 파일이 어긋날 수 있고, 규칙을 고칠 때마다 데이터셋 생성 스크립트까지
 * 함께 고쳐야 한다.
 *
 * @param version 데이터셋 버전 ({@code #version} 헤더 값)
 * @param rows    데이터 행. 각 행은 정확히 {@code columns}개의 필드를 갖는다
 */
public record LocationSeedFile(String version, List<String[]> rows) {

    private static final String COMMENT_PREFIX = "#";
    private static final String VERSION_MARKER = "#version";

    /**
     * {@code #version} 헤더만 읽고 멈춘다. 재적재가 필요한지 판단하는 데는 이 값만 있으면 되므로,
     * 평시 부팅에서 3만 행을 파싱하지 않기 위한 경로다.
     *
     * @throws IllegalStateException {@code #version} 헤더가 없을 때
     */
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

    /**
     * TSV 스트림을 읽어 파싱한다. 스트림은 호출자가 닫는다.
     *
     * @param columns 데이터 행이 가져야 할 필드 수. 다르면 어느 줄인지와 함께 실패시킨다
     * @throws IllegalStateException {@code #version} 헤더가 없거나 필드 수가 맞지 않을 때
     */
    public static LocationSeedFile parse(InputStream in, int columns) {
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
                // 열 수만 세면 "KR\t"처럼 값이 빈 줄은 그대로 통과한다. 이름 없는 국가·도시가
                // 후보로 나가느니 적재를 실패시키는 편이 낫다.
                for (int i = 0; i < fields.length; i++) {
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
