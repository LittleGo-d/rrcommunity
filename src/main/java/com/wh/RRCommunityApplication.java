package com.wh;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@MapperScan("com.wh.mapper")
public class RRCommunityApplication {
    public static void main(String[] args) {
        SpringApplication.run(RRCommunityApplication.class);
    }
}
