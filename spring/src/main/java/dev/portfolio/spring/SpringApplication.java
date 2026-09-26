package dev.portfolio.spring;

import dev.portfolio.foundation.*;
import dev.portfolio.graphrag.Product;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication public class SpringApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(SpringApplication.class, args);
    }
    @Bean Config workbenchConfig() {
        return Config.fromEnvironment(8313);
    }
    @Bean(initMethod="start", destroyMethod="close") Application workbench(Config config) {
        return new Product(config.createStore(), config.createModel());
    }
    @Bean Gateway gateway(Application app, Config config) {
        return new Gateway(app, config);
    }
}
