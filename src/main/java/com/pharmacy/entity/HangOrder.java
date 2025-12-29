package com.pharmacy.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "hang_order")
public class HangOrder {
    @Id
    @Column(name = "hang_id", length = 32)
    private String hangId;

    @Column(name = "hang_time")
    private LocalDateTime hangTime;

    @Column(columnDefinition = "TEXT")
    private String cart_json;

    @Column(name = "member_id", length = 32)
    private String memberId;

    @Column(name = "member_name", length = 50)
    private String memberName;

    public String getHangId() { return hangId; }
    public void setHangId(String hangId) { this.hangId = hangId; }

    public LocalDateTime getHangTime() { return hangTime; }
    public void setHangTime(LocalDateTime hangTime) { this.hangTime = hangTime; }

    public String getCartJson() { return cart_json; }
    public void setCartJson(String cart_json) { this.cart_json = cart_json; }

    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
}

