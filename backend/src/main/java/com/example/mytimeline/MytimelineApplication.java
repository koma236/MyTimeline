package com.example.mytimeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Mapper は各インターフェースの {@code @Mapper} を MyBatis の自動設定が拾うため、
 * ここで {@code @MapperScan} は宣言しない（宣言すると DB を使わないテストスライスでも
 * Mapper Bean が登録されてしまい、SqlSessionFactory が無いと起動に失敗する）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MytimelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(MytimelineApplication.class, args);
    }
}
