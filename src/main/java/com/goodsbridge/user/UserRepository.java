package com.goodsbridge.payment;

import com.goodsbridge.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
