package com.hongrui.middleware.db.router.config;
import com.hongrui.middleware.db.router.dynamic.DynamicMybatisPlugin;
import com.hongrui.middleware.db.router.strategy.IDBRouterStrategy;
import com.hongrui.middleware.db.router.strategy.impl.DBRouterStrategyHashCode;
import com.hongrui.middleware.db.router.util.StringUtils;
import org.apache.ibatis.plugin.Interceptor;
import com.hongrui.middleware.db.router.DBRouterConfig;
import com.hongrui.middleware.db.router.DBRouterJoinPoint;
import com.hongrui.middleware.db.router.annotation.DBRouter;
import com.hongrui.middleware.db.router.dynamic.DynamicDataSource;
import com.hongrui.middleware.db.router.util.PropertyUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.*;

import java.util.HashMap;

/**
 * @author hongrui
 * @description
 * @date 2024-11-17 13:45
 */
@Configuration
public class DataSourceAutoConfig implements EnvironmentAware {

    /**
     * 分库全局属性
     */
    private static final String TAG_GLOBAL = "global";

    /**
     * 连接池属性
     */
    private static final String TAG_POOL = "pool";

    /**
     * 数据源配置组
     */
    private Map<String, Map<String, Object>> dataSourceMap = new HashMap<>();

    /**
     * 默认数据源配置
     */
    private Map<String, Object> defaultDataSourceConfig;

    /**
     * 分库数量
     */
    private int dbCount;

    /**
     * 分表数量
     */
    private int tbCount;

    /**
     * 路由字段
     */
    private String routerKey;

    @Bean(name = "db-router-point")
    @ConditionalOnMissingBean
    public DBRouterJoinPoint point(DBRouterConfig dbRouterConfig, IDBRouterStrategy dbRouterStrategy) {
        return new DBRouterJoinPoint(dbRouterConfig, dbRouterStrategy);
    }

    @Bean
    public DBRouterConfig dbRouterConfig() {
        return new DBRouterConfig(dbCount, tbCount, routerKey);
    }

    @Bean("dbRouterDynamicMybatisPlugin")
    public Interceptor plugin() {
        return new DynamicMybatisPlugin();
    }

    private DataSource createDataSource(Map<String, Object> attributes) {
        try {
            DataSourceProperties dataSourceProperties = new DataSourceProperties();
            dataSourceProperties.setUrl(attributes.get("url").toString());
            dataSourceProperties.setUsername(attributes.get("username").toString());
            dataSourceProperties.setPassword(attributes.get("password").toString());

            String driverClassName = attributes.get("driver-class-name") == null ? "com.zaxxer.hikari.HikariDataSource" : attributes.get("driver-class-name").toString();
            dataSourceProperties.setDriverClassName(driverClassName);

            String typeClassName = attributes.get("type-class-name") == null ? "com.zaxxer.hikari.HikariDataSource" : attributes.get("type-class-name").toString();
            DataSource ds = dataSourceProperties.initializeDataSourceBuilder().type((Class<DataSource>) Class.forName(typeClassName)).build();

            MetaObject dsMeta = SystemMetaObject.forObject(ds);
            Map<String, Object> poolProps = (Map<String, Object>) (attributes.containsKey(TAG_POOL) ? attributes.get(TAG_POOL) : Collections.EMPTY_MAP);
            for (Map.Entry<String, Object> entry : poolProps.entrySet()) {
                // 中划线转驼峰
                String key = StringUtils.middleScoreToCamelCase(entry.getKey());
                if (dsMeta.hasSetter(key)) {
                    dsMeta.setValue(key, entry.getValue());
                }
            }
            return ds;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("can not find datasource type class by class name", e);
        }
    }

    @Bean("mysqlDataSource")
    public DataSource createDataSource() {
        // 创建数据源
        Map<Object, Object> targetDataSources = new HashMap<>();
        for (String dbInfo : dataSourceMap.keySet()) {
            Map<String, Object> objMap = dataSourceMap.get(dbInfo);
            // 根据objMap创建DataSourceProperties,遍历objMap根据属性反射创建DataSourceProperties
            DataSource ds = createDataSource(objMap);
            targetDataSources.put(dbInfo, ds);

        }

        // 设置数据源
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        dynamicDataSource.setTargetDataSources(targetDataSources);
        // db0为默认数据源
        dynamicDataSource.setDefaultTargetDataSource(createDataSource(defaultDataSourceConfig));

        return dynamicDataSource;
    }

    @Bean
    public IDBRouterStrategy dbRouterStrategy(DBRouterConfig dbRouterConfig) {
        return new DBRouterStrategyHashCode(dbRouterConfig);
    }

    @Bean
    public TransactionTemplate transactionTemplate(DataSource mysqlDataSource) {
        DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager();
        dataSourceTransactionManager.setDataSource(mysqlDataSource);

        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(dataSourceTransactionManager);
        transactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRED");
        return transactionTemplate;
    }

    @Override
    public void setEnvironment(Environment environment) {
        String prefix = "mini-db-router.jdbc.datasource.";

        dbCount = Integer.parseInt(Objects.requireNonNull(environment.getProperty(prefix + "dbCount")));
        tbCount = Integer.parseInt(Objects.requireNonNull(environment.getProperty(prefix + "tbCount")));
        routerKey = environment.getProperty(prefix + "routerKey");
        // 分库分表数据源
        String dataSources = environment.getProperty(prefix + "list");
        Map<String, Object> globalInfo = getGlobalProps(environment, prefix + TAG_GLOBAL);
        for (String dbInfo : dataSources.split(",")) {
            final String dbPrefix = prefix + dbInfo;
            Map<String, Object> dataSourceProps = PropertyUtil.handle(environment, dbPrefix, Map.class);
            injectGlobal(dataSourceProps, globalInfo);
            dataSourceMap.put(dbInfo, dataSourceProps);
        }

        // 默认数据源
        String defaultData = environment.getProperty(prefix + "default");
        defaultDataSourceConfig = PropertyUtil.handle(environment, prefix + defaultData, Map.class);
        injectGlobal(defaultDataSourceConfig, globalInfo);
    }

    private Map<String, Object> getGlobalProps(Environment environment, String key) {
        try {
            return PropertyUtil.handle(environment, key, Map.class);
        } catch (Exception e) {
            return Collections.EMPTY_MAP;
        }
    }

    private void injectGlobal(Map<String, Object> origin, Map<String, Object> global) {
        for (String key : global.keySet()) {
            if (!origin.containsKey(key)) {
                origin.put(key, global.get(key));
            } else if (origin.get(key) instanceof Map) {
                injectGlobal((Map<String, Object>) origin.get(key), (Map<String, Object>) global.get(key));
            }
        }
    }

}
