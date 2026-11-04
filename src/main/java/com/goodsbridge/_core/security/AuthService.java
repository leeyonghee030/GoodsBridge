
package com.goodsbridge._core.security;

import com.goodsbridge._core.exception.CustomException;
import com.goodsbridge._core.exception.ErrorCode;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    // 로그인 성공 후 토큰 발급
    @Transactional
    public TokenResponse login(User user) {

        String accessToken = jwtProvider.createAccessToken(
            user.getId(),
            user.getRole().name()
        );

        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        user.updateRefreshTokenHash(hashToken(refreshToken));

        return TokenResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
    }

    // Refresh Token 검증 후 토큰 재발급
    @Transactional
    public TokenResponse reissue(String refreshToken) {

        var claims = jwtProvider.validateRefreshToken(refreshToken);
        Long userId = jwtProvider.getUserId(claims);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "토큰에 해당하는 사용자를 찾을 수 없습니다."));

        String storedHash = user.getRefreshTokenHash();
        String requestHash = hashToken(refreshToken);

        if (storedHash == null ||
            !MessageDigest.isEqual(
                requestHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
            )) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다.");
        }

        String newAccessToken = jwtProvider.createAccessToken(
            user.getId(),
            user.getRole().name()
        );

        String newRefreshToken = jwtProvider.createRefreshToken(user.getId());

        user.updateRefreshTokenHash(hashToken(newRefreshToken));

        return TokenResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .build();
    }

    // 로그아웃
    @Transactional
    public void logout(Long userId) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "로그아웃할 사용자를 찾을 수 없습니다."));

        user.clearRefreshToken();
    }

    // Refresh Token 해시 생성
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    @Getter
    @Builder
    public static class TokenResponse {

        private String accessToken;

        private String refreshToken;

        @Builder.Default
        private String tokenType = "Bearer";
    }
}
