package com.example.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.board.entity.BoardLike;

public interface BoardLikeRepository extends JpaRepository<BoardLike, Long> {

    boolean existsByBoardIdAndUserId(Long boardId, Long userId);

    long countByBoardId(Long boardId);

    void deleteByBoardIdAndUserId(Long boardId, Long userId);

    void deleteByBoardId(Long boardId);
}
