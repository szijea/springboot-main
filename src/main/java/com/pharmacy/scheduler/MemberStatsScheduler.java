package com.pharmacy.scheduler;

import com.pharmacy.service.impl.MemberConsumptionUpdater;
import com.pharmacy.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 定时任务：
 * 1. 定期清理过期会员消费缓存
 * 2. 定期批量刷新最近活跃会员的消费统计
 */
@Component
public class MemberStatsScheduler {

    @Autowired(required = false)
    private MemberConsumptionUpdater updater;

    @Autowired
    private OrderRepository orderRepository;

    // 每 20 分钟清理过期缓存 (由配置 application.yaml 指定，亦可直接写 cron)
    @Scheduled(cron = "${member.cache.evict-cron:0 */20 * * * *}")
    public void evictExpired(){
        if(updater==null) return;
        int removed = updater.evictExpired();
        if(removed>0){
            System.out.println("[MemberStatsScheduler] 过期缓存清理数量="+removed+" time="+ LocalDateTime.now());
        }
    }

    // 每 15 分钟批量刷新最近活跃会员（订单参与过的 + 最近缓存命中的）
    @Scheduled(cron = "${member.cache.batch-refresh-cron:0 */15 * * * *}")
    public void batchRefresh(){
        if(updater==null) return;
        try {
            Set<String> recent = updater.snapshotRecentActive(200);
            java.util.List<String> todayMembers = orderRepository.getTodayActiveMembers();
            if(todayMembers!=null){
                for(String mid: todayMembers){
                    if(mid!=null) recent.add(mid);
                    if(recent.size()>=300) break;
                }
            }
            if(!recent.isEmpty()){
                updater.refreshMembersBatch(recent);
                System.out.println("[MemberStatsScheduler] 批量刷新会员消费统计 size="+recent.size());
            }
        } catch(Exception e){
            System.err.println("[MemberStatsScheduler] 批量刷新失败:"+e.getMessage());
        }
    }
}
