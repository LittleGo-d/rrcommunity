package com.wh.mysqlReadWrite.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.wh.mysqlReadWrite.aspect.DataSourceAspect;
import com.wh.mysqlReadWrite.common.DSNames;
import com.wh.mysqlReadWrite.common.DynamicDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置数据源、事务管理
 */
@Configuration
@EnableTransactionManagement
@ConditionalOnClass(JdbcOperations.class)//存在该JdbcOperations类则此配置类才装载容器，可能是判定是否采用jdbc事务管理器
//存在该配置此配置类才生效--至少需要存在主数据库才会实例化持久层连接
@ConditionalOnProperty(prefix = "spring.datasource", name = "master.jdbc-url")
public class DataSourceConfig {

    @Bean
    public DataSourceAspect DataSourceAspect() {
        return new DataSourceAspect();
    }

    //多数据源时，必须有一个主数据源
    //master datasource
    @Bean(name = "masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.druid.master")
    public DataSource masterDataSource() {
    	//更换数据源，需要写到这里
    	// return new DruidDataSource(); 
    	return DataSourceBuilder.create().build();
    }

    //slave datasource
    @Bean(name = "slave1DataSource")
    @ConfigurationProperties(prefix = "spring.datasource.druid.slave")
    public DataSource slave1DataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary//有同名bean @Autowired 优先注入
    @Bean(name = "dataSource")
    //@DependsOn({"masterDataSource", "slave1DataSource"})//bean需要的依赖，示意优先实例化到容器，可忽略
    public DataSource dynamicDataSource() {
        //注入数据源
        DataSource masterDataSource = masterDataSource();
        DataSource slave1DataSource = slave1DataSource();

        Map<Object, Object> targetDataSources = new HashMap<>(2);
        targetDataSources.put(DSNames.MASTER, masterDataSource);
        targetDataSources.put(DSNames.SLAVE1, slave1DataSource);

        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        //设置默认数据源
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        //设置目标数据源
        dynamicDataSource.setTargetDataSources(targetDataSources);
        return dynamicDataSource;
    }

    @Bean(name = "sqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory() throws Exception {
        MybatisSqlSessionFactoryBean sqlSessionFactory = new MybatisSqlSessionFactoryBean();
        sqlSessionFactory.setDataSource(dynamicDataSource());
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        sqlSessionFactory.setConfiguration(configuration);
        return sqlSessionFactory.getObject();
    }

    /**
     * 事务管理器
     * @return
     */
    @Bean
    public PlatformTransactionManager transactionManager() {
        return new DataSourceTransactionManager(dynamicDataSource());
    }
}
