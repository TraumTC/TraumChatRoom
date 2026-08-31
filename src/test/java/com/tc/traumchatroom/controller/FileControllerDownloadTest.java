package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FileController 下载接口：缓存头、条件请求、Content-Disposition 单元测试
 *
 * 背景：聊天列表是虚拟滚动，图片/头像的 img 会随列表回收反复重建；
 * 缺少缓存头时每次重建都要重新走网络下载整张原图，移动端尤其慢。
 */
class FileControllerDownloadTest {

    @TempDir
    Path tempDir;

    private FileController controller;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        controller = new FileController();
        fileService = mock(FileService.class);
        ReflectionTestUtils.setField(controller, "fileService", fileService);
    }

    private MockHttpServletRequest requestFor(String fileName) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/file/download/" + fileName);
        request.setRequestURI("/api/file/download/" + fileName);
        return request;
    }

    /** 落一个真实文件到临时目录，并让 fileService 解析到它 */
    private void givenStoredFile(String fileName, String content) throws Exception {
        Path p = tempDir.resolve(fileName);
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        when(fileService.getFilePath(anyString())).thenReturn(p.toString());
    }

    // ---------- 缓存头 ----------

    @Test
    void responseCarriesLongLivedImmutableCacheAndValidators() throws Exception {
        givenStoredFile("1786025377275_9f8a1201.png", "fake-png");

        ResponseEntity<FileSystemResource> res =
                controller.download(requestFor("1786025377275_9f8a1201.png"), null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cacheControl = res.getHeaders().getCacheControl();
        assertThat(cacheControl)
                .contains("max-age=31536000")
                .contains("public")
                .contains("immutable");
        assertThat(res.getHeaders().getETag()).isNotBlank();
        assertThat(res.getHeaders().getLastModified()).isPositive();
    }

    @Test
    void matchingIfNoneMatchReturns304WithoutBody() throws Exception {
        givenStoredFile("1786025377275_9f8a1201.png", "fake-png");

        String etag = controller.download(requestFor("1786025377275_9f8a1201.png"), null)
                .getHeaders().getETag();

        MockHttpServletRequest conditional = requestFor("1786025377275_9f8a1201.png");
        conditional.addHeader(HttpHeaders.IF_NONE_MATCH, etag);
        ResponseEntity<FileSystemResource> res = controller.download(conditional, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(res.getBody()).isNull();
        assertThat(res.getHeaders().getCacheControl()).contains("immutable");
    }

    @Test
    void staleIfNoneMatchStillReturnsFullBody() throws Exception {
        givenStoredFile("1786025377275_9f8a1201.png", "fake-png");

        MockHttpServletRequest conditional = requestFor("1786025377275_9f8a1201.png");
        conditional.addHeader(HttpHeaders.IF_NONE_MATCH, "\"0-0\"");
        ResponseEntity<FileSystemResource> res = controller.download(conditional, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
    }

    // ---------- Content-Disposition ----------

    @Test
    void explicitNameParamForcesAttachmentWithEncodedFileName() throws Exception {
        givenStoredFile("1786025377275_9f8a1201.png", "fake-png");

        ResponseEntity<FileSystemResource> res =
                controller.download(requestFor("1786025377275_9f8a1201.png"), "我的照片.png");

        String disposition = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).startsWith("attachment;");
        assertThat(disposition).contains("filename*=UTF-8''");
        assertThat(disposition).doesNotContain("+");   // 空格必须编码成 %20 而不是 +
    }

    @Test
    void nonMediaFileStaysAttachment() throws Exception {
        givenStoredFile("1786026575481_48dbb605.zip", "fake-zip");

        ResponseEntity<FileSystemResource> res =
                controller.download(requestFor("1786026575481_48dbb605.zip"), null);

        assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
    }

    @Test
    void inlineResponsesForbidMimeSniffing() throws Exception {
        givenStoredFile("1786025377275_9f8a1201.png", "fake-png");

        ResponseEntity<FileSystemResource> res =
                controller.download(requestFor("1786025377275_9f8a1201.png"), null);

        assertThat(res.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    // ---------- inline 白名单 ----------

    @Test
    void rasterImagesAndMediaAreInlineSafe() {
        assertThat(FileController.isInlineSafe("image/png")).isTrue();
        assertThat(FileController.isInlineSafe("image/jpeg")).isTrue();
        assertThat(FileController.isInlineSafe("image/webp")).isTrue();
        assertThat(FileController.isInlineSafe("IMAGE/GIF")).isTrue();   // 大小写不敏感
        assertThat(FileController.isInlineSafe("video/mp4")).isTrue();
        assertThat(FileController.isInlineSafe("audio/mpeg")).isTrue();
    }

    @Test
    void svgAndDocumentsAreNotInlineSafe() {
        // SVG 内联可执行脚本 → 存储型 XSS，必须走 attachment
        assertThat(FileController.isInlineSafe("image/svg+xml")).isFalse();
        assertThat(FileController.isInlineSafe("application/pdf")).isFalse();
        assertThat(FileController.isInlineSafe("application/zip")).isFalse();
        assertThat(FileController.isInlineSafe("text/html")).isFalse();
        assertThat(FileController.isInlineSafe("application/octet-stream")).isFalse();
        assertThat(FileController.isInlineSafe(null)).isFalse();
    }

    // ---------- 不存在 / 非普通文件 ----------

    @Test
    void missingFileReturns404() {
        when(fileService.getFilePath(anyString())).thenReturn(tempDir.resolve("nope.png").toString());

        ResponseEntity<FileSystemResource> res = controller.download(requestFor("nope.png"), null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void directoryPathReturns404() {
        when(fileService.getFilePath(anyString())).thenReturn(tempDir.toString());

        ResponseEntity<FileSystemResource> res = controller.download(requestFor("avatars"), null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void requestOutsideDownloadPrefixIsBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/file/other");
        request.setRequestURI("/api/file/other");

        ResponseEntity<FileSystemResource> res = controller.download(request, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
