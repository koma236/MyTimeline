package com.example.mytimeline.model;

import java.time.LocalDateTime;

/**
 * comments テーブルの 1 行に対応するモデル。
 *
 * <p>{@link Post} と同じく、MyBatis が結果セットから組み立てるため引数なしコンストラクタと
 * setter を持つ可変クラスにしている（record は使えない）。</p>
 *
 * <p>{@link #author} は comments 単体には存在しない列で、users を JOIN したときだけ埋まる。
 * コメント一覧は必ず投稿者名を表示するので、コメントを取得する SQL では常に JOIN している。</p>
 */
public class Comment {

    private Long id;
    private Long postId;
    private Long userId;
    private String body;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private User author;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }
}
