package com.soma.yeolo.course.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SavedCourseTest {

    private SavedCourse courseOwnedBy(UUID userId) {
        return new SavedCourse(UUID.randomUUID(), userId, "제주 코스", "대한민국", "제주",
                LocalDate.of(2026, 8, 1), 3, List.of("힐링"), "이유", "{\"days\":[]}", Instant.now());
    }

    @Test
    void 소유자면_true를_반환한다() {
        UUID owner = UUID.randomUUID();

        assertThat(courseOwnedBy(owner).isOwnedBy(owner)).isTrue();
    }

    @Test
    void 소유자가_아니면_false를_반환한다() {
        assertThat(courseOwnedBy(UUID.randomUUID()).isOwnedBy(UUID.randomUUID())).isFalse();
    }
}
