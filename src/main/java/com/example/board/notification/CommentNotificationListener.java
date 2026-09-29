package com.example.board.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.board.entity.User;
import com.example.board.event.CommentCreatedEvent;
import com.example.board.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 댓글이 달리면 게시글 작성자에게 SMS 알림을 보낸다. 댓글 트랜잭션이 커밋된 이후에만 실행되고(AFTER_COMMIT),
 * 응답 속도에 영향을 주지 않도록 별도 스레드(smsTaskExecutor)에서 비동기로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class CommentNotificationListener {

    private final UserRepository userRepository;
    private final SmsSender smsSender;

    @Async("smsTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        if (event.boardAuthorId().equals(event.commenterId())) {
            return;
        }

        User author = userRepository.findById(event.boardAuthorId()).orElse(null);
        if (author == null || author.getPhoneNumber() == null || author.getPhoneNumber().isBlank()) {
            return;
        }

        smsSender.send(author, "[게시판] 회원님의 글에 새 댓글이 달렸습니다.");
    }
}
