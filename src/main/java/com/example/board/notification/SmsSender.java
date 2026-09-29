package com.example.board.notification;

import com.example.board.entity.User;

public interface SmsSender {

    void send(User recipient, String message);
}
