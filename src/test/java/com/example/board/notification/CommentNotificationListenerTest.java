package com.example.board.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.event.CommentCreatedEvent;
import com.example.board.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CommentNotificationListenerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SmsSender smsSender;

    @InjectMocks
    private CommentNotificationListener listener;

    private User author;

    @BeforeEach
    void setUp() {
        author = User.builder()
                .email("author@example.com")
                .name("작성자")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(author, "id", 1L);
    }

    @Test
    void 본인_글에_본인이_댓글을_달면_SMS를_보내지_않는다() {
        CommentCreatedEvent event = new CommentCreatedEvent(100L, 10L, 1L, 1L);

        listener.onCommentCreated(event);

        verify(userRepository, never()).findById(any());
        verify(smsSender, never()).send(any(), any());
    }

    @Test
    void 작성자가_전화번호를_등록하지_않았으면_SMS를_보내지_않는다() {
        CommentCreatedEvent event = new CommentCreatedEvent(100L, 10L, 1L, 2L);
        given(userRepository.findById(1L)).willReturn(Optional.of(author));

        listener.onCommentCreated(event);

        verify(smsSender, never()).send(any(), any());
    }

    @Test
    void 작성자가_존재하지_않으면_SMS를_보내지_않는다() {
        CommentCreatedEvent event = new CommentCreatedEvent(100L, 10L, 1L, 2L);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        listener.onCommentCreated(event);

        verify(smsSender, never()).send(any(), any());
    }

    @Test
    void 전화번호가_등록된_작성자에게_다른_사람이_댓글을_달면_SMS를_보낸다() {
        ReflectionTestUtils.setField(author, "phoneNumber", "+821012345678");
        CommentCreatedEvent event = new CommentCreatedEvent(100L, 10L, 1L, 2L);
        given(userRepository.findById(1L)).willReturn(Optional.of(author));

        listener.onCommentCreated(event);

        verify(smsSender).send(eq(author), any());
    }
}
