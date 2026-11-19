package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserUpdateFormTest {

    @Test
    void updateForm_opensForUserWithoutAddressOrPhone() {
        User user = new User("a@a.com", "pw", "닉", LoginType.EMAIL, null);

        UserResponse.UpdateFormDto form = assertDoesNotThrow(() -> new UserResponse.UpdateFormDto(user));

        assertNull(form.getPostalCode());
        assertNull(form.getAddress());
        assertNull(form.getPhonePrefix());
    }

    @Test
    void validate_allowsChangingNicknameWithoutPhone() {
        UserRequest.UpdateDto dto = new UserRequest.UpdateDto();
        dto.setNickname("새닉네임");
        // 화면의 국번 select 는 항상 값이 있으므로 국번만 오는 경우도 허용해야 한다
        dto.setPhonePrefix("010");

        assertDoesNotThrow(dto::validate);
    }

    @Test
    void validate_allowsChangingPasswordWithoutPhone() {
        UserRequest.UpdateDto dto = new UserRequest.UpdateDto();
        dto.setPassword("newpassword1");

        assertDoesNotThrow(dto::validate);
    }

    @Test
    void validate_rejectsPartiallyEnteredPhone() {
        UserRequest.UpdateDto dto = new UserRequest.UpdateDto();
        dto.setNickname("새닉네임");
        dto.setPhonePrefix("010");
        dto.setPhoneMiddle("1234");

        assertThrows(BadRequestException.class, dto::validate);
    }

    @Test
    void validate_acceptsFullPhone() {
        UserRequest.UpdateDto dto = new UserRequest.UpdateDto();
        dto.setPhonePrefix("010");
        dto.setPhoneMiddle("1234");
        dto.setPhoneLast("5678");

        assertDoesNotThrow(dto::validate);
    }

    @Test
    void validate_rejectsEmptyRequest() {
        assertThrows(BadRequestException.class, () -> new UserRequest.UpdateDto().validate());
    }
}
