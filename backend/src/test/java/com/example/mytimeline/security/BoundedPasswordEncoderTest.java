package com.example.mytimeline.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link BoundedPasswordEncoder} の同時実行数の上限と、委譲先の結果をそのまま返すことを確かめる。
 *
 * <p>設計技法: 境界値分析（上限 = 1 / 上限より多い同時要求 / 0 は不正）と、
 * 分岐網羅（encode / matches / upgradeEncoding の各経路、および例外時の解放）。</p>
 */
class BoundedPasswordEncoderTest {

    /**
     * 「同時に何本が中で動いているか」を数える偽物のエンコーダ。
     * 全員が入口に到達するまで中で待つので、上限が効いていなければ同時実行数が上限を超える。
     */
    private static final class CountingEncoder implements PasswordEncoder {
        private final AtomicInteger inside = new AtomicInteger();
        private final AtomicInteger maxInside = new AtomicInteger();
        private final CountDownLatch release;

        CountingEncoder(CountDownLatch release) {
            this.release = release;
        }

        private <T> T track(T result) {
            int now = inside.incrementAndGet();
            maxInside.accumulateAndGet(now, Math::max);
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inside.decrementAndGet();
            }
            return result;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return track("hashed:" + rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return track(encodedPassword.equals("hashed:" + rawPassword));
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return true;
        }
    }

    /** 委譲先が例外を投げても、許可が解放されて次の呼び出しが通ることを見るための偽物。 */
    private static final class ThrowingEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            throw new IllegalStateException("boom");
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            throw new IllegalStateException("boom");
        }
    }

    @ParameterizedTest(name = "上限 {0}")
    @ValueSource(ints = {1, 2})
    @DisplayName("境界値: 上限より多く同時に呼んでも、中で動く数は上限を超えない")
    void concurrentCallsNeverExceedLimit(int limit) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountingEncoder counting = new CountingEncoder(release);
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(counting, limit);
        int callers = limit + 3;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> encoder.matches("pw", "hashed:pw")));
            }
            // 上限分が中に入って待っている状態になるまで少し待ってから、全員を解放する
            Thread.sleep(200);
            assertThat(counting.inside.get()).isEqualTo(limit);
            release.countDown();
            for (Future<Boolean> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(counting.maxInside.get()).isEqualTo(limit);
    }

    @Test
    @DisplayName("境界値: 上限 0 は不正")
    void rejectsZeroLimit() {
        assertThatThrownBy(() -> new BoundedPasswordEncoder(new CountingEncoder(new CountDownLatch(0)), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("分岐網羅: encode / matches / upgradeEncoding は委譲先の結果をそのまま返す")
    void delegatesResults() {
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(new CountingEncoder(new CountDownLatch(0)), 1);

        assertThat(encoder.encode("pw")).isEqualTo("hashed:pw");
        assertThat(encoder.matches("pw", "hashed:pw")).isTrue();
        assertThat(encoder.matches("other", "hashed:pw")).isFalse();
        assertThat(encoder.upgradeEncoding("hashed:pw")).isTrue();
    }

    @Test
    @DisplayName("分岐網羅（割り込み）: 順番待ち中に割り込まれたら例外にし、割り込み状態は戻しておく")
    void interruptedWhileWaitingIsReportedAndFlagRestored() {
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(new CountingEncoder(new CountDownLatch(0)), 1);
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> encoder.matches("pw", "hashed:pw"))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(InterruptedException.class);
            // 飲み込むと、呼び出し元（スレッドプールなど）が停止要求に気づけなくなる
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // 後続のテストに割り込み状態を持ち越さない
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("分岐網羅（例外）: 委譲先が例外を投げても許可が解放され、次の呼び出しが待たされない")
    void releasesPermitWhenDelegateThrows() {
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(new ThrowingEncoder(), 1);

        assertThatThrownBy(() -> encoder.encode("pw")).isInstanceOf(IllegalStateException.class);
        // 解放されていなければここで永久に待つ
        assertThatThrownBy(() -> encoder.matches("pw", "x")).isInstanceOf(IllegalStateException.class);
    }
}
