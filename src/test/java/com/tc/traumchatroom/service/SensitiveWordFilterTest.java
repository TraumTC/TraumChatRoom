package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.SensitiveWord;
import com.tc.traumchatroom.mapper.SensitiveWordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * SensitiveWordFilter 单元测试（mock 数据库，验证三级处理策略与刷新期间的并发安全）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SensitiveWordFilterTest {

    @Mock
    private SensitiveWordMapper sensitiveWordMapper;

    @InjectMocks
    private SensitiveWordFilter filter;

    private SensitiveWord word(String text, int level) {
        SensitiveWord w = new SensitiveWord();
        w.setWord(text);
        w.setLevel(level);
        return w;
    }

    @BeforeEach
    void setUp() {
        // 预置敏感词库：广告(替换)、违禁(拦截)
        when(sensitiveWordMapper.findAll())
                .thenReturn(List.of(word("广告", 1), word("违禁", 2)));
        filter.refresh();
    }

    @Test
    void replaceLevelWordIsMasked() {
        FilterResult result = filter.filter("这是一个广告词");
        assertThat(result.isReplaced()).isTrue();
        assertThat(result.getContent()).doesNotContain("广告");
        assertThat(result.getContent()).contains("*");
    }

    @Test
    void blockLevelWordIsRejected() {
        FilterResult result = filter.filter("这是违禁内容");
        assertThat(result.isBlocked()).isTrue();
    }

    @Test
    void cleanContentPassesThrough() {
        FilterResult result = filter.filter("今天天气不错");
        assertThat(result.isReplaced()).isFalse();
        assertThat(result.isBlocked()).isFalse();
        assertThat(result.getContent()).isEqualTo("今天天气不错");
    }

    @Test
    void nullOrEmptyContentPasses() {
        assertThat(filter.filter(null).isBlocked()).isFalse();
        assertThat(filter.filter("").isBlocked()).isFalse();
    }

    @Test
    void wordCountReflectsLoadedSnapshot() {
        assertThat(filter.getWordCount()).isEqualTo(2);
    }

    // ---------- 刷新期间的并发安全（P1-7 回归） ----------

    /**
     * 构造一个独立的 filter 实例，词库放大到 wordCount 条以拉开重建窗口。
     * stubOnly 关掉 Mockito 的调用记录：这里会有极高频调用，既避免内存膨胀，也避开并发记录。
     */
    private SensitiveWordFilter filterWithLargeWordBook(int padding) throws Exception {
        List<SensitiveWord> many = new ArrayList<>();
        many.add(word("违禁", 2));
        for (int i = 0; i < padding; i++) {
            many.add(word("填充词" + i, 1));
        }
        SensitiveWordMapper stub = mock(SensitiveWordMapper.class, withSettings().stubOnly());
        when(stub.findAll()).thenReturn(many);

        SensitiveWordFilter target = new SensitiveWordFilter();
        Field f = SensitiveWordFilter.class.getDeclaredField("sensitiveWordMapper");
        f.setAccessible(true);
        f.set(target, stub);
        target.refresh();
        return target;
    }

    /**
     * 刷新词库期间，并发读的消息不得漏过过滤。
     *
     * 旧实现 refresh() 先 clear() 再逐词重建，读线程在这个窗口里匹配的是空 Trie
     * → 违禁词直接放行。此测试在旧实现下会稳定失败。
     */
    @Test
    void filterNeverBypassesWhileRefreshing() throws Exception {
        SensitiveWordFilter target = filterWithLargeWordBook(5000);

        int readerCount = 8;
        AtomicInteger leaked = new AtomicInteger();
        AtomicInteger checked = new AtomicInteger();
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int r = 0; r < readerCount; r++) {
            Thread reader = new Thread(() -> {
                await(go);
                while (!stop.get()) {
                    // 该消息含 level=2 敏感词，任何时刻都必须被拦截
                    if (!target.filter("这是违禁内容").isBlocked()) {
                        leaked.incrementAndGet();
                    }
                    checked.incrementAndGet();
                }
            });
            threads.add(reader);
            reader.start();
        }

        Thread refresher = new Thread(() -> {
            await(go);
            while (!stop.get()) {
                target.refresh();
            }
        });
        threads.add(refresher);
        refresher.start();

        go.countDown();
        Thread.sleep(1500);
        stop.set(true);
        for (Thread t : threads) t.join(5000);

        assertThat(checked.get()).isGreaterThan(1000);   // 确认真的压上量了
        assertThat(leaked.get()).isZero();
    }

    /**
     * 刷新失败时旧词库必须继续生效。
     *
     * 旧实现已经 clear() 之后才查库，findAll() 抛异常 → 词库永久停在空状态，
     * 过滤功能直到下次成功刷新前彻底失效。
     */
    @Test
    void failedRefreshKeepsPreviousWordBook() {
        when(sensitiveWordMapper.findAll()).thenThrow(new RuntimeException("数据库连接中断"));

        assertThatThrownBy(() -> filter.refresh()).isInstanceOf(RuntimeException.class);

        // 旧快照仍在生效
        assertThat(filter.filter("这是违禁内容").isBlocked()).isTrue();
        assertThat(filter.filter("这是一个广告词").isReplaced()).isTrue();
        assertThat(filter.getWordCount()).isEqualTo(2);
    }

    /**
     * 刷新前后语义一致：同一趟过滤内不会出现「长度取自旧树、级别取自新树」的撕裂读。
     * 词表在「违禁=拦截」与「违禁=替换」之间反复切换，结果只允许是这两种之一，
     * 绝不允许出现「既没拦截也没替换」（即漏过）。
     */
    @Test
    void filterStaysConsistentWhenWordLevelFlips() throws Exception {
        SensitiveWordMapper stub = mock(SensitiveWordMapper.class, withSettings().stubOnly());
        AtomicInteger flip = new AtomicInteger();
        when(stub.findAll()).thenAnswer(inv ->
                List.of(word("违禁", flip.incrementAndGet() % 2 == 0 ? 2 : 1)));

        SensitiveWordFilter target = new SensitiveWordFilter();
        Field f = SensitiveWordFilter.class.getDeclaredField("sensitiveWordMapper");
        f.setAccessible(true);
        f.set(target, stub);
        target.refresh();

        AtomicInteger leaked = new AtomicInteger();
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch go = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            await(go);
            while (!stop.get()) {
                FilterResult res = target.filter("这是违禁内容");
                if (!res.isBlocked() && !res.isReplaced()) {
                    leaked.incrementAndGet();
                }
            }
        });
        Thread refresher = new Thread(() -> {
            await(go);
            while (!stop.get()) {
                target.refresh();
            }
        });
        reader.start();
        refresher.start();
        go.countDown();
        Thread.sleep(1000);
        stop.set(true);
        reader.join(5000);
        refresher.join(5000);

        assertThat(leaked.get()).isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
