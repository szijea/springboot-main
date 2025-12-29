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
        // 默认数据源（基础租户）
        DataSource defaultDs = buildHikari(defaultUrl, defaultUser, defaultPwd, "default");
        dataSourceMap.put("default", defaultDs);

        // 解析租户列表（spring.tenants[n].id 结构）
        List<Map<String,Object>> tenants = loadTenantsFromEnv();
        if(tenants.isEmpty()){
            System.out.println("[MultiTenant] 未发现 spring.tenants 配置，系统仅使用 default 数据源");
        } else {
            for(Map<String,Object> t : tenants){
                String id = Objects.toString(t.get("id"), null);
                String url = Objects.toString(t.get("url"), null);
                String user = Objects.toString(t.get("username"), defaultUser);
                String pwd = Objects.toString(t.get("password"), defaultPwd);
                if(id==null || url==null){
                    System.err.println("[MultiTenant] 跳过无效租户配置: " + t);
                    continue;
                }
                if(dataSourceMap.containsKey(id)){
                    System.out.println("[MultiTenant] 租户重复忽略: " + id);
                    continue;
                }
                DataSource tenantDs = buildHikari(url,user,pwd,id);
                dataSourceMap.put(id, tenantDs);
            }
        }
        routing.setDefaultTargetDataSource(defaultDs);
        // 使用显式 Map<Object,Object> 传入，避免泛型不匹配
        Map<Object,Object> targetMap = new HashMap<>();
        targetMap.putAll(dataSourceMap);
        routing.setTargetDataSources(targetMap);
        routing.afterPropertiesSet();
        System.out.println("[MultiTenant] DataSources 初始化完成: " + dataSourceMap.keySet());
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

        int maxRetry = 10; // 调高重试次数，容忍 MySQL 初始化较慢情况
        long backoffMs = 3000L; // 每次重试间隔加大
        Exception last = null;
        for(int attempt=1; attempt<=maxRetry; attempt++){
            try {
                System.out.println("[MultiTenant] 尝试建立连接("+poolName+") 第 " + attempt + " 次 -> " + url);
                HikariDataSource ds = new HikariDataSource(cfg);
                try(var c = ds.getConnection()){ /* 连接测试 */ }
                System.out.println("[MultiTenant] 租户 " + poolName + " 数据源连接成功");
                return ds;
            } catch (Exception ex){
                last = ex;
                System.err.println("[MultiTenant] 租户 " + poolName + " 连接失败("+attempt+"/"+maxRetry+"): " + ex.getMessage());
                if(attempt < maxRetry){
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) {}
                }
            }
        }
        System.err.println("[MultiTenant] 多次重试仍失败, 放弃租户 " + poolName + " -> " + url);
        throw new RuntimeException("[MultiTenant] 数据源初始化多次重试仍失败: " + poolName + " -> " + url, last);
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
        Map<Object,Object> targetMap = new HashMap<>(dataSourceMap);
        routing.setTargetDataSources(targetMap);
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
        // 初始化阶段此处不做空列表误导日志，routingDataSource 创建后会输出最终列表
        System.out.println("[MultiTenant] 配置类加载完成");
    }
}
