package com.example.mytimeline.model;

import java.time.LocalDateTime;

/**
 * users テーブルの 1 行に対応するモデル。
 *
 * <p>MyBatis が結果セットからインスタンスを組み立てるため、引数なしコンストラクタと
 * setter を持つ可変クラスにしている（record は使えない）。</p>
 */
public class User {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String passwordHash;
    private String bio;
    private String avatarKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    /**
     * アバター画像のストレージキー。未設定なら null。
     *
     * <p>画像本体は保持せず、キーだけを持つ。画面に出す URL は
     * {@code AvatarUrlFactory} がこのキーから署名付き URL を組み立てる。</p>
     */
    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
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
}
