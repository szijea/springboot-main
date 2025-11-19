package com.pharmacy.multitenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每个 HTTP 请求开始时解析店铺标识写入 TenantContext，完成后清除。
 * 解析顺序：Header X-Shop-Id > 请求参数 shopId > 默认 default。
 */
@Component
public class TenantFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String shopId = resolveShopId(request);
            if(shopId != null) {
                TenantContext.setTenant(shopId);
            }
            filterChain.doFilter(request,response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveShopId(HttpServletRequest request){
        String header = request.getHeader("X-Shop-Id");
        if(header != null && !header.isBlank()) return header.trim();
        String param = request.getParameter("shopId");
        if(param != null && !param.isBlank()) return param.trim();
        return null; // 让路由数据源使用 default
    }
}

