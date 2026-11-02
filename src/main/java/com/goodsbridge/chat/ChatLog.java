package com.goodsbridge.chat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;

@Getter
@Entity
public class ChatLog {

    @Id
    @GeneratedValue
    private Long id;
}
