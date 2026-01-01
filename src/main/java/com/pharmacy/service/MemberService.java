package com.pharmacy.service;

import com.pharmacy.dto.MemberStatsDTO;
import com.pharmacy.entity.Member;
import com.pharmacy.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList; // 添加这个导入
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class MemberService {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private com.pharmacy.repository.OrderRepository orderRepository; // 新增: 用于消费聚合

    // 改进的搜索方法 - 同时搜索所有条件并去重
    public List<Member> searchMembers(String keyword) {
        try {
            System.out.println("=== 在MemberService中搜索会员 ===");
            System.out.println("搜索关键词: " + keyword);

            if (keyword == null || keyword.trim().isEmpty()) {
                System.out.println("关键词为空，返回空列表");
                return new ArrayList<>();
            }

            String trimmedKeyword = normalize(keyword);
            Set<Member> results = new HashSet<>();
            // 1. 精确手机号
            Optional<Member> byPhone = memberRepository.findByPhone(trimmedKeyword);
            byPhone.ifPresent(m -> { System.out.println("手机精确命中: " + m.getName()); results.add(m); });

            // 新增: 部分手机号匹配(包含)
            if (trimmedKeyword.chars().allMatch(Character::isDigit) && trimmedKeyword.length() >= 4) {
                List<Member> partialPhones = memberRepository.findAll().stream()
                        .filter(m -> m.getPhone() != null && m.getPhone().contains(trimmedKeyword))
                        .collect(Collectors.toList());
                if(!partialPhones.isEmpty()) {
                    System.out.println("手机号包含匹配数量: " + partialPhones.size());
                }
                results.addAll(partialPhones);
            }

            // 2. 姓名 LIKE（数据库层面）
            List<Member> nameDbMatches = memberRepository.findByNameContaining(trimmedKeyword);
            if(!nameDbMatches.isEmpty()) {
                System.out.println("数据库 LIKE 匹配姓名数量: " + nameDbMatches.size());
                results.addAll(nameDbMatches);
            } else {
                // Fallback：在已加载的全部列表中手动 contains（处理可能的编码 / 空格差异）
                List<Member> manualNameMatches = memberRepository.findAll().stream()
                        .filter(m -> {
                            String n = normalize(m.getName());
                            return !n.isEmpty() && n.contains(trimmedKeyword);
                        })
                        .collect(Collectors.toList());
                if(!manualNameMatches.isEmpty()) {
                    System.out.println("手动遍历姓名匹配数量: " + manualNameMatches.size());
                    results.addAll(manualNameMatches);
                } else {
                    System.out.println("姓名匹配为空（数据库+手动），关键词: " + trimmedKeyword);
                }
            }

            // 3. 卡号精确
            memberRepository.findByCardNo(trimmedKeyword).ifPresent(m -> {
                System.out.println("卡号精确命中: " + m.getName());
                results.add(m);
            });

            System.out.println("总共找到 " + results.size() + " 个不重复的会员");
            return new ArrayList<>(results);
        } catch (Exception e) {
            System.err.println("会员搜索异常: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("搜索会员时发生错误: " + e.getMessage(), e);
        }
    }

    // 新增: 更宽松的快速搜索(姓名包含 / 手机包含 / 卡号包含)
    public List<Member> quickSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return new ArrayList<>();
        String k = keyword.trim();
        return memberRepository.findAll().stream().filter(m -> {
            return (m.getName() != null && m.getName().contains(k)) ||
                    (m.getPhone() != null && m.getPhone().contains(k)) ||
                    (m.getCardNo() != null && m.getCardNo().contains(k));
        }).collect(Collectors.toList());
    }

    // 创建新会员
    public Member createMember(String memberId, String name, String phone) {
        // 检查手机号是否已存在
        if (memberRepository.findByPhone(phone).isPresent()) {
            throw new RuntimeException("手机号已存在: " + phone);
        }

        Member member = new Member(memberId, name, phone);
        member.setCreateTime(LocalDateTime.now());
        return memberRepository.save(member);
    }

    // 根据ID查找会员
    public Optional<Member> findById(String memberId) {
        return memberRepository.findById(memberId);
    }

    // 根据手机号查找会员
    public Optional<Member> findByPhone(String phone) {
        return memberRepository.findByPhone(phone);
    }

    // 获取所有会员
    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    // 更新会员信息
    public Member updateMember(Member member) {
        if (!memberRepository.existsById(member.getMemberId())) {
            throw new RuntimeException("会员不存在: " + member.getMemberId());
        }
        return memberRepository.save(member);
    }

    public Member updateMember(String id, Member memberDetails) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        member.setName(memberDetails.getName());
        member.setPhone(memberDetails.getPhone());
        member.setCardNo(memberDetails.getCardNo());
        member.setAllergicHistory(memberDetails.getAllergicHistory());
        member.setMedicalCardNo(memberDetails.getMedicalCardNo());

        return memberRepository.save(member);
    }

    // 删除会员
    public void deleteMember(String memberId) {
        memberRepository.deleteById(memberId);
    }


    // 增加积分
    public boolean addPoints(String memberId, int points) {
        Optional<Member> optionalMember = memberRepository.findById(memberId);
        if (optionalMember.isPresent()) {
            Member member = optionalMember.get();
            member.addPoints(points);
            memberRepository.save(member);
            return true;
        }
        return false;
    }

    // 使用积分
    public boolean usePoints(String memberId, int points) {
        Optional<Member> optionalMember = memberRepository.findById(memberId);
        if (optionalMember.isPresent()) {
            Member member = optionalMember.get();
            boolean success = member.usePoints(points);
            if (success) {
                memberRepository.save(member);
            }
            return success;
        }
        return false;
    }

    // 积分兑换
    public void exchangeReward(String memberId, Integer points) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("会员不存在"));

        if (member.getPoints() < points) {
            throw new RuntimeException("积分不足");
        }

        member.setPoints(member.getPoints() - points);
        memberRepository.save(member);
    }

    // 检查手机号是否存在
    public boolean isPhoneExists(String phone) {
        return memberRepository.findByPhone(phone).isPresent();
    }

    // 生成下一个会员ID（简单实现）
    public String generateNextMemberId() {
        // 这里可以根据业务规则实现更复杂的ID生成逻辑
        List<Member> members = memberRepository.findAll();
        if (members.isEmpty()) {
            return "M00001";
        }

        // 获取最大的会员ID并递增
        String maxId = members.stream()
                .map(Member::getMemberId)
                .max(String::compareTo)
                .orElse("M00000");

        // 提取数字部分并递增
        int number = Integer.parseInt(maxId.substring(1)) + 1;
        return String.format("M%05d", number);
    }

    // 在现有的 MemberService 类中添加以下方法：

    // 获取会员统计数据
    public MemberStatsDTO getMemberStats() {
        MemberStatsDTO stats = new MemberStatsDTO();

        try {
            // 总会员数
            Long total = memberRepository.countTotalMembers();
            stats.setTotalMembers(total != null ? total : 0L);

            // VIP会员数（假设等级>=3为VIP）
            List<Member> vipMembers = memberRepository.findByLevel(3);
            stats.setVipMembers((long) vipMembers.size());

            // 最近30天新增会员
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            Long newMembers = memberRepository.countNewMembersSince(thirtyDaysAgo);
            stats.setNewMembers(newMembers != null ? newMembers : 0L);

            // 设置模拟数据（实际项目中应从订单服务获取）
            stats.setTodayConsumption(89L);
            stats.setTotalConsumption(328560.0);
            stats.setSleepingMembers(128L);

            // 设置增长率（模拟数据）
            stats.setTotalGrowth(32);
            stats.setVipGrowth(12);
            stats.setNewGrowth(15);
            stats.setTodayGrowth(12);
            stats.setConsumptionGrowth(15.2);
            stats.setSleepingGrowth(-18);

            // 计算附加指标：平均积分 / 活跃率 / 流失风险数量 (基于真实订单聚合)
            List<Member> allMembers = memberRepository.findAll();
            double sumPoints = 0.0; long totalCount = allMembers.size();
            java.util.Set<String> allIds = allMembers.stream().map(Member::getMemberId).collect(java.util.stream.Collectors.toSet());
            java.util.Map<String, java.time.LocalDateTime> lastMap = new java.util.HashMap<>();
            try {
                if(!allIds.isEmpty()){
                    java.util.List<Object[]> rows = orderRepository.aggregateMemberConsumption(allIds); // [memberId, count, last]
                    for(Object[] r: rows){
                        String mid = (String) r[0];
                        java.time.LocalDateTime last = (java.time.LocalDateTime) r[2];
                        if(mid!=null) lastMap.put(mid,last);
                    }
                }
            } catch(Exception ex){
                System.err.println("订单聚合失败, 使用 createTime 近似: "+ex.getMessage());
            }
            int active = 0; int churn = 0; java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime activeThreshold = now.minusDays(30);
            java.time.LocalDateTime churnThreshold = now.minusDays(90);
            for(Member m: allMembers){
                int pts = m.getPoints()!=null? m.getPoints():0; sumPoints += pts;
                java.time.LocalDateTime last = lastMap.get(m.getMemberId());
                if(last!=null && last.isAfter(activeThreshold)) active++;
                if(last==null || last.isBefore(churnThreshold)) churn++;
            }
            double avg = totalCount==0?0.0: (sumPoints / totalCount);
            double activeRate = totalCount==0?0.0: (active * 100.0 / totalCount);
            stats.setAvgPoints(Math.round(avg*10.0)/10.0); // 一位小数
            stats.setActiveRate(String.format(java.util.Locale.ROOT, "%.1f%%", activeRate));
            stats.setChurnCount(churn);

        } catch (Exception e) {
            System.err.println("获取会员统计数据失败: " + e.getMessage());
            // 设置默认值
            stats.setTotalMembers(0L);
            stats.setVipMembers(0L);
            stats.setNewMembers(0L);
            stats.setTodayConsumption(0L);
            stats.setTotalConsumption(0.0);
            stats.setSleepingMembers(0L);
        }

        return stats;
    }

    // 批量删除会员
    @Transactional
    public boolean deleteMembers(List<String> memberIds) {
        try {
            for (String memberId : memberIds) {
                memberRepository.deleteById(memberId);
            }
            return true;
        } catch (Exception e) {
            System.err.println("批量删除会员失败: " + e.getMessage());
            return false;
        }
    }

    // 根据多个条件筛选会员
    public List<Member> filterMembers(String name, String phone, Integer level, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // 这里可以实现更复杂的查询逻辑
            // 目前简化实现：如果提供了具体条件就按条件查询，否则返回所有
            if (name != null && !name.trim().isEmpty()) {
                return memberRepository.findByNameContaining(name.trim());
            } else if (phone != null && !phone.trim().isEmpty()) {
                Optional<Member> member = memberRepository.findByPhone(phone.trim());
                return member.map(List::of).orElse(new ArrayList<>());
            } else if (level != null) {
                return memberRepository.findByLevel(level);
            } else {
                return memberRepository.findAll();
            }
        } catch (Exception e) {
            System.err.println("筛选会员失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private String normalize(String s){
        if(s==null) return "";
        String trimmed = s.trim();
        // 全角空格转换为普通空格，再压缩连续空白
        trimmed = trimmed.replace('\u3000',' ').replaceAll("\\s+"," ");
        // 去除常见隐藏字符: 零宽空格/零宽不换行/字节顺序标记
        trimmed = trimmed.replace("\u200B","" ) // ZERO WIDTH SPACE
                         .replace("\u200C","" ) // ZERO WIDTH NON-JOINER
                         .replace("\u200D","" ) // ZERO WIDTH JOINER
                         .replace("\uFEFF","" ); // BOM
        return trimmed;
    }
}
