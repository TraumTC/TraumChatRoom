package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FileServiceImpl 单元测试（路径遍历防护）
 */
class FileServiceImplTest {

    private FileServiceImpl newService(String uploadDir) {
        FileServiceImpl svc = new FileServiceImpl();
        ReflectionTestUtils.setField(svc, "uploadDir", uploadDir);
        return svc;
    }

    @Test
    void getFilePathRejectsTraversal() {
        FileServiceImpl svc = newService("D:/uploads/");
        assertThatThrownBy(() -> svc.getFilePath("../../application.yml"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void getFilePathRejectsAbsolutePath() {
        FileServiceImpl svc = newService("D:/uploads/");
        assertThatThrownBy(() -> svc.getFilePath("D:/windows/system.ini"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void getFilePathRejectsEncodedTraversal() {
        FileServiceImpl svc = newService("D:/uploads/");
        // ..\ 与 ../ 两种分隔符形式都必须被拦截
        assertThatThrownBy(() -> svc.getFilePath("..\\..\\application.yml"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> svc.getFilePath("../application.yml"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void getFilePathRejectsBlank() {
        FileServiceImpl svc = newService("D:/uploads/");
        assertThatThrownBy(() -> svc.getFilePath(""))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void getFilePathAcceptsNormalFile() {
        FileServiceImpl svc = newService("D:/uploads/");
        String path = svc.getFilePath("msg_123_abcd1234.txt");
        java.nio.file.Path p = java.nio.file.Paths.get(path);
        assertThat(p.toAbsolutePath().normalize().startsWith(java.nio.file.Paths.get("D:/uploads/"))).isTrue();
        assertThat(path).doesNotContain("..");
    }
}
