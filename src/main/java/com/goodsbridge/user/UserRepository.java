package com.goodsbridge.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    @Query("SELECT u FROM User u where u.kakaoId = :kakaoId")
    Optional<User> findByKakaoId(@Param("kakaoId") String kakaoId);

}
