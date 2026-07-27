package com.example.mytimeline.model;

import java.time.LocalDateTime;

/**
 * posts テーブルの 1 行に対応するモデル。
 *
 * <p>{@link User} と同じく、MyBatis が結果セットから組み立てるため引数なしコンストラクタと
 * setter を持つ可変クラスにしている（record は使えない）。</p>
 *
 * <p>{@link #author} は posts 単体には存在しない列で、users を JOIN したときだけ埋まる。
 * タイムラインは必ず投稿者名を表示するので、投稿を取得する SQL では常に JOIN している。</p>
 */
public class Post {

    private Long id;
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
