package dev.deliverydrill.core;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationParserTest {
    @Test void parsesCommonUnits() {
        assertThat(DurationParser.parse("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(DurationParser.parse("1.5s")).isEqualTo(Duration.ofMillis(1500));
        assertThat(DurationParser.parse("2m")).isEqualTo(Duration.ofMinutes(2));
    }
    @Test void rejectsInvalidDuration() { assertThatThrownBy(() -> DurationParser.parse("soon")).isInstanceOf(IllegalArgumentException.class); }
}

