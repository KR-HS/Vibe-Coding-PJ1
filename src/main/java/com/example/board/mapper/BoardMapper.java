package com.example.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.entity.BoardCategory;

@Mapper
public interface BoardMapper {

    List<BoardListItemResponse> findList(
            @Param("keyword") String keyword,
            @Param("category") BoardCategory category,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long count(
            @Param("keyword") String keyword,
            @Param("category") BoardCategory category);
}
