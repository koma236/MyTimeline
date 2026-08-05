package com.example.mytimeline.model;

import java.time.LocalDateTime;

/**
 * post_images テーブルの 1 行に対応するモデル。
 *
 * <p>{@link Post} と同じく、MyBatis が結果セットから組み立てるため引数なしコンストラクタと
 * setter を持つ可変クラスにしている（record は使えない）。</p>
 */
public class PostImage {

    private Long id;
    private Long postId;
    private String s3Key;
    private int position;
    private LocalDateTime createdAt;

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

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
