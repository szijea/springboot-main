package com.pharmacy.controller;

import com.pharmacy.entity.PointReward;
import com.pharmacy.service.PointRewardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/point-rewards")
public class PointRewardController {

    @Autowired
    private PointRewardService pointRewardService;

    @GetMapping
    public ResponseEntity<List<PointReward>> getAllRewards() {
        return ResponseEntity.ok(pointRewardService.getAllRewards());
    }

    @GetMapping("/active")
    public ResponseEntity<List<PointReward>> getActiveRewards() {
        return ResponseEntity.ok(pointRewardService.getActiveRewards());
    }

    @PostMapping
    public ResponseEntity<PointReward> createReward(@RequestBody PointReward reward) {
        return ResponseEntity.ok(pointRewardService.createReward(reward));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PointReward> updateReward(@PathVariable Long id, @RequestBody PointReward reward) {
        return ResponseEntity.ok(pointRewardService.updateReward(id, reward));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReward(@PathVariable Long id) {
        pointRewardService.deleteReward(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/toggle")
    public ResponseEntity<Void> toggleStatus(@PathVariable Long id) {
        pointRewardService.toggleStatus(id);
        return ResponseEntity.ok().build();
    }
}

