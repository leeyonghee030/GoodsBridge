package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    // ===== 주소: 안 적어도 되지만, 적었다면 상세주소까지 필수 =====

    private static UserRequest.UpdateDto address(String postalCode, String base, String detail) {
        UserRequest.UpdateDto dto = new UserRequest.UpdateDto();
        dto.setNickname("닉");
        dto.setPostalCode(postalCode);
        dto.setAddress(base);
        dto.setDetailAddress(detail);
        return dto;
    }

    @Test
    void validate_allowsNoAddressAtAll() {
        assertDoesNotThrow(address(null, null, null)::validate);
        assertDoesNotThrow(address("", "", "")::validate);
    }

    @Test
    void validate_requiresDetailAddressWhenAddressIsEntered() {
        BadRequestException e = assertThrows(BadRequestException.class, address("04420", "서울 용산구 유엔빌리지길 1", " ")::validate);
        assertEquals("상세주소(동·호수 등)를 입력해 주세요.", e.getMessage());
        assertDoesNotThrow(address("04420", "서울 용산구 유엔빌리지길 1", "101동 202호")::validate);
    }

    @Test
    void validate_rejectsTooLongDetailAddress() {
        assertThrows(BadRequestException.class, address("04420", "서울 용산구 유엔빌리지길 1", "가".repeat(61))::validate);
        assertDoesNotThrow(address("04420", "서울 용산구 유엔빌리지길 1", "가".repeat(60))::validate);
    }

    @Test
    void validate_rejectsEmptyRequest() {
        assertThrows(BadRequestException.class, () -> new UserRequest.UpdateDto().validate());
    }
}
