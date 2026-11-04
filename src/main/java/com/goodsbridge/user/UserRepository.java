package com.goodsbridge.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    // 사용자명과 비밀번호로 조회 (로그인 용)
    @Query("""
        select DISTINCT u from User u Left Join Fetch u.roles where u.username = :username
    """)
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    // 이메일 중복 체크
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);

}
