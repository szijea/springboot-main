package com.pharmacy.multitenant;

import com.pharmacy.entity.Supplier;
import com.pharmacy.repository.SupplierRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class MultiTenantSupplierSeeder {

    @Bean
    @Transactional
    public ApplicationRunner supplierDefaultSeeder(MultiTenantDataSourceConfig dsConfig, SupplierRepository supplierRepository) {
        return args -> {
            System.out.println("[SupplierSeeder] 开始检测多租户默认供应商...");
            for (String tenantId : dsConfig.getDataSourceMap().keySet()) {
                if ("default".equals(tenantId)) continue;
                TenantContext.setTenant(tenantId);
                try {
                    long count = supplierRepository.count();
                    boolean needCreate = (count == 0);
                    if (!needCreate && supplierRepository.findBySupplierName("默认供应商").isEmpty()) {
                        needCreate = true;
                    }
                    if (needCreate) {
                        Supplier s = new Supplier();
                        s.setSupplierName("默认供应商");
                        s.setContactPerson("系统");
                        s.setPhone("13800000000");
                        s.setAddress("系统自动创建");
                        supplierRepository.save(s);
                        System.out.println("[SupplierSeeder] 租户="+tenantId+" 已创建默认供应商");
                    } else {
                        System.out.println("[SupplierSeeder] 租户="+tenantId+" 默认供应商已存在");
                    }
                } catch (Exception ex) {
                    System.err.println("[SupplierSeeder] 租户="+tenantId+" 创建默认供应商失败: "+ex.getMessage());
                } finally { TenantContext.clear(); }
            }
            System.out.println("[SupplierSeeder] 默认供应商检测完成");
        };
    }
}
