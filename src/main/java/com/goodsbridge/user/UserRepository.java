package com.goodsbridge.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select u.id from User u where u.role = :role")
    List<Long> findIdsByRole(@Param("role") Role role);

    boolean existsByIdAndRole(Long id, Role role);

    // 닉네임 중복 확인 (닉네임 컬럼은 unique)
    boolean existsByNickname(String nickname);

    // 내 정보 수정: 나를 뺀 다른 회원이 이 닉네임을 쓰는지
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
