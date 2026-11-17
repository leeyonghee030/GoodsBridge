package com.goodsbridge._core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class FileUtilDeleteTest {

    @Test
    void 카카오_프로필처럼_http_주소는_파일로_보고_지우려_하지_않는다() {
        assertThatCode(() -> FileUtil.deleteFile("http://k.kakaocdn.net/dn/bzFJkO/dJMcagLHYP8/RkNYWOMvwwuJTFOXw7p6zk/img_640x640.jpg"))
            .doesNotThrowAnyException();
        assertThatCode(() -> FileUtil.deleteFile("https://k.kakaocdn.net/a.jpg")).doesNotThrowAnyException();
    }

    @Test
    void 이름이_없거나_없는_파일이어도_예외가_없다() {
        assertThatCode(() -> FileUtil.deleteFile(null)).doesNotThrowAnyException();
        assertThatCode(() -> FileUtil.deleteFile(" ")).doesNotThrowAnyException();
        assertThatCode(() -> FileUtil.deleteFile("no-such-file.png")).doesNotThrowAnyException();
    }
}
