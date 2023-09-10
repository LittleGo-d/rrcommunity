package com.wh.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @Author wh
 * @ClassName SwaggerUtil
 **/
@Configuration
@EnableSwagger2
public class SwaggerUtil {
    @Bean
    public Docket createRestApi(){
        return new Docket(DocumentationType.SWAGGER_2)
                .pathMapping("/")
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.wh.controller"))
                .paths(PathSelectors.any())
                .build()
                .apiInfo(new ApiInfoBuilder()
                .title("人人社区")
                        .description("用于发现附近店铺")
                        .version("1.0")
                        .contact(new Contact("wh","https://LittleGo-d.github.io","wh18720283974@163.com")
                        )
                        .license("wh")
                        .licenseUrl("https://LittleGo-d.github.io")
                        .build());
    }
}

