package com.example.board.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.board.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "user")
    List<Comment> findByBoardIdOrderByCreatedAtAsc(Long boardId);

    @EntityGraph(attributePaths = "board")
    Page<Comment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    void deleteByBoardId(Long boardId);
}
