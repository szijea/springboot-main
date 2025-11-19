package com.pharmacy.multitenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.*;

/**
 * 动态多数据源配置：默认数据源 + tenants 列表，路由至对应店铺库。
 */
@Configuration
@ConditionalOnProperty(prefix = "spring", name = "datasource.url")
public class MultiTenantDataSourceConfig implements InitializingBean {

    private final Environment env;
    public MultiTenantDataSourceConfig(Environment env){ this.env = env; }

    private Map<String, DataSource> dataSourceMap = new HashMap<>();

    @Value("${spring.datasource.url}")
    private String defaultUrl;
    @Value("${spring.datasource.username}")
    private String defaultUser;
    @Value("${spring.datasource.password}")
    private String defaultPwd;

    @Bean
    @Primary
    public DataSource routingDataSource(){
        StoreRoutingDataSource routing = new StoreRoutingDataSource();
        // 默认数据源
        DataSource defaultDs = buildHikari(defaultUrl, defaultUser, defaultPwd, "default");
        dataSourceMap.put("default", defaultDs);
        // 解析 tenants 列表
        List<Map<String,Object>> tenants = loadTenantsFromEnv();
        for(Map<String,Object> t : tenants){
            String id = Objects.toString(t.get("id"), null);
            String url = Objects.toString(t.get("url"), null);
            String user = Objects.toString(t.get("username"), null);
            String pwd = Objects.toString(t.get("password"), null);
            if(id==null || url==null) continue;
            dataSourceMap.put(id, buildHikari(url,user,pwd,id));
        }
        routing.setDefaultTargetDataSource(defaultDs);
        routing.setTargetDataSources(new HashMap<>(dataSourceMap));
        routing.afterPropertiesSet();
        System.out.println("[MultiTenant] Data sources initialized: " + dataSourceMap.keySet());
        return routing;
    }

    private List<Map<String,Object>> loadTenantsFromEnv(){
        // spring.tenants[0].id 结构读取
        List<Map<String,Object>> list = new ArrayList<>();
        int idx=0;
        while(true){
            String prefix = "spring.tenants["+idx+"]";
            String id = env.getProperty(prefix+".id");
            if(id==null) break; // 结束
            Map<String,Object> item = new HashMap<>();
            item.put("id", id);
            item.put("url", env.getProperty(prefix+".url"));
            item.put("username", env.getProperty(prefix+".username"));
            item.put("password", env.getProperty(prefix+".password"));
            list.add(item);
            idx++;
        }
        return list;
    }

    private DataSource buildHikari(String url,String user,String pwd,String poolName){
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(pwd);
        cfg.setMaximumPoolSize(5);
        cfg.setPoolName("DS-"+poolName);
        cfg.setConnectionTestQuery("SELECT 1");
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(cfg);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource routingDataSource){
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(routingDataSource);
        emf.setPackagesToScan("com.pharmacy.entity");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String,Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", env.getProperty("spring.jpa.hibernate.ddl-auto","none"));
        props.put("hibernate.show_sql", env.getProperty("spring.jpa.show-sql","false"));
        props.put("hibernate.format_sql", env.getProperty("spring.jpa.properties.hibernate.format_sql","true"));
        props.put("hibernate.dialect", env.getProperty("spring.jpa.properties.hibernate.dialect","org.hibernate.dialect.MySQLDialect"));
        emf.setJpaPropertyMap(props);
        return emf;
    }

    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory){
        return new JpaTransactionManager(Objects.requireNonNull(entityManagerFactory.getObject()));
    }

    /**
     * 动态添加新租户数据源（可在管理接口调用）。
     */
    public synchronized void addTenant(String id, String url, String user, String pwd){
        if(dataSourceMap.containsKey(id)) return;
        DataSource ds = buildHikari(url,user,pwd,id);
        dataSourceMap.put(id, ds);
        StoreRoutingDataSource routing = (StoreRoutingDataSource) routingDataSource();
        routing.setTargetDataSources(new HashMap<>(dataSourceMap));
        routing.afterPropertiesSet();
    }

    public Set<String> getTenantIds(){
        return new LinkedHashSet<>(dataSourceMap.keySet());
    }
    public Map<String, DataSource> getDataSourceMap(){
        return Collections.unmodifiableMap(dataSourceMap);
    }

    @Override
    public void afterPropertiesSet() {
        // 预留：启动后日志输出当前已注册数据源
        System.out.println("[MultiTenant] Registered data sources: " + dataSourceMap.keySet());
    }
}
